package dev.qilletni.intellij.execution;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.qilletni.intellij.runconfig.QilletniBuildRunConfiguration;
import dev.qilletni.intellij.runconfig.QilletniBuildRunConfigurationType;
import dev.qilletni.intellij.runconfig.QilletniRunConfiguration;
import dev.qilletni.intellij.runconfig.QilletniRunConfigurationType;

import java.util.List;

public class QilletniCommandArgsTest extends BasePlatformTestCase {

    private QilletniRunConfiguration runConfiguration() {
        return (QilletniRunConfiguration) QilletniRunConfigurationType.getInstance().getConfigurationFactories()[0]
                .createTemplateConfiguration(getProject());
    }

    private QilletniBuildRunConfiguration buildConfiguration() {
        return (QilletniBuildRunConfiguration) QilletniBuildRunConfigurationType.getInstance().getConfigurationFactories()[0]
                .createTemplateConfiguration(getProject());
    }

    public void testRunPutsOptionsBeforeScriptAndProgramArgs() {
        var cfg = runConfiguration();
        cfg.targetPath = "/p/qilletni-src/main.ql";
        cfg.args = "one \"two words\"";
        var args = QilletniCommandLineState.buildArgs(cfg, "/p/qilletni-src/qilletni.lock", 1234);
        assertEquals(List.of("run", "--lockfile", "/p/qilletni-src/qilletni.lock", "--log-port", "1234",
                "/p/qilletni-src/main.ql", "one", "two words"), args);
    }

    public void testRunWithLocalLibrary() {
        var cfg = runConfiguration();
        cfg.targetPath = "/lib/examples/demo.ql";
        cfg.localLibrary = "/lib";
        cfg.useNativeJar = true;
        var args = QilletniCommandLineState.buildArgs(cfg, null, 1);
        assertEquals("--local-library", args.get(1));
        assertTrue(args.contains("--use-native-jar"));
        assertFalse(args.contains("--lockfile"));
    }

    public void testBuildFlags() {
        var cfg = buildConfiguration();
        cfg.targetPath = "/lib";
        var defaults = QilletniBuildCommandLineState.buildArgs(cfg, 5);
        assertEquals(List.of("build", "--log-port", "5", "/lib"), defaults);

        cfg.rebuildNativeJar = false;
        cfg.verboseGradle = true;
        cfg.outputDir = "/out";
        var custom = QilletniBuildCommandLineState.buildArgs(cfg, 5);
        assertTrue(custom.contains("--no-build-jar"));
        assertTrue(custom.contains("--gradle-verbose"));
        assertFalse(custom.contains("--verbose"));
        assertEquals("/out", custom.get(custom.indexOf("--output-file") + 1));
        assertEquals("/lib", custom.getLast());
    }
}
