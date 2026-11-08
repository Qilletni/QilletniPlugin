package dev.qilletni.intellij.toolchain;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Single source of truth for where Qilletni is installed on this machine.
 * <p>
 * The installer lays things out as:
 * <pre>
 * ~/.qilletni/
 *   bin/qilletni -> ../platforms/&lt;ver&gt;/toolchain/qilletni   (launcher next to QilletniToolchain.jar)
 *   bin/qpm      -> ../platforms/&lt;ver&gt;/qpm/qpm
 *   packages/&lt;scope&gt;/&lt;name&gt;/&lt;name&gt;-&lt;version&gt;.qll
 *   packages-local/...                               (qpm publish-local, preferred over packages/)
 *   persistence/&lt;package&gt;.properties                  (qilletni persist)
 *   config.json                                      (qpm login token)
 * </pre>
 * Every setting in {@link QilletniToolchainSettings} is optional and falls back to this layout.
 */
public final class QilletniInstallation {
    public static final String TOOLCHAIN_MAIN_CLASS = "dev.qilletni.toolchain.QilletniToolchainApplication";
    public static final String TOOLCHAIN_JAR_NAME = "QilletniToolchain.jar";
    public static final JavaSdkVersion MIN_JDK = JavaSdkVersion.JDK_22;

    private QilletniInstallation() {}

    /** The Qilletni home directory: the configured one, else {@code ~/.qilletni}. */
    public static @NotNull Path home() {
        var configured = QilletniToolchainSettings.getInstance().getQilletniHome();
        if (configured != null && !configured.isBlank()) return Path.of(configured.trim());
        return defaultHome();
    }

    public static @NotNull Path defaultHome() {
        return Path.of(System.getProperty("user.home"), ".qilletni");
    }

    /** Packages installed by {@code qpm install}. */
    public static @NotNull Path packagesDir() {
        return home().resolve("packages");
    }

    /** Packages published by {@code qpm publish-local}; these take precedence over {@link #packagesDir()}. */
    public static @NotNull Path localPackagesDir() {
        return home().resolve("packages-local");
    }

    /** Directory holding per-package {@code .properties} written by {@code qilletni persist}. */
    public static @NotNull Path persistenceDir() {
        return home().resolve("persistence");
    }

    /** The qpm config file, present once the user has logged in with {@code qpm login}. */
    public static @NotNull Path qpmConfigFile() {
        return home().resolve("config.json");
    }

    /** The toolchain JAR: the configured one if it exists, else the one detected from the Qilletni home. */
    public static @Nullable Path toolchainJar() {
        var configured = QilletniToolchainSettings.getInstance().getToolchainPath();
        if (configured != null && !configured.isBlank()) {
            var path = Path.of(configured.trim());
            if (Files.isDirectory(path)) path = path.resolve(TOOLCHAIN_JAR_NAME);
            return Files.isRegularFile(path) ? path : null;
        }
        return detectToolchainJar(home());
    }

    /**
     * Detects the toolchain JAR under a Qilletni home. Prefers the platform that {@code bin/qilletni} points to,
     * then the most recently modified {@code platforms/*}/toolchain/QilletniToolchain.jar.
     */
    public static @Nullable Path detectToolchainJar(@NotNull Path home) {
        for (var launcherName : launcherNames("qilletni")) {
            var launcher = home.resolve("bin").resolve(launcherName);
            if (!Files.exists(launcher)) continue;
            try {
                var jar = launcher.toRealPath().resolveSibling(TOOLCHAIN_JAR_NAME);
                if (Files.isRegularFile(jar)) return jar;
            } catch (IOException ignored) {
                // Broken symlink; fall through to scanning platforms/
            }
        }

        var platforms = home.resolve("platforms");
        if (!Files.isDirectory(platforms)) return null;
        try (var dirs = Files.list(platforms)) {
            return dirs.map(dir -> dir.resolve("toolchain").resolve(TOOLCHAIN_JAR_NAME))
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(QilletniInstallation::lastModified))
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The {@code qpm} launcher script. The launcher is used rather than QPM.jar directly because it sets
     * environment the CLI needs (such as the GitHub OAuth client ID used by {@code qpm login}).
     */
    public static @Nullable Path qpmLauncher() {
        for (var name : launcherNames("qpm")) {
            var launcher = home().resolve("bin").resolve(name);
            if (Files.isRegularFile(launcher)) return launcher;
        }
        for (var name : launcherNames("qpm")) {
            var onPath = PathEnvironmentVariableUtil.findInPath(name);
            if (onPath != null) return onPath.toPath();
        }
        return null;
    }

    private static List<String> launcherNames(String base) {
        return SystemInfo.isWindows ? List.of(base + ".cmd", base + ".bat") : List.of(base);
    }

    /** The JDK used to run the toolchain: the configured JDK, else the Project SDK, else the newest suitable JDK. */
    public static @Nullable Sdk jdk(@Nullable Project project) {
        var jdkName = QilletniToolchainSettings.getInstance().getJdkName();
        if (jdkName != null && !jdkName.isBlank()) {
            var configured = ProjectJdkTable.getInstance().findJdk(jdkName);
            if (configured != null) return configured;
        }
        if (project != null && !project.isDefault()) {
            var projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
            if (projectSdk != null && projectSdk.getSdkType() instanceof JavaSdk && isSupportedJdk(projectSdk)) {
                return projectSdk;
            }
        }
        return ProjectJdkTable.getInstance().getSdksOfType(JavaSdk.getInstance()).stream()
                .filter(QilletniInstallation::isSupportedJdk)
                .max(Comparator.comparing(sdk -> Optional.ofNullable(JavaSdk.getInstance().getVersion(sdk)).orElse(MIN_JDK)))
                .orElse(null);
    }

    public static boolean isSupportedJdk(@NotNull Sdk sdk) {
        var version = JavaSdk.getInstance().getVersion(sdk);
        return version != null && version.isAtLeast(MIN_JDK);
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
