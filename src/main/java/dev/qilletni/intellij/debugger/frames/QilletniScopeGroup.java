package dev.qilletni.intellij.debugger.frames;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueGroup;
import dev.qilletni.intellij.debugger.QilletniDebugProcess;
import org.eclipse.lsp4j.debug.Scope;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/** A DAP scope other than the frame's first one, shown as a collapsible group. */
final class QilletniScopeGroup extends XValueGroup {
    private final QilletniDebugProcess process;
    private final Scope scope;

    QilletniScopeGroup(@NotNull QilletniDebugProcess process, @NotNull Scope scope) {
        super(scope.getName());
        this.process = process;
        this.scope = scope;
    }

    @Override
    public boolean isAutoExpand() {
        return !scope.isExpensive();
    }

    @Override
    public Icon getIcon() {
        return AllIcons.Nodes.Folder;
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        QilletniVariables.load(process, scope.getVariablesReference(), scope.getNamedVariables(), scope.getIndexedVariables(), node, null);
    }
}
