package dev.qilletni.intellij.debugger.frames;

import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XValueChildrenList;
import dev.qilletni.intellij.debugger.QilletniDebugProcess;
import dev.qilletni.intellij.debugger.dap.QilletniDapSession;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArgumentsFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Loads a container's children into a tree node. When the adapter reports {@code indexedVariables} (lists), named
 * children come first and indexed ones are paged, {@link #PAGE} at a time, behind a "more" link; otherwise all
 * children are requested at once.
 */
final class QilletniVariables {
    static final int PAGE = XCompositeNode.MAX_CHILDREN_TO_SHOW;

    private QilletniVariables() {}

    /** {@code trailing}, if given, is appended after the last child (the frame's other scopes). */
    static void load(@NotNull QilletniDebugProcess process, int reference, @Nullable Integer named, @Nullable Integer indexed,
                     @NotNull XCompositeNode node, @Nullable XValueChildrenList trailing) {
        if (reference <= 0) {
            node.addChildren(trailing != null ? trailing : XValueChildrenList.EMPTY, true);
            return;
        }
        if (indexed == null || indexed <= 0) {
            process.variables(reference, null, null, null).thenAccept(vars -> {
                var list = toList(process, reference, vars);
                if (trailing != null) addAll(list, trailing);
                node.addChildren(list, true);
            }).exceptionally(e -> fail(node, e));
            return;
        }
        if (named != null && named > 0) {
            process.variables(reference, VariablesArgumentsFilter.NAMED, null, null)
                    .thenAccept(vars -> {
                        node.addChildren(toList(process, reference, vars), false);
                        loadPage(process, reference, indexed, 0, node, trailing);
                    }).exceptionally(e -> fail(node, e));
        } else {
            loadPage(process, reference, indexed, 0, node, trailing);
        }
    }

    private static void loadPage(QilletniDebugProcess process, int reference, int indexed, int start,
                                 XCompositeNode node, @Nullable XValueChildrenList trailing) {
        int count = Math.min(PAGE, indexed - start);
        process.variables(reference, VariablesArgumentsFilter.INDEXED, start, count).thenAccept(vars -> {
            var list = toList(process, reference, vars);
            int next = start + count;
            if (next >= indexed) {
                if (trailing != null) addAll(list, trailing);
                node.addChildren(list, true);
            } else {
                node.addChildren(list, false);
                node.tooManyChildren(indexed - next, () -> loadPage(process, reference, indexed, next, node, trailing));
            }
        }).exceptionally(e -> fail(node, e));
    }

    private static XValueChildrenList toList(QilletniDebugProcess process, int parent, Variable[] vars) {
        var list = new XValueChildrenList(vars.length);
        for (var v : vars) list.add(new QilletniValue(process, parent, v));
        return list;
    }

    private static void addAll(XValueChildrenList target, XValueChildrenList source) {
        for (int i = 0; i < source.size(); i++) target.add(source.getName(i), source.getValue(i));
        for (var group : source.getBottomGroups()) target.addBottomGroup(group);
        for (var group : source.getTopGroups()) target.addTopGroup(group);
    }

    private static Void fail(XCompositeNode node, Throwable e) {
        node.setErrorMessage(QilletniDapSession.messageOf(e));
        return null;
    }
}
