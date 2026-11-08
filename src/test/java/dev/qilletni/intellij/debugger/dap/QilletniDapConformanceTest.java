package dev.qilletni.intellij.debugger.dap;

import junit.framework.TestCase;
import org.eclipse.lsp4j.debug.Breakpoint;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.EvaluateArgumentsContext;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.ScopesArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetExceptionBreakpointsArguments;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StepInArguments;
import org.eclipse.lsp4j.debug.StepOutArguments;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Checks a real toolchain against the debugger contract in {@code docs/debugger-protocol.md}, one test per milestone.
 * Skipped unless {@code QILLETNI_TOOLCHAIN_JAR} is set. Optional environment:
 * <ul>
 *   <li>{@code QILLETNI_JAVA}: a JDK 22+ {@code java} executable (default: {@code java} on the PATH);</li>
 *   <li>{@code QILLETNI_DEBUG_MILESTONE}: the highest milestone to check, e.g. {@code 2} runs M0 to M2 (default: all).</li>
 * </ul>
 * Example: {@code QILLETNI_TOOLCHAIN_JAR=... QILLETNI_DEBUG_MILESTONE=1 ./gradlew test --tests '*QilletniDapConformanceTest'}
 */
public class QilletniDapConformanceTest extends TestCase {
    private static final String MAIN_CLASS = "dev.qilletni.toolchain.QilletniToolchainApplication";
    private static final Path DATA = Path.of("src/test/testData/debugger");
    private static final long TIMEOUT_SECONDS = 60;

    private Program program;

    @Override
    protected void tearDown() throws Exception {
        try {
            if (program != null) program.close();
        } finally {
            super.tearDown();
        }
    }

    /** Whether the toolchain is configured and {@code milestone} is within the requested range. */
    private static boolean enabled(int milestone) {
        if (System.getenv("QILLETNI_TOOLCHAIN_JAR") == null) return false;
        var max = System.getenv("QILLETNI_DEBUG_MILESTONE");
        return max == null || milestone <= Integer.parseInt(max.trim());
    }

    public void testM0RunsToCompletion() throws Exception {
        if (!enabled(0)) return;
        program = Program.start("basic.ql");
        program.configure(Map.of());
        program.assertExitsCleanly();
    }

    public void testM1StopsAtBreakpoint() throws Exception {
        if (!enabled(1)) return;
        program = Program.start("basic.ql");
        var results = program.configure(Map.of("basic.ql", new int[]{7}));
        var result = results.get("basic.ql")[0];
        assertTrue("breakpoint on line 7 should be verified: " + result, result.isVerified());
        assertEquals(Integer.valueOf(7), result.getLine());

        var stop = program.awaitStop();
        assertEquals("breakpoint", stop.getReason());
        var top = program.stack()[0];
        assertEquals(7, top.getLine());
        assertEquals(program.path("basic.ql"), top.getSource().getPath());

        program.resume();
        program.assertExitsCleanly();
    }

    public void testM2FramesAndVariables() throws Exception {
        if (!enabled(2)) return;
        program = Program.start("basic.ql");
        program.configure(Map.of("basic.ql", new int[]{2}));
        program.awaitStop();

        var stack = program.stack();
        assertTrue("expected the add frame and the top-level frame: " + Arrays.toString(stack), stack.length >= 2);
        assertEquals("add", stack[0].getName());
        assertEquals(2, stack[0].getLine());
        assertEquals(7, stack[1].getLine());

        var scopes = program.dap.server().scopes(scopesArgs(stack[0].getId())).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getScopes();
        assertTrue(scopes.length > 0);
        assertEquals("Locals", scopes[0].getName());
        assertEquals("1", program.variable(stack[0].getId(), "a").getValue());
        assertEquals("2", program.variable(stack[0].getId(), "b").getValue());

        program.resume();
        program.awaitExit();
    }

    public void testM3Stepping() throws Exception {
        if (!enabled(3)) return;
        program = Program.start("basic.ql");
        program.configure(Map.of("basic.ql", new int[]{6}));
        program.awaitStop();

        var next = new NextArguments();
        next.setThreadId(QilletniDapSession.THREAD_ID);
        program.dap.server().next(next).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertEquals("step", program.awaitStop().getReason());
        assertEquals(7, program.stack()[0].getLine());

        var stepIn = new StepInArguments();
        stepIn.setThreadId(QilletniDapSession.THREAD_ID);
        program.dap.server().stepIn(stepIn).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        program.awaitStop();
        var inside = program.stack();
        assertEquals(2, inside[0].getLine());
        assertEquals("add", inside[0].getName());

        var stepOut = new StepOutArguments();
        stepOut.setThreadId(QilletniDapSession.THREAD_ID);
        program.dap.server().stepOut(stepOut).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        program.awaitStop();
        // Line 7's statement already started before the call, so the next statement in the caller is line 8
        assertEquals(8, program.stack()[0].getLine());

        program.resume();
        program.awaitExit();
    }

    public void testM4StructuredValues() throws Exception {
        if (!enabled(4)) return;
        program = Program.start("values.ql");
        program.configure(Map.of("values.ql", new int[]{9}));
        program.awaitStop();
        int frame = program.stack()[0].getId();

        var box = program.variable(frame, "b");
        assertTrue("an entity should have children", box.getVariablesReference() > 0);
        var fields = program.children(box.getVariablesReference(), null, null, null);
        assertEquals("3", find(fields, "size").getValue());
        assertEquals("\"box\"", find(fields, "label").getValue());

        var nums = program.variable(frame, "nums");
        assertEquals(Integer.valueOf(3), nums.getIndexedVariables());
        var items = program.children(nums.getVariablesReference(), VariablesArgumentsFilter.INDEXED, 1, 2);
        assertEquals(2, items.length);
        assertEquals("[1]", items[0].getName());
        assertEquals("2", items[0].getValue());
        assertEquals("3", items[1].getValue());

        program.resume();
        program.awaitExit();
    }

    public void testM5Evaluate() throws Exception {
        if (!enabled(5)) return;
        program = Program.start("basic.ql");
        program.configure(Map.of("basic.ql", new int[]{7}));
        program.awaitStop();
        int frame = program.stack()[0].getId();

        assertEquals("2", program.evaluate(frame, "x + 1").get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getResult());
        try {
            program.evaluate(frame, "doesNotExist").get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            fail("Evaluating an unknown name should fail");
        } catch (ExecutionException expected) {
            assertFalse(QilletniDapSession.messageOf(expected).isBlank());
        }

        program.resume();
        program.awaitExit();
    }

    public void testM6BreakpointInImportedFile() throws Exception {
        if (!enabled(6)) return;
        program = Program.start("imports.ql");
        var result = program.configure(Map.of("helper.ql", new int[]{2})).get("helper.ql")[0];
        // Pending (not yet parsed) or verified are both fine; an error message is not
        assertTrue("breakpoint in an imported file must not be rejected: " + result,
                result.isVerified() || result.getMessage() == null);

        program.awaitStop();
        var top = program.stack()[0];
        assertEquals(2, top.getLine());
        assertEquals(program.path("helper.ql"), top.getSource().getPath());

        program.resume();
        program.awaitExit();
    }

    /**
     * Only checks the filters are advertised and accepted: actually stopping needs a music provider, which the
     * conformance programs don't have. Check those stops by hand in a sandbox IDE.
     */
    public void testM8WeightChances() throws Exception {
        if (!enabled(8)) return;
        program = Program.start("weights.ql");
        // Line 2 is inside pick(), which building the weights must not call
        program.configure(Map.of("weights.ql", new int[]{2, 16}));
        program.awaitStop();
        assertEquals("a function entry must not be called until it's picked", 16, program.stack()[0].getLine());
        int frame = program.stack()[0].getId();

        var w = program.children(program.variable(frame, "w").getVariablesReference(), null, null, null);
        var chances = find(w, "chances");
        assertEquals("3 paths", chances.getValue());
        var paths = program.children(chances.getVariablesReference(), null, null, null);
        assertEquals(List.of("15%", "15%", "70%"), Arrays.stream(paths).map(Variable::getValue).toList());
        assertTrue(paths[0].getName(), paths[0].getName().contains(" → "));

        var partial = program.children(program.variable(frame, "partial").getVariablesReference(), null, null, null);
        var partialChances = find(partial, "chances");
        assertEquals("1 path, 80% miss", partialChances.getValue());
        var partialRows = program.children(partialChances.getVariablesReference(), null, null, null);
        assertEquals(List.of("20%", "copies in the collection's queue, not rolled", "80% → the collection's own tracks"),
                Arrays.stream(partialRows).map(Variable::getValue).toList());

        var funcs = program.children(program.variable(frame, "funcs").getVariablesReference(), null, null, null);
        assertEquals("pick()", funcs[0].getValue());
        var funcsChances = program.children(find(funcs, "chances").getVariablesReference(), null, null, null);
        var funcEntry = program.children(funcsChances[0].getVariablesReference(), null, null, null);
        assertEquals("pick()", find(funcEntry, "entry").getValue());

        program.resume();
        program.awaitExit();
    }

    public void testM7MusicFilters() throws Exception {
        if (!enabled(7)) return;
        program = Program.start("basic.ql");
        var filters = program.dap.capabilities().getExceptionBreakpointFilters();
        assertNotNull("exceptionBreakpointFilters must be advertised", filters);
        for (var name : List.of("songPlayed", "weightSelected")) {
            var filter = Arrays.stream(filters).filter(f -> name.equals(f.getFilter())).findFirst().orElse(null);
            assertNotNull(name + " filter must be advertised", filter);
            assertFalse(name + " must be off by default", Boolean.TRUE.equals(filter.getDefault_()));
        }

        program.dap.configure(false, () -> {
            var args = new SetExceptionBreakpointsArguments();
            args.setFilters(new String[]{"uncaught", "songPlayed", "weightSelected"});
            return program.dap.server().setExceptionBreakpoints(args).thenApply(r -> null);
        }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // basic.ql plays nothing, so it must run to the end without stopping
        program.assertExitsCleanly();
    }

    private static ScopesArguments scopesArgs(int frameId) {
        var args = new ScopesArguments();
        args.setFrameId(frameId);
        return args;
    }

    private static Variable find(Variable[] variables, String name) {
        return Arrays.stream(variables).filter(v -> name.equals(v.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("No variable " + name + " in " + Arrays.toString(variables)));
    }

    /** A toolchain process under debug, with events collected into a queue. */
    private static final class Program implements QilletniDapSession.Listener, AutoCloseable {
        private final Process process;
        private final Path output;
        private final QilletniDapSession dap;
        private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();

        private Program(Process process, Path output, QilletniDapSession dap) {
            this.process = process;
            this.output = output;
            this.dap = dap;
        }

        static Program start(String fixture) throws Exception {
            var dap = new QilletniDapSession();
            var output = Files.createTempFile("qilletni-debug", ".log");
            var java = System.getenv().getOrDefault("QILLETNI_JAVA", "java");
            var command = List.of(java, "-cp", System.getenv("QILLETNI_TOOLCHAIN_JAR"), MAIN_CLASS,
                    "run", "--debug-port", Integer.toString(dap.getPort()), DATA.resolve(fixture).toAbsolutePath().toString());
            var process = new ProcessBuilder(command).directory(DATA.toFile()).redirectErrorStream(true)
                    .redirectOutput(output.toFile()).start();
            var program = new Program(process, output, dap);
            try {
                dap.connect(program, process::isAlive, Duration.ofSeconds(TIMEOUT_SECONDS)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new AssertionError("Toolchain did not connect.\n" + program.describe(), e);
            }
            return program;
        }

        String path(String fixture) throws Exception {
            return DATA.resolve(fixture).toRealPath().toString();
        }

        /** Runs the configuration phase with the given breakpoints (1-based lines) and returns the adapter's results. */
        Map<String, Breakpoint[]> configure(Map<String, int[]> breakpoints) throws Exception {
            var results = new LinkedHashMap<String, Breakpoint[]>();
            dap.configure(false, () -> {
                var sent = new ArrayList<CompletableFuture<?>>();
                breakpoints.forEach((fixture, lines) -> {
                    var args = new SetBreakpointsArguments();
                    var source = new Source();
                    try {
                        source.setPath(path(fixture));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    source.setName(fixture);
                    args.setSource(source);
                    args.setBreakpoints(Arrays.stream(lines).mapToObj(line -> {
                        var bp = new SourceBreakpoint();
                        bp.setLine(line);
                        return bp;
                    }).toArray(SourceBreakpoint[]::new));
                    sent.add(dap.server().setBreakpoints(args).thenAccept(r -> results.put(fixture, r.getBreakpoints())));
                });
                return CompletableFuture.allOf(sent.toArray(CompletableFuture[]::new));
            }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return results;
        }

        StoppedEventArguments awaitStop() throws Exception {
            var event = events.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (event instanceof StoppedEventArguments stopped) return stopped;
            throw new AssertionError("Expected a stop, got " + event + "\n" + describe());
        }

        int awaitExit() throws Exception {
            assertTrue("The program did not exit.\n" + describe(), process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            return process.exitValue();
        }

        /** Waits for the exit before reading the output, so a failure message includes everything the program printed. */
        void assertExitsCleanly() throws Exception {
            int exitCode = awaitExit();
            assertEquals(describe(), 0, exitCode);
        }

        void resume() throws Exception {
            var args = new ContinueArguments();
            args.setThreadId(QilletniDapSession.THREAD_ID);
            dap.server().continue_(args).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        StackFrame[] stack() throws Exception {
            var args = new StackTraceArguments();
            args.setThreadId(QilletniDapSession.THREAD_ID);
            var frames = dap.server().stackTrace(args).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getStackFrames();
            assertTrue("The stack is empty", frames.length > 0);
            return frames;
        }

        Variable[] children(int reference, VariablesArgumentsFilter filter, Integer start, Integer count) throws Exception {
            var args = new VariablesArguments();
            args.setVariablesReference(reference);
            args.setFilter(filter);
            args.setStart(start);
            args.setCount(count);
            return dap.server().variables(args).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getVariables();
        }

        /** A variable visible in {@code frameId}, searching its scopes in order. */
        Variable variable(int frameId, String name) throws Exception {
            for (var scope : dap.server().scopes(scopesArgs(frameId)).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).getScopes()) {
                for (var v : children(scope.getVariablesReference(), null, null, null)) {
                    if (name.equals(v.getName())) return v;
                }
            }
            throw new AssertionError("No variable " + name + " in frame " + frameId);
        }

        CompletableFuture<org.eclipse.lsp4j.debug.EvaluateResponse> evaluate(int frameId, String expression) {
            var args = new EvaluateArguments();
            args.setFrameId(frameId);
            args.setExpression(expression);
            args.setContext(EvaluateArgumentsContext.WATCH);
            return dap.server().evaluate(args);
        }

        String describe() {
            try {
                return "Toolchain output:\n" + Files.readString(output);
            } catch (Exception e) {
                return "(no toolchain output)";
            }
        }

        @Override
        public void stopped(@NotNull StoppedEventArguments args) {
            events.add(args);
        }

        @Override
        public void launchFailed(@NotNull String message) {
            events.add("launch failed: " + message);
        }

        @Override
        public void close() throws Exception {
            dap.close();
            if (process.isAlive()) process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            Files.deleteIfExists(output);
        }
    }
}
