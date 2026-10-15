package dev.qilletni.intellij.execution;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import dev.qilletni.intellij.runconfig.QilletniBuildRunConfiguration;
import dev.qilletni.intellij.runconfig.QilletniBuildRunConfigurationType;
import dev.qilletni.intellij.runconfig.QilletniRunConfiguration;
import dev.qilletni.intellij.runconfig.QilletniRunConfigurationType;
import org.jdom.Element;

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

    public void testRunConsoleLogLevel() {
        var info = QilletniLogStream.Level.INFO;
        var debug = QilletniLogStream.Level.DEBUG;
        assertTrue(QilletniCommandLineState.shouldShow(QilletniLogStream.Level.ERROR, info));
        assertTrue(QilletniCommandLineState.shouldShow(QilletniLogStream.Level.WARN, info));
        assertTrue(QilletniCommandLineState.shouldShow(info, info));
        assertFalse(QilletniCommandLineState.shouldShow(debug, info));
        assertFalse(QilletniCommandLineState.shouldShow(QilletniLogStream.Level.TRACE, info));
        assertTrue(QilletniCommandLineState.shouldShow(info, debug));
        assertTrue(QilletniCommandLineState.shouldShow(debug, debug));
        assertFalse(QilletniCommandLineState.shouldShow(QilletniLogStream.Level.TRACE, debug));
    }

    public void testRunLogLevelPersists() {
        var cfg = runConfiguration();
        cfg.logLevel = QilletniLogStream.Level.DEBUG;
        var element = new Element("configuration");
        cfg.writeExternal(element);
        var restored = runConfiguration();
        restored.readExternal(element);
        assertEquals(QilletniLogStream.Level.DEBUG, restored.logLevel);

        var fresh = runConfiguration();
        fresh.readExternal(new Element("configuration"));
        assertEquals(QilletniLogStream.Level.INFO, fresh.logLevel);
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
