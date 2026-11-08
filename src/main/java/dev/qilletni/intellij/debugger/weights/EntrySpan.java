package dev.qilletni.intellij.debugger.weights;

import dev.qilletni.intellij.debugger.dap.WeightPickedEventArguments;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where a weight entry is written. Lines and columns are 1-based; the end column is just past the last character.
 *
 * @param path The DAP source path
 */
public record EntrySpan(@NotNull String path, int line, int column, int endLine, int endColumn) {

    /** @return Where {@code node} is written, or {@code null} if the adapter didn't say */
    public static @Nullable EntrySpan of(@NotNull WeightPickedEventArguments.Node node) {
        return node.sourcePath == null || node.line <= 0 ? null
                : new EntrySpan(node.sourcePath, node.line, node.column, node.endLine, node.endColumn);
    }
}
