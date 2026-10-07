package dev.qilletni.intellij.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal resolve utilities for Qilletni.
 * - Variables: resolve to params or var_declaration within nearest function
 * - Functions: resolve to top-level functions (no receiver) in the file
 * - Members: resolve to entity properties or methods when receiver type is statically known
 */
public final class QilletniResolveUtil {
    private QilletniResolveUtil() {}

    public static @Nullable PsiElement resolveVariableUsage(PsiElement idElement) {
        if (!isIdLeaf(idElement)) return null;
        PsiFile file = idElement.getContainingFile();
        var function = PsiTreeUtil.getParentOfType(idElement, QilletniFunctionDef.class);
        if (function == null) {
            return isInRoot(idElement) && file instanceof QilletniFile ? resolveTopLevelUses(idElement, file) : null;
        }

        // Within the current function: params first, then local var declarations
        for (var pName : PsiTreeUtil.findChildrenOfType(function, QilletniParamName.class)) {
            if (textEquals(pName.getText(), idElement.getText())) return pName;
        }
        for (var vName : PsiTreeUtil.findChildrenOfType(function, QilletniVarName.class)) {
            if (textEquals(vName.getText(), idElement.getText())) return vName;
        }

        if (file instanceof QilletniFile) {
            if (function.getParent() instanceof QilletniRunning) {
                // A top-level function also sees top-level variables
                return resolveTopLevelUses(idElement, file);
            }
            // A method sees its entity's properties
            var entity = PsiTreeUtil.getParentOfType(function, QilletniEntityDef.class);
            if (entity != null) {
                for (var propName : PsiTreeUtil.findChildrenOfType(entity, QilletniPropertyName.class)) {
                    if (textEquals(propName.getText(), idElement.getText())) return propName;
                }
            }
        }
        return null;
    }

    /** Searches top-level variables in the current file and every imported file. */
    private static @Nullable PsiElement resolveTopLevelUses(PsiElement idElement, PsiFile file) {
        List<PsiFile> filesToSearch = new ArrayList<>();
        if (file instanceof QilletniFile qf) {
            if (qf.getVirtualFile() != null) filesToSearch.add(qf);
            var psiManager = PsiManager.getInstance(idElement.getProject());
            for (var vf : QilletniAliasResolver.getAllImportedFiles(qf)) {
                var psi = vf != null ? psiManager.findFile(vf) : null;
                if (psi instanceof QilletniFile) filesToSearch.add(psi);
            }
            // Completion copies have no VirtualFile; still search the context PSI
            if (qf.getVirtualFile() == null) filesToSearch.add(qf);
        } else {
            filesToSearch.add(file);
        }

        for (var psi : filesToSearch) {
            for (var vName : PsiTreeUtil.findChildrenOfType(psi, QilletniVarName.class)) {
                if (QilletniPsiUtil.isTopLevel(vName) && textEquals(vName.getText(), idElement.getText())) {
                    return vName;
                }
            }
        }
        return null;
    }

    public static @Nullable PsiElement resolveFunctionUsage(PsiElement idElement) {
        if (!isIdLeaf(idElement)) return null;
        if (!(idElement.getContainingFile() instanceof QilletniFile qf)) return null;
        var name = idElement.getText();

        // Top-level functions visible in scope (current file + all imports)
        var match = findTopLevelFunctionName(QilletniIndexFacade.listTopLevelFunctions(idElement.getProject(), qf), name);
        if (match != null) return match;

        // Completion copies have no VirtualFile; scan the context PSI directly
        if (qf.getVirtualFile() == null) {
            return findTopLevelFunctionName(PsiTreeUtil.findChildrenOfType(qf, QilletniFunctionDef.class), name);
        }
        return null;
    }

    private static @Nullable QilletniFunctionName findTopLevelFunctionName(Collection<QilletniFunctionDef> defs, String name) {
        for (var def : defs) {
            if (PsiTreeUtil.findChildOfType(def, QilletniFunctionOnType.class) != null) continue; // extension method
            var fName = PsiTreeUtil.findChildOfType(def, QilletniFunctionName.class);
            if (fName != null && textEquals(name, fName.getText())) return fName;
        }
        return null;
    }

