package dev.qilletni.intellij.debugger.frames;

import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XNamedValue;
import com.intellij.xdebugger.frame.XValueModifier;
import com.intellij.xdebugger.frame.XValueNode;
import com.intellij.xdebugger.frame.XValuePlace;
import dev.qilletni.intellij.debugger.QilletniDebugProcess;
import dev.qilletni.intellij.debugger.dap.QilletniDapSession;
import org.eclipse.lsp4j.debug.EvaluateResponse;
import org.eclipse.lsp4j.debug.SetVariableArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablePresentationHintKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

/** A DAP variable or evaluation result. Children load lazily from its {@code variablesReference}. */
public final class QilletniValue extends XNamedValue {
    private static final Set<String> PRIMITIVES = Set.of("int", "double", "string", "boolean");

    private final QilletniDebugProcess process;
    /** The container this variable came from, for {@code setVariable}; 0 for evaluation results. */
    private final int parentReference;
    private final Variable variable;

    public QilletniValue(@NotNull QilletniDebugProcess process, int parentReference, @NotNull Variable variable) {
        super(variable.getName() != null ? variable.getName() : "");
        this.process = process;
        this.parentReference = parentReference;
        this.variable = variable;
    }

    /** Wraps an {@code evaluate} result, named by its expression. */
    public static @NotNull QilletniValue ofEvaluation(@NotNull QilletniDebugProcess process, @NotNull String expression,
                                                     @NotNull EvaluateResponse response) {
        var v = new Variable();
        v.setName(expression);
        v.setValue(response.getResult() != null ? response.getResult() : "");
        v.setType(response.getType());
        v.setVariablesReference(response.getVariablesReference());
        v.setNamedVariables(response.getNamedVariables());
        v.setIndexedVariables(response.getIndexedVariables());
        v.setPresentationHint(response.getPresentationHint());
        v.setEvaluateName(expression);
        return new QilletniValue(process, 0, v);
    }

    @Override
    public void computePresentation(@NotNull XValueNode node, @NotNull XValuePlace place) {
        node.setPresentation(icon(), variable.getType(), variable.getValue() != null ? variable.getValue() : "", hasChildren());
    }

    @Override
    public void computeChildren(@NotNull XCompositeNode node) {
        QilletniVariables.load(process, variable.getVariablesReference(), variable.getNamedVariables(), variable.getIndexedVariables(), node, null);
    }

    @Override
    public @Nullable String getEvaluationExpression() {
        return variable.getEvaluateName();
    }

    @Override
    public @Nullable XValueModifier getModifier() {
        if (parentReference <= 0 || !process.supportsSetVariable()) return null;
        return new XValueModifier() {
            @Override
            public void setValue(@NotNull XExpression expression, @NotNull XModificationCallback callback) {
                var args = new SetVariableArguments();
                args.setVariablesReference(parentReference);
                args.setName(variable.getName());
                args.setValue(expression.getExpression());
                process.dap().server().setVariable(args).thenAccept(r -> callback.valueModified()).exceptionally(e -> {
                    callback.errorOccurred(QilletniDapSession.messageOf(e));
                    return null;
                });
            }

            @Override
            public void calculateInitialValueEditorText(XInitialValueCallback callback) {
                callback.setValue(variable.getValue());
            }
        };
    }

    private boolean hasChildren() {
        return variable.getVariablesReference() > 0;
    }

    private Icon icon() {
        var hint = variable.getPresentationHint();
        if (hint != null && VariablePresentationHintKind.PROPERTY.equals(hint.getKind())) return AllIcons.Nodes.Field;
        var type = variable.getType();
        if (type != null && type.endsWith("[]")) return AllIcons.Debugger.Db_array;
        if (type != null && PRIMITIVES.contains(type)) return AllIcons.Debugger.Db_primitive;
        return hasChildren() ? AllIcons.Debugger.Value : AllIcons.Debugger.Db_primitive;
    }
}
