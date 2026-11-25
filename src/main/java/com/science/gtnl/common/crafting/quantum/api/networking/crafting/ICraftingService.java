package com.science.gtnl.common.crafting.quantum.api.networking.crafting;

import java.util.Collection;
import java.util.Comparator;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.science.gtnl.common.crafting.quantum.api.crafting.IPatternDetails;
import com.science.gtnl.common.crafting.quantum.api.stacks.AEKey;
import com.science.gtnl.common.crafting.quantum.crafting.graph.CraftingGraph;

public interface ICraftingService {

    /** Supplies the execution platform's task order; an unspecified order preserves the replay's stable order. */
    public default Comparator<IPatternDetails> getCpuTaskComparator() {
        return (first, second) -> 0;
    }

    public Collection<IPatternDetails> getCraftingFor(AEKey whatToCraft);

    public @Nullable AEKey getFuzzyCraftable(AEKey whatToCraft, Predicate<AEKey> filter);

    public boolean canEmitFor(AEKey what);

    public default long getRecursiveIngredientReserveAmount() {
        return 1;
    }

    public default long getGraphCacheRevision() {
        return Long.MIN_VALUE;
    }

    public @Nullable default CraftingGraph takeCachedGraph(AEKey output) {
        return null;
    }

    public @Nullable default CraftingGraph peekCachedGraph(AEKey output) {
        return null;
    }

    public default void putCachedGraph(AEKey output, CraftingGraph graph) {}
}
