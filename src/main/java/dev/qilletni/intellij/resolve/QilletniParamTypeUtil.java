package dev.qilletni.intellij.resolve;

import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiTreeUtil;
import dev.qilletni.intellij.psi.QilletniFunctionDef;
import dev.qilletni.intellij.psi.QilletniParamName;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Parameter typing utilities for Qilletni.
 *
 * Assigns the first parameter of an extension function (with "on <type>") the same type as the receiver.
 * Returns Optional.empty() for all other parameters.
 */
public final class QilletniParamTypeUtil {
    private QilletniParamTypeUtil() {}

    /**
     * If the given param is the first parameter of an extension function, returns the receiver type text.
     * Otherwise returns Optional.empty().
     */
    public static @NotNull Optional<String> getParamType(@NotNull QilletniParamName param) {
        var def = PsiTreeUtil.getParentOfType(param, QilletniFunctionDef.class);
        if (def == null) return Optional.empty();
        var params = def.getFunctionDefParams();
        if (params == null) return Optional.empty();
        QilletniParamName first = PsiTreeUtil.findChildOfType(params, QilletniParamName.class);
        if (first != param) return Optional.empty();
        if (def.getFunctionOnType() == null) return Optional.empty();
        return getOnTypeCached(def);
    }

    /**
     * Returns the on-type text for an extension function, cached per function node.
     */
    public static @NotNull Optional<String> getOnTypeCached(@NotNull QilletniFunctionDef def) {
        return CachedValuesManager.getCachedValue(def, () ->
                CachedValueProvider.Result.create(extractOnType(def), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static @NotNull Optional<String> extractOnType(@NotNull QilletniFunctionDef def) {
        var token = QilletniPsiUtil.receiverTypeToken(def.getFunctionOnType());
        return Optional.ofNullable(token).map(t -> t.getText()).filter(s -> !s.isBlank());
    }
}
