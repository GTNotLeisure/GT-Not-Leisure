package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipe.GTRecipe_WithAlt;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class CircuitRecipeVariants {

    private CircuitRecipeVariants() {}

    public static List<GTRecipe> splitUnequalAlternatives(GTRecipe recipe) {
        List<GTRecipe> variants = Collections.singletonList(recipe);
        if (!(recipe instanceof GTRecipe_WithAlt withAlt)) return variants;
        if (withAlt.mOreDictAlt != null) {
            for (int index = 0; index < Math.min(withAlt.mOreDictAlt.length, recipe.mInputs.length); index++) {
                ItemStack[] alternatives = withAlt.mOreDictAlt[index];
                if (alternatives == null || alternatives.length < 2 || recipe.mInputs[index] == null) continue;
                int amount = recipe.mInputs[index].stackSize;
                boolean unequal = false;
                for (ItemStack alternative : alternatives)
                    unequal |= alternative != null && alternative.stackSize != amount;
                if (!unequal) continue;
                List<GTRecipe> expanded = new ObjectArrayList<>();
                for (GTRecipe variant : variants) {
                    for (ItemStack alternative : alternatives) {
                        if (alternative == null) continue;
                        GTRecipe_WithAlt copy = (GTRecipe_WithAlt) variant.copy();
                        copy.mInputs[index] = alternative.copy();
                        copy.mOreDictAlt[index] = new ItemStack[] { alternative.copy() };
                        if (copy.mOreDictIds != null && index < copy.mOreDictIds.length) copy.mOreDictIds[index] = -1;
                        expanded.add(copy);
                    }
                }
                variants = expanded;
            }
        }
        if (withAlt.mAltFluidInputs != null) {
            for (int index = 0; index < Math.min(withAlt.mAltFluidInputs.length, recipe.mFluidInputs.length); index++) {
                FluidStack[] alternatives = withAlt.mAltFluidInputs[index];
                if (alternatives == null || alternatives.length < 2 || recipe.mFluidInputs[index] == null) continue;
                int amount = recipe.mFluidInputs[index].amount;
                boolean unequal = false;
                for (FluidStack alternative : alternatives)
                    unequal |= alternative != null && alternative.amount != amount;
                if (!unequal) continue;
                List<GTRecipe> expanded = new ObjectArrayList<>();
                for (GTRecipe variant : variants) {
                    for (FluidStack alternative : alternatives) {
                        if (alternative == null) continue;
                        GTRecipe_WithAlt copy = (GTRecipe_WithAlt) variant.copy();
                        copy.mFluidInputs[index] = alternative.copy();
                        copy.mAltFluidInputs[index] = new FluidStack[] { alternative.copy() };
                        expanded.add(copy);
                    }
                }
                variants = expanded;
            }
        }
        return variants;
    }
}
