package dev.qilletni.intellij.completion;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionUtil;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniEntityInitialize;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import dev.qilletni.intellij.psi.QilletniFunctionName;
import dev.qilletni.intellij.psi.QilletniLhsCore;
import dev.qilletni.intellij.psi.QilletniParamName;
import dev.qilletni.intellij.psi.QilletniPostfixExpr;
import dev.qilletni.intellij.psi.QilletniPostfixSuffix;
import dev.qilletni.intellij.psi.QilletniPrimaryExpr;
import dev.qilletni.intellij.psi.QilletniPropertyName;
import dev.qilletni.intellij.psi.QilletniTypes;
import dev.qilletni.intellij.psi.QilletniVarName;
import dev.qilletni.intellij.resolve.QilletniIndexFacade;
import dev.qilletni.intellij.resolve.QilletniPsiUtil;
import dev.qilletni.intellij.resolve.QilletniTypeInference;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Collectors;

/**
 * Lookup builders, receiver inference and insert handlers shared by the Qilletni completion providers.
 */
final class QilletniCompletionUtil {
    private QilletniCompletionUtil() {}

    /** Infers the receiver type, preferring the original PSI since the completion copy may be truncated. */
    static @Nullable String inferReceiverType(@Nullable PsiElement primary) {
        return QilletniTypeInference.inferReceiverType(originalOrNull(primary), true);
    }

    /** Like {@link #inferReceiverType}, but also recognizes a bare entity name as a type receiver. */
    static @Nullable QilletniTypeInference.Receiver inferReceiver(QilletniFile file, @Nullable PsiElement primary) {
        return QilletniTypeInference.inferReceiver(file, originalOrNull(primary), true);
    }

    private static @Nullable PsiElement originalOrNull(@Nullable PsiElement element) {
        return element != null ? CompletionUtil.getOriginalOrSelf(element) : null;
    }

    static void addPropertiesForPrimary(QilletniFile file, PsiElement primary, CompletionResultSet result) {
        String receiverType = inferReceiverType(primary);
        if (receiverType == null) return;
        addEntityProperties(file, receiverType, result);
    }

    static void addEntityProperties(QilletniFile file, String receiverType, CompletionResultSet result) {
        var entity = QilletniIndexFacade.findEntityByTypeName(file.getProject(), file, receiverType);
        if (entity == null) return;
        for (var p : PsiTreeUtil.findChildrenOfType(entity, QilletniPropertyName.class)) {
            result.addElement(LookupElementBuilder.create(p, p.getText())
                    .withIcon(AllIcons.Nodes.ObjectTypeAttribute)
                    .withTypeText(receiverType, true));
        }
    }

    /** Entity methods (static ones for a type receiver, instance ones otherwise) and extension methods. */
    static void addMethodsForPrimary(QilletniFile file, PsiElement primary, CompletionResultSet result) {
        var project = file.getProject();
        var receiver = inferReceiver(file, primary);
        if (receiver == null) {
            // Unknown type: offer ANY-based extension methods only
            for (var def : QilletniIndexFacade.listExtensionMethods(project, file, null)) addFunction(result, def);
            return;
        }
        var entity = QilletniIndexFacade.findEntityByTypeName(project, file, receiver.type());
        if (entity != null) {
            for (var def : PsiTreeUtil.findChildrenOfType(entity, QilletniFunctionDef.class)) {
                if (QilletniPsiUtil.isStaticFunctionDef(def) == receiver.isTypeReceiver()) addFunction(result, def);
            }
        }
        if (!receiver.isTypeReceiver()) {
            for (var def : QilletniIndexFacade.listExtensionMethods(project, file, receiver.type())) addFunction(result, def);
        }
    }

