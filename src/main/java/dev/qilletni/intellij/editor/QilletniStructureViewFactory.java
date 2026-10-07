package dev.qilletni.intellij.editor;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.StructureViewModel;
import com.intellij.ide.structureView.StructureViewModelBase;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.PsiStructureViewFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiFile;
import dev.qilletni.intellij.psi.QilletniEntityConstructor;
import dev.qilletni.intellij.psi.QilletniEntityDef;
import dev.qilletni.intellij.psi.QilletniEntityPropertyDeclaration;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Structure tool window and File Structure popup (Ctrl+F12) for .ql files. */
public final class QilletniStructureViewFactory implements PsiStructureViewFactory {
    @Override
    public @Nullable StructureViewBuilder getStructureViewBuilder(@NotNull PsiFile psiFile) {
        return new TreeBasedStructureViewBuilder() {
            @Override
            public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
                return new Model(psiFile, editor);
            }
        };
    }

    private static final class Model extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {
        Model(PsiFile file, @Nullable Editor editor) {
            super(file, editor, new QilletniStructureElement(file));
            withSorters(Sorter.ALPHA_SORTER);
            withSuitableClasses(QilletniEntityDef.class, QilletniFunctionDef.class,
                    QilletniEntityPropertyDeclaration.class, QilletniEntityConstructor.class);
        }

        @Override
        public boolean isAlwaysShowsPlus(com.intellij.ide.structureView.StructureViewTreeElement element) {
            return false;
        }

        @Override
        public boolean isAlwaysLeaf(com.intellij.ide.structureView.StructureViewTreeElement element) {
            return !(element.getValue() instanceof QilletniEntityDef) && !(element.getValue() instanceof PsiFile);
        }
    }
}
