package dev.qilletni.intellij.debugger.dap;

import java.util.List;

/** The arguments of a {@code qilletni/weightEntrySongs} request. */
public class WeightEntrySongsArguments {
    /** From a {@code qilletni/weightPicked} event. */
    public int weightsId;
    /** The entry's index at each level, outermost first. */
    public List<Integer> indices;
}
