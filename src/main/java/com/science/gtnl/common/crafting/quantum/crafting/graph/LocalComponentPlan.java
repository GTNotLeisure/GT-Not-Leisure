package com.science.gtnl.common.crafting.quantum.crafting.graph;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.science.gtnl.common.crafting.quantum.api.crafting.IPatternDetails;
import com.science.gtnl.common.crafting.quantum.api.stacks.KeyCounter;
import com.science.gtnl.common.crafting.quantum.crafting.CraftingCalculation;
import com.science.gtnl.common.crafting.quantum.crafting.inv.CraftingSimulationDiff;
import com.science.gtnl.common.crafting.quantum.crafting.inv.CraftingSimulationState;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

/**
 * Frozen transaction for one recursive component that cannot use the native closed-form solver.
 */
public class LocalComponentPlan {

    public record Entry(int nodeIndex, @Nullable IPatternDetails pattern, long craftTimes) {}

    private final List<Entry> entries;
    private final KeyCounter boundaryDemands;
    private final CraftingSimulationDiff diff;
    private final CraftingCalculation.CalculationDelta delta;
    private boolean committed;

    public LocalComponentPlan(List<Entry> entries, KeyCounter boundaryDemands, CraftingSimulationDiff diff,
        CraftingCalculation.CalculationDelta delta) {
        this.entries = List.copyOf(entries);
        this.boundaryDemands = copy(boundaryDemands);
        this.diff = diff;
        this.delta = copy(delta);
    }

    public List<Entry> entries() {
        return entries;
    }

    public KeyCounter boundaryDemands() {
        return copy(boundaryDemands);
    }

    public void commit(CraftingCalculation calculation, CraftingSimulationState inventory) {
        if (committed) throw new IllegalStateException("Local component plan was already committed");
        var previousMarker = calculation.createCalculationMarker();
        try {
            calculation.mergeCalculationDelta(delta);
            diff.applyTo(inventory);
            committed = true;
        } catch (RuntimeException | Error failure) {
            calculation.restoreCalculationMarker(previousMarker);
            throw failure;
        }
    }

    private static KeyCounter copy(KeyCounter source) {
        var result = KeyCounter.checked();
        result.addAll(source);
        return result;
    }

    private static CraftingCalculation.CalculationDelta copy(CraftingCalculation.CalculationDelta source) {
        return new CraftingCalculation.CalculationDelta(
            copy(source.missingItems()),
            copy(source.recursiveMissingSeeds()),
            new ObjectOpenHashSet<>(source.realSeededRecursiveRequests()),
            new ObjectOpenHashSet<>(source.realRecursiveSeeds()),
            new ObjectOpenHashSet<>(source.realSeededRecursiveKeys()),
            new ObjectOpenHashSet<>(source.recursiveFinalOutputInputs()),
            copy(source.recursiveReserveCandidates()),
            source.intermediateFinalOutputAmount());
    }
}
