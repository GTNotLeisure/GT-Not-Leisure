package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import java.util.Collection;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.science.gtnl.common.circuit.CircuitTarget;

import gregtech.api.util.GTRecipe;

public interface CircuitRecipeSource {

    public String id();

    public Collection<GTRecipe> recipes();

    public ItemStack primaryOutput(GTRecipe recipe);

    public List<CircuitRecipeDraft> expand(GTRecipe recipe);

    public default int priority(CircuitTarget target) {
        return 0;
    }
}