    public static @Nullable PsiElement resolveMemberUsage(PsiElement idElement) {
        if (idElement == null || !isIdLeaf(idElement)) return null;
        if (!(idElement.getContainingFile() instanceof QilletniFile file)) return null;
        var project = idElement.getProject();

        // A method name sits inside a function_call; a property name is a bare ID segment
        var isMethodCall = PsiTreeUtil.getParentOfType(idElement, QilletniFunctionCall.class, false, QilletniPrimaryExpr.class) != null
                || PsiTreeUtil.getParentOfType(idElement, QilletniFunctionCall.class, false, QilletniLhsCore.class) != null;
        boolean isTypeReceiver = false;
        String receiverType = null;

        var postfix = PsiTreeUtil.getParentOfType(idElement, QilletniPostfixExpr.class);
        if (postfix != null) {
            // postfix_expr ::= primary_expr (DOT postfix_suffix)*
            var primary = PsiTreeUtil.findChildOfType(postfix, QilletniPrimaryExpr.class);
            var receiver = QilletniTypeInference.inferReceiver(file, primary, false);
            if (receiver != null) {
                receiverType = receiver.type();
                isTypeReceiver = receiver.isTypeReceiver();
            }

            List<PsiElement> orderedSuffixes = new ArrayList<>();
            for (var ch : postfix.getChildren()) {
                if (ch instanceof QilletniPostfixSuffix) orderedSuffixes.add(ch);
            }
            int currentIndex = -1;
            for (int i = 0; i < orderedSuffixes.size(); i++) {
                if (PsiTreeUtil.isAncestor(orderedSuffixes.get(i), idElement, false)) {
                    currentIndex = i;
                    break;
                }
            }

            if (currentIndex != -1 && !isTypeReceiver) {
                // Advance the receiver type through preceding property segments; call return types are unknown
                for (int i = 0; i < currentIndex && receiverType != null; i++) {
                    var suff = orderedSuffixes.get(i);
                    if (PsiTreeUtil.findChildOfType(suff, QilletniFunctionCall.class) != null) continue;
                    PsiElement idLeaf = null;
                    // getChildren() returns only composite children, so this never sees the ID leaf
                    for (var c : suff.getChildren()) {
                        if (isIdLeaf(c)) { idLeaf = c; break; }
                    }
                    if (idLeaf != null) {
                        var ent = QilletniIndexFacade.findEntityByTypeName(project, file, receiverType);
                        receiverType = ent != null ? getEntityPropertyType(ent, idLeaf.getText()) : null;
                    }
                }
            }
        } else {
            var lhsMember = PsiTreeUtil.getParentOfType(idElement, QilletniLhsMember.class);
            if (lhsMember != null) {
                // lhs_member ::= lhs_core (DOT ID)+
                var lhsCore = PsiTreeUtil.findChildOfType(lhsMember, QilletniLhsCore.class);
                PsiElement primary = lhsCore != null ? PsiTreeUtil.findChildOfType(lhsCore, QilletniPrimaryExpr.class) : null;
                receiverType = QilletniTypeInference.inferReceiverType(primary, false);

                List<PsiElement> lhsIds = collectLhsMemberIds(lhsMember, lhsCore);
                int idx = -1;
                for (int i = 0; i < lhsIds.size(); i++) {
                    var it = lhsIds.get(i);
                    if (it == idElement || it.getTextRange().equals(idElement.getTextRange())) { idx = i; break; }
                }
                for (int i = 0; i < idx && receiverType != null; i++) {
                    var ent = QilletniIndexFacade.findEntityByTypeName(project, file, receiverType);
                    receiverType = ent != null ? getEntityPropertyType(ent, lhsIds.get(i).getText()) : null;
                }
            } else {
                // Method name inside lhs_core (lhs_core . name(args)); prior call return types are unknown
                var lhsCore = PsiTreeUtil.getParentOfType(idElement, QilletniLhsCore.class);
                if (lhsCore != null) {
                    PsiElement primary = PsiTreeUtil.findChildOfType(lhsCore, QilletniPrimaryExpr.class);
                    receiverType = QilletniTypeInference.inferReceiverType(primary, false);
                }
            }
        }

        if (receiverType == null) return null;

        // Prefer members declared inside the matching entity
        QilletniEntityDef targetEntity = QilletniIndexFacade.findEntityByTypeName(project, file, receiverType);
        if (targetEntity != null) {
            if (isMethodCall) {
                for (var f : PsiTreeUtil.findChildrenOfType(targetEntity, QilletniFunctionDef.class)) {
                    var fName = PsiTreeUtil.findChildOfType(f, QilletniFunctionName.class);
                    // A type receiver only sees static methods, an instance only non-static ones
                    if (fName != null && textEquals(fName.getText(), idElement.getText())
                            && QilletniPsiUtil.isStaticFunctionDef(f) == isTypeReceiver) {
                        return fName;
                    }
                }
            } else {
                for (var prop : PsiTreeUtil.findChildrenOfType(targetEntity, QilletniPropertyName.class)) {
                    if (textEquals(prop.getText(), idElement.getText())) return prop;
                }
            }
        }

        // Extension methods: function_def with function_on_type matching receiverType
        if (isMethodCall && !isTypeReceiver) {
            for (var def : QilletniIndexFacade.findExtensionMethods(project, file, receiverType, idElement.getText())) {
                var fName = PsiTreeUtil.findChildOfType(def, QilletniFunctionName.class);
                if (fName != null) return fName;
            }
        }
        return null;
    }

