package com.science.gtnl.common.recipe.thaumcraft;

import net.minecraft.item.ItemStack;

import com.science.gtnl.common.item.wand.MajoWandStage;
import com.science.gtnl.loader.ItemLoader;

import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;

public class MajoWandUpgradeRecipe extends InfusionRecipe {

    private final MajoWandStage target;

    public MajoWandUpgradeRecipe(MajoWandStage target, int instability, AspectList aspects, ItemStack[] components) {
        super(
            target.getResearch(),
            ItemLoader.majoWand.createStack(target),
            instability,
            aspects,
            new ItemStack(ItemLoader.majoWand, 1, target.ordinal() - 1),
            components);
        this.target = target;
    }

    @Override
    public ItemStack getRecipeOutput(ItemStack input) {
        return ItemLoader.majoWand.upgradeStack(input, target);
    }
}
