package dev.qilletni.intellij.yaml;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLScalar;

/** Reference from a YAML native_classes scalar to a Java PsiClass in project sources. */
public class QilletniYamlNativeClassReference extends PsiReferenceBase<YAMLScalar> {
    public QilletniYamlNativeClassReference(@NotNull YAMLScalar element) {
        // Anchor the reference to the entire scalar text within the element (relative range)
        super(element, new TextRange(0, element.getTextLength()));
    }

    @Override
    public @Nullable PsiElement resolve() {
        var element = getElement();
        var project = element.getProject();
        if (DumbService.isDumb(project)) return null;
        var fqn = element.getTextValue().trim();
        if (fqn.isEmpty()) return null;
        var scope = GlobalSearchScope.projectScope(project);
        PsiClass cls = JavaPsiFacade.getInstance(project).findClass(fqn, scope);
        if (cls == null) return null;
        PsiFile file = cls.getContainingFile();
        VirtualFile vf = file != null ? file.getVirtualFile() : null;
        if (vf == null) return null;
        // Ensure it is strictly in project content (not in libraries)
        if (!ProjectFileIndex.getInstance(project).isInContent(vf)) return null;
        return cls;
    }

    @Override
    public Object @NotNull [] getVariants() {
        // Completion is handled by a dedicated CompletionContributor
        return EMPTY_ARRAY;
    }

}