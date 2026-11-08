package dev.qilletni.intellij.debugger.frames;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import dev.qilletni.intellij.debugger.QilletniDebugProcess;
import dev.qilletni.intellij.debugger.QilletniDebuggerEvaluator;
import dev.qilletni.intellij.debugger.dap.QilletniDapSession;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackFramePresentationHint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * One DAP stack frame. Its first scope (normally "Locals") is shown inline; the others become collapsible groups,
 * with expensive ones (normally "Globals") left collapsed, and "Music" placed above the inline variables.
 */
public final class QilletniStackFrame extends XStackFrame {
    /** Shown above the inline Locals, since it's what a music stop was made for. */
    private static final String MUSIC_SCOPE = "Music";
    private final QilletniDebugProcess process;
    private final StackFrame frame;
    private final @Nullable XSourcePosition position;

    public QilletniStackFrame(@NotNull QilletniDebugProcess process, @NotNull StackFrame frame) {
        this.process = process;
        this.frame = frame;
        var file = process.mapper().toFile(frame.getSource());
        position = file != null && frame.getLine() > 0
                ? XDebuggerUtil.getInstance().createPosition(file, frame.getLine() - 1)
                : null;
    }

    @Override
    public @Nullable XSourcePosition getSourcePosition() {
        return position;
    }

    @Override
    public @NotNull XDebuggerEvaluator getEvaluator() {
        return new QilletniDebuggerEvaluator(process, frame.getId());
    }

    /** Identifies "the same" frame across stops, so the Variables view keeps its expansion state while stepping. */
    @Override
    public @NotNull Object getEqualityObject() {
        return Objects.toString(frame.getName()) + "@" + (frame.getSource() != null ? frame.getSource().getPath() : "");
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
        boolean subtle = frame.getPresentationHint() == StackFramePresentationHint.SUBTLE
                || frame.getPresentationHint() == StackFramePresentationHint.LABEL;
        component.append(Objects.toString(frame.getName(), "?"),
                subtle ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (frame.getSource() != null && frame.getLine() > 0) {
            var name = frame.getSource().getName() != null ? frame.getSource().getName() : position != null ? position.getFile().getName() : "";
            component.append(" " + name + ":" + frame.getLine(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        component.setIcon(AllIcons.Debugger.Frame);
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        process.scopes(frame.getId()).thenAccept(response -> {
            var scopes = response != null && response.getScopes() != null ? response.getScopes() : new org.eclipse.lsp4j.debug.Scope[0];
            if (scopes.length == 0) {
                node.addChildren(XValueChildrenList.EMPTY, true);
                return;
            }
            var groups = new XValueChildrenList();
            for (int i = 1; i < scopes.length; i++) {
                var group = new QilletniScopeGroup(process, scopes[i]);
                if (scopes[i].getName() != null && scopes[i].getName().startsWith(MUSIC_SCOPE)) groups.addTopGroup(group);
                else groups.addBottomGroup(group);
            }
            var first = scopes[0];
            QilletniVariables.load(process, first.getVariablesReference(), first.getNamedVariables(), first.getIndexedVariables(), node, groups);
        }).exceptionally(error -> {
            node.setErrorMessage(QilletniDapSession.messageOf(error));
            return null;
        });
    }
}
