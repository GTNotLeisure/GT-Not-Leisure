package com.science.gtnl.common.crafting.quantum.crafting.graph;

import java.util.ArrayDeque;

import com.science.gtnl.common.crafting.quantum.api.stacks.AEKey;
import com.science.gtnl.common.crafting.quantum.crafting.CraftingCalculation;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class MaterialClosureBuilder {

    public static MaterialClosure build(CraftingCalculation calculation, CraftingGraph graph)
        throws InterruptedException {
        var fuzzy = new ObjectOpenHashSet<AEKey>();
        var visited = new ObjectOpenHashSet<AEKey>();
        var pending = new ArrayDeque<>(graph.getSnapshotKeys());
        while (!pending.isEmpty()) {
            calculation.handlePausing();
            var key = pending.removeFirst();
            if (!visited.add(key)) continue;
            var patterns = calculation.getCraftingFor(key);
            for (var pattern : patterns) {
                for (var input : pattern.getInputs()) {
                    if (input.getMultiplier() <= 0) continue;
                    for (var option : input.possibleInputs()) {
                        if (option.amount() <= 0) continue;
                        pending.add(option.what());
                        if (pattern.canSubstitute()) fuzzy.add(option.what());
                    }
                }
            }
            if (patterns.isEmpty() && !calculation.canEmitFor(key)) {
                calculation.getFuzzyCraftable(key, candidate -> {
                    pending.add(candidate);
                    return false;
                });
            }
        }
        var root = graph.getRootNode();
        if (root == null) throw new IllegalStateException("Graph has no root node");
        return new MaterialClosure(root.getWhat(), visited, fuzzy);
    }
}
