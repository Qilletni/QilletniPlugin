package dev.qilletni.intellij.toolchain;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.SystemInfo;
import dev.qilletni.intellij.QilletniBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * The only place that builds command lines for the Qilletni toolchain and qpm, so every feature (run and build
 * configurations, new-project creation, the version check, package actions) launches them the same way:
 * {@code <jdk>/bin/java -cp QilletniToolchain.jar dev.qilletni.toolchain.QilletniToolchainApplication <args>}.
 */
public final class QilletniToolchainLauncher {
    private QilletniToolchainLauncher() {}

    /** A toolchain JAR and a JDK able to run it. */
    public record Toolchain(@NotNull Path jar, @NotNull Sdk jdk) {}

    /** Why the toolchain can't be launched, phrased for the user. */
    public static @Nullable String findProblem(@Nullable Project project) {
        if (QilletniInstallation.toolchainJar() == null) return QilletniBundle.message("toolchain.problem.noJar");
        if (QilletniInstallation.jdk(project) == null) return QilletniBundle.message("toolchain.problem.noJdk", QilletniInstallation.MIN_JDK.getDescription());
        return null;
    }

    public static @NotNull Toolchain resolve(@Nullable Project project) throws ExecutionException {
        var problem = findProblem(project);
        if (problem != null) throw new ExecutionException(problem);
        //noinspection DataFlowIssue: findProblem checked both
        return new Toolchain(QilletniInstallation.toolchainJar(), QilletniInstallation.jdk(project));
    }

    /** Java parameters running the toolchain with the given arguments, for use in a {@code JavaCommandLineState}. */
    public static @NotNull JavaParameters javaParameters(@Nullable Project project, @NotNull List<String> args) throws ExecutionException {
        var toolchain = resolve(project);
        var params = new JavaParameters();
        params.setJdk(toolchain.jdk());
        params.getClassPath().add(toolchain.jar().toString());
        params.setMainClass(QilletniInstallation.TOOLCHAIN_MAIN_CLASS);
        params.getProgramParametersList().addAll(args);
        params.setCharset(StandardCharsets.UTF_8);
        return params;
    }

    /** A command line running the toolchain, for background tasks such as {@code init} or {@code --version}. */
    public static @NotNull GeneralCommandLine toolchainCommandLine(@Nullable Project project, @NotNull List<String> args) throws ExecutionException {
        var toolchain = resolve(project);
        var javaHome = toolchain.jdk().getHomePath();
        if (javaHome == null) throw new ExecutionException(QilletniBundle.message("toolchain.problem.noJdk", QilletniInstallation.MIN_JDK.getDescription()));
        var java = Path.of(javaHome, "bin", SystemInfo.isWindows ? "java.exe" : "java");
        return new GeneralCommandLine(java.toString(), "-cp", toolchain.jar().toString(), QilletniInstallation.TOOLCHAIN_MAIN_CLASS)
                .withParameters(args)
                .withCharset(StandardCharsets.UTF_8);
    }

    /**
     * A command line running {@code qpm}. The launcher picks its own Java from {@code JAVA_HOME}, so it is pointed
     * at the same JDK as the toolchain when one is available.
     */
    public static @NotNull GeneralCommandLine qpmCommandLine(@Nullable Project project, @NotNull List<String> args) throws ExecutionException {
        var qpm = QilletniInstallation.qpmLauncher();
        if (qpm == null) throw new ExecutionException(QilletniBundle.message("toolchain.problem.noQpm"));
        var cmd = new GeneralCommandLine(qpm.toString())
                .withParameters(args)
                .withCharset(StandardCharsets.UTF_8)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE);
        var jdk = QilletniInstallation.jdk(project);
        if (jdk != null && jdk.getSdkType() instanceof JavaSdk && jdk.getHomePath() != null) {
            cmd.withEnvironment("JAVA_HOME", jdk.getHomePath());
        }
        return cmd;
    }
}