    /** Method completions for a member name in a postfix chain or after an assignment's lhs_core. */
    static void addMethodCompletions(PsiElement idLeaf, CompletionResultSet result) {
        if (!(idLeaf.getContainingFile() instanceof QilletniFile file)) return;

        PsiElement primary;
        if (PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixSuffix.class) != null) {
            var postfix = PsiTreeUtil.getParentOfType(idLeaf, QilletniPostfixExpr.class);
            if (postfix == null) return;
            primary = PsiTreeUtil.findChildOfType(postfix, QilletniPrimaryExpr.class);
        } else {
            var lhsCore = PsiTreeUtil.getParentOfType(idLeaf, QilletniLhsCore.class);
            if (lhsCore == null) return;
            primary = PsiTreeUtil.findChildOfType(lhsCore, QilletniPrimaryExpr.class);
        }
        if (primary != null) addMethodsForPrimary(file, primary, result);
    }

    static void addFunction(CompletionResultSet result, QilletniFunctionDef def) {
        var nameEl = PsiTreeUtil.findChildOfType(def, QilletniFunctionName.class);
        if (nameEl == null) return;
        var functionArgs = def.getFunctionDefParams().getParamNameList().stream()
                .map(QilletniParamName::getText)
                .collect(Collectors.joining(", ", "(", ")"));
        result.addElement(LookupElementBuilder.create(def, nameEl.getText())
                .bold()
                .appendTailText(functionArgs, true)
                .withIcon(AllIcons.Nodes.Method)
                .withInsertHandler(CALL_PARENS_INSERT_HANDLER));
    }

    /** Root-scope variable names of the file. */
    static void addTopLevelVariables(QilletniFile file, CompletionResultSet result) {
        for (var vName : PsiTreeUtil.findChildrenOfType(file, QilletniVarName.class)) {
            if (!QilletniPsiUtil.isTopLevel(vName)) continue;
            String typeText = QilletniPsiUtil.getVarDeclarationType(vName);
            result.addElement(LookupElementBuilder.create(vName.getText())
                    .withIcon(AllIcons.Nodes.Variable)
                    .withTypeText(typeText != null ? typeText : "var", true));
        }
    }

    static boolean hasPrecedingDot(PsiElement leaf) {
        return QilletniPsiUtil.isToken(QilletniPsiUtil.prevSignificantLeaf(leaf), QilletniTypes.DOT);
    }

    /** Function/method items: append "()" unless already present, and put the caret inside. */
    static final InsertHandler<LookupElement> CALL_PARENS_INSERT_HANDLER = (context, item) -> insertOrEnterParens(context);

    /** Entity names: add parentheses when after {@code new} or already parsed as entity_initialize. */
    static final InsertHandler<LookupElement> ENTITY_CTOR_PARENS_INSERT_HANDLER = (context, item) -> {
        var editor = context.getEditor();
        var file = context.getFile();
        int tail = context.getTailOffset();

        var caretEl = file.findElementAt(Math.max(0, editor.getCaretModel().getOffset()));
        var beforeTailEl = file.findElementAt(Math.max(0, tail - 1));
        boolean parsedCtor = PsiTreeUtil.getParentOfType(caretEl, QilletniEntityInitialize.class) != null
                || PsiTreeUtil.getParentOfType(beforeTailEl, QilletniEntityInitialize.class) != null;

        PsiElement scanFrom = beforeTailEl != null ? beforeTailEl : caretEl;
        PsiElement prevSig = scanFrom != null ? prevSignificantLeafSkipping(scanFrom, item.getLookupString()) : null;
        boolean afterNew = prevSig != null
                && (QilletniPsiUtil.isToken(prevSig, QilletniTypes.NEW)
                    || QilletniPsiUtil.isToken(QilletniPsiUtil.prevSignificantLeaf(prevSig), QilletniTypes.NEW));
        if (!parsedCtor && !afterNew) return;

        insertOrEnterParens(context);
    };

    private static void insertOrEnterParens(InsertionContext context) {
        var doc = context.getDocument();
        var caretModel = context.getEditor().getCaretModel();
        int tail = context.getTailOffset();
        CharSequence seq = doc.getCharsSequence();

        int offset = tail;
        while (offset < seq.length() && Character.isWhitespace(seq.charAt(offset))) offset++;
        if (offset < seq.length() && seq.charAt(offset) == '(') {
            caretModel.moveToOffset(offset + 1);
            return;
        }
        doc.insertString(tail, "()");
        caretModel.moveToOffset(tail + 1);
    }

    /** The previous significant leaf, skipping the just-inserted identifier once if it comes first. */
    private static @Nullable PsiElement prevSignificantLeafSkipping(PsiElement fromLeaf, @Nullable String justInsertedText) {
        PsiElement prev = QilletniPsiUtil.prevSignificantLeaf(fromLeaf);
        if (justInsertedText != null && QilletniPsiUtil.isToken(prev, QilletniTypes.ID) && justInsertedText.contentEquals(prev.getText())) {
            prev = QilletniPsiUtil.prevSignificantLeaf(prev);
        }
        return prev;
    }
}
