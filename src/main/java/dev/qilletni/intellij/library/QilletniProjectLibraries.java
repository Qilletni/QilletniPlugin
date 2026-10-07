package dev.qilletni.intellij.library;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Earlier plugin versions mirrored every installed package as a project library named {@code "Qilletni: <name>"}
 * and attached it to every module. Libraries now come from {@link QilletniAdditionalLibraryRootsProvider}, so this
 * removes those leftovers once per project.
 */
public final class QilletniProjectLibraries {
    private static final String LEGACY_PREFIX = "Qilletni: ";

    private QilletniProjectLibraries() {}

    public static void removeLegacyLibraries(@NotNull Project project) {
        var table = LibraryTablesRegistrar.getInstance().getLibraryTable(project);
        var legacy = Arrays.stream(table.getLibraries())
                .filter(lib -> lib.getName() != null && lib.getName().startsWith(LEGACY_PREFIX))
                .toList();
        if (legacy.isEmpty()) return;

        ApplicationManager.getApplication().invokeLater(() -> WriteAction.run(() -> {
            for (var module : ModuleManager.getInstance(project).getModules()) {
                ModuleRootModificationUtil.updateModel(module, model -> {
                    for (var entry : model.getOrderEntries()) {
                        if (entry instanceof LibraryOrderEntry libEntry
                                && libEntry.getLibraryName() != null
                                && libEntry.getLibraryName().startsWith(LEGACY_PREFIX)) {
                            model.removeOrderEntry(entry);
                        }
                    }
                });
            }
            var model = table.getModifiableModel();
            legacy.forEach(model::removeLibrary);
            model.commit();
        }), project.getDisposed());
    }
}
