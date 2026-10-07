package dev.qilletni.intellij.library;

import com.intellij.navigation.ItemPresentation;
import dev.qilletni.intellij.QilletniIcons;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Synthetic library representing binary/class roots (e.g., native.jar) for a Qilletni library.
 * Exposes the jar via getBinaryRoots() so Java indices/class resolution can see classes.
 */
public final class QilletniBinarySyntheticLibrary extends SyntheticLibrary implements ItemPresentation {
    private final String name;
    private final String presentableName;
    private final List<VirtualFile> binaryRoots;
    private final List<VirtualFile> sourceRoots;

    public QilletniBinarySyntheticLibrary(String name, String presentableName,
                                          List<VirtualFile> binaryRoots,
                                          List<VirtualFile> sourceRoots) {
        this.name = Objects.requireNonNull(name);
        this.presentableName = presentableName;
        this.binaryRoots = List.copyOf(binaryRoots);
        this.sourceRoots = List.copyOf(sourceRoots);
    }

    public String getName() { return name; }

    @Override
    public @NotNull Collection<VirtualFile> getBinaryRoots() {
        return binaryRoots;
    }

    @Override
    public @NotNull Collection<VirtualFile> getSourceRoots() {
        return sourceRoots;
    }

    /** Label of the node in External Libraries. */
    @Override
    public @Nullable String getPresentableText() {
        return presentableName;
    }

    @Override
    public @Nullable Icon getIcon(boolean unused) {
        return QilletniIcons.FILE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QilletniBinarySyntheticLibrary that)) return false;
        return Objects.equals(name, that.name)
                && Objects.equals(binaryRoots, that.binaryRoots)
                && Objects.equals(sourceRoots, that.sourceRoots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, binaryRoots, sourceRoots);
    }
}