    private static @Nullable String getEntityPropertyType(QilletniEntityDef entity, String propertyName) {
        if (entity == null || propertyName == null) return null;
        return getOrBuildEntityPropertyTypeMap(entity).get(propertyName);
    }

    /** propertyName -> declared type text, cached per entity. */
    private static Map<String, String> getOrBuildEntityPropertyTypeMap(QilletniEntityDef entity) {
        return CachedValuesManager.getCachedValue(entity, () -> {
            Map<String, String> m = new HashMap<>();
            for (var decl : PsiTreeUtil.findChildrenOfType(entity, QilletniEntityPropertyDeclaration.class)) {
                QilletniPropertyName propName = PsiTreeUtil.findChildOfType(decl, QilletniPropertyName.class);
                if (propName == null) continue;
                String typeText = QilletniPsiUtil.declaredTypeText(decl, propName);
                if (typeText != null) m.put(propName.getText(), typeText);
            }
            return CachedValueProvider.Result.create(Map.copyOf(m), PsiModificationTracker.MODIFICATION_COUNT);
        });
    }

    /** IDs in lhs_member after lhs_core, in source order. */
    private static List<PsiElement> collectLhsMemberIds(QilletniLhsMember lhsMember, QilletniLhsCore lhsCore) {
        List<PsiElement> ids = new ArrayList<>();
        if (lhsMember == null) return ids;
        for (PsiElement cur = lhsCore != null ? lhsCore.getNextSibling() : lhsMember.getFirstChild(); cur != null; cur = cur.getNextSibling()) {
            if (isIdLeaf(cur)) ids.add(cur);
        }
        return ids;
    }

    private static boolean isIdLeaf(PsiElement el) {
        return QilletniPsiUtil.isToken(el, QilletniTypes.ID);
    }

    private static boolean textEquals(String a, String b) { return a != null && a.contentEquals(b); }

    private static boolean isInRoot(PsiElement element) {
        PsiElement cur = element;
        while (cur != null) {
            if (cur.getNode() != null && (cur.getNode().getElementType() == QilletniTypes.FUNCTION_DEFINITION || cur.getNode().getElementType() == QilletniTypes.ENTITY_DEF)) return false;
            if (cur.getNode() != null && cur.getNode().getElementType() == QilletniTypes.RUNNING) return true;
            cur = cur.getParent();
        }
        return true;
    }
}
