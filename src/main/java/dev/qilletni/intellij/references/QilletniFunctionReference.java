package dev.qilletni.intellij.references;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.QilletniFile;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import dev.qilletni.intellij.psi.QilletniFunctionName;
import dev.qilletni.intellij.psi.impl.QilletniElementFactory;
import dev.qilletni.intellij.resolve.QilletniIndexFacade;
import dev.qilletni.intellij.resolve.QilletniResolveUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reference for function call callee identifiers (no receiver).
 */
public final class QilletniFunctionReference extends PsiReferenceBase<PsiElement> {
    public QilletniFunctionReference(@NotNull PsiElement element, @NotNull TextRange rangeInElement) {
        super(element, rangeInElement);
    }

    @Override
    public @Nullable PsiElement resolve() {
        return QilletniResolveUtil.resolveFunctionUsage(getElement());
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) {
        return QilletniElementFactory.renameIdentifier(getElement(), newElementName);
    }

    @Override
    public Object @NotNull [] getVariants() {
        // Provide function-name completion that includes functions from all imports (project-local + libraries),
        // so that self-library functions from 'qilletni-src' are suggested within 'examples'.
        var el = getElement();
        var file = el.getContainingFile();
        if (!(file instanceof QilletniFile qf)) return EMPTY_ARRAY;
        Project project = el.getProject();

        List<QilletniFunctionDef> defs = QilletniIndexFacade.listTopLevelFunctions(project, qf);
        Set<String> names = new LinkedHashSet<>();
        for (var def : defs) {
            if (def == null || !def.isValid()) continue;
            var name = PsiTreeUtil.findChildOfType(def, QilletniFunctionName.class);
            if (name == null) continue;
            var id = name.getId();
            if (id == null) continue;
            var s = id.getText();
            if (s != null && !s.isBlank()) names.add(s);
        }
        if (names.isEmpty()) return EMPTY_ARRAY;
        return names.toArray();
    }
}
