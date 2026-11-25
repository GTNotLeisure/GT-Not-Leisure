package com.science.gtnl.api;

import net.minecraft.item.ItemStack;

/**
 * Allows an infusion recipe with fixed ingredients, aspects and research to be converted for machines.
 * The output may depend on the central item's data, which must not affect ingredient matching or costs.
 */
public interface IMachineInfusionRecipe {

    ItemStack getRecipeOutput(ItemStack input);
}
