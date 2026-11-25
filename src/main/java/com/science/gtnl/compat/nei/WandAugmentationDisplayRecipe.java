package com.science.gtnl.compat.nei;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import thaumcraft.api.crafting.InfusionRecipe;

@SideOnly(Side.CLIENT)
public class WandAugmentationDisplayRecipe extends InfusionRecipe {

    public WandAugmentationDisplayRecipe(InfusionRecipe source, ItemStack input) {
        super(
            source.getResearch(),
            source.getRecipeOutput(input),
            source.getInstability(),
            source.getAspects()
                .copy(),
            input.copy(),
            copyComponents(source.getComponents()));
    }

    public static ItemStack[] copyComponents(ItemStack[] components) {
        ItemStack[] copy = new ItemStack[components.length];
        for (int i = 0; i < components.length; i++) copy[i] = components[i].copy();
        return copy;
    }
}
