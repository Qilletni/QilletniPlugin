package dev.qilletni.intellij.editor;

import com.intellij.icons.AllIcons;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniIcons;
import dev.qilletni.intellij.psi.QilletniEntityConstructor;
import dev.qilletni.intellij.psi.QilletniEntityDef;
import dev.qilletni.intellij.psi.QilletniEntityPropertyDeclaration;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** A node of the Qilletni structure view: a file, entity, property, constructor or function. */
final class QilletniStructureElement implements StructureViewTreeElement, SortableTreeElement {
    private final PsiElement element;

    QilletniStructureElement(@NotNull PsiElement element) {
        this.element = element;
    }

    @Override
    public Object getValue() {
        return element;
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (element instanceof NavigationItem item) item.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return element instanceof NavigationItem item && item.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return element instanceof NavigationItem item && item.canNavigateToSource();
    }

    @Override
    public @NotNull String getAlphaSortKey() {
        return nameOf(element);
    }

    @Override
    public @NotNull ItemPresentation getPresentation() {
        if (element instanceof PsiFile file) return new PresentationData(file.getName(), null, QilletniIcons.FILE, null);
        if (element instanceof QilletniEntityDef) return new PresentationData(nameOf(element), null, AllIcons.Nodes.Class, null);
        if (element instanceof QilletniEntityPropertyDeclaration property) {
            var type = property.getFirstChild() != null && property.getDocComment() == property.getFirstChild()
                    ? property.getFirstChild().getNextSibling() : property.getFirstChild();
            return new PresentationData(nameOf(element), type != null ? type.getText() : null, AllIcons.Nodes.Property, null);
        }
        if (element instanceof QilletniEntityConstructor) return new PresentationData(nameOf(element), null, AllIcons.Nodes.ClassInitializer, null);
        if (element instanceof QilletniFunctionDef function) {
            var params = function.getFunctionDefParams() != null ? function.getFunctionDefParams().getText() : "";
            var onType = function.getFunctionOnType() != null ? " " + function.getFunctionOnType().getText() : "";
            var icon = function.getStatic() != null ? AllIcons.Nodes.Static : function.getNative() != null ? AllIcons.Nodes.AbstractMethod : AllIcons.Nodes.Function;
            return new PresentationData(nameOf(element) + "(" + params + ")" + onType, null, icon, null);
        }
        return new PresentationData(element.getText(), null, null, null);
    }

    @Override
    public TreeElement @NotNull [] getChildren() {
        var children = new ArrayList<TreeElement>();
        if (element instanceof PsiFile file) {
            // Entities and functions not nested in another definition
            for (var def : PsiTreeUtil.findChildrenOfAnyType(file, QilletniEntityDef.class, QilletniFunctionDef.class)) {
                if (PsiTreeUtil.getParentOfType(def, QilletniEntityDef.class, QilletniFunctionDef.class) == null) {
                    children.add(new QilletniStructureElement(def));
                }
            }
        } else if (element instanceof QilletniEntityDef entity && entity.getEntityBody() != null) {
            var body = entity.getEntityBody();
            List<PsiElement> members = new ArrayList<>(body.getEntityPropertyDeclarationList());
            if (body.getEntityConstructor() != null) members.add(body.getEntityConstructor());
            members.addAll(body.getFunctionDefList());
            members.forEach(member -> children.add(new QilletniStructureElement(member)));
        }
        return children.toArray(TreeElement.EMPTY_ARRAY);
    }

    static @NotNull String nameOf(@NotNull PsiElement element) {
        if (element instanceof QilletniEntityDef entity && entity.getEntityName() != null) return entity.getEntityName().getText();
        if (element instanceof QilletniFunctionDef function && function.getFunctionName() != null) return function.getFunctionName().getText();
        if (element instanceof QilletniEntityPropertyDeclaration property && property.getPropertyName() != null) return property.getPropertyName().getText();
        if (element instanceof QilletniEntityConstructor constructor && constructor.getConstructorName() != null) return constructor.getConstructorName().getText();
        if (element instanceof PsiFile file) return file.getName();
        return "";
    }
}
