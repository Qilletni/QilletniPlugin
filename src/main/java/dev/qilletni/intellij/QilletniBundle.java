package dev.qilletni.intellij;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/** User-visible strings of the plugin, from {@code messages/QilletniBundle.properties}. */
public final class QilletniBundle {
    private static final String BUNDLE = "messages.QilletniBundle";
    private static final DynamicBundle INSTANCE = new DynamicBundle(QilletniBundle.class, BUNDLE);

    private QilletniBundle() {}

    public static @Nls @NotNull String message(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }

    public static @NotNull Supplier<@Nls String> lazy(@PropertyKey(resourceBundle = BUNDLE) @NotNull String key, Object @NotNull ... params) {
        return INSTANCE.getLazyMessage(key, params);
    }
}
