package com.science.gtnl.mixins.late.aspectRecipeIndex;

import java.util.List;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.gtnewhorizons.aspectrecipeindex.nei.InfusionRecipeHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.science.gtnl.compat.nei.WandAugmentationDisplay;
import com.science.gtnl.compat.nei.WandAugmentationDisplayRecipe;

import thaumcraft.api.crafting.InfusionRecipe;

@Mixin(value = InfusionRecipeHandler.class, remap = false)
public abstract class MixinInfusionRecipeHandler {

    @ModifyExpressionValue(
        method = "loadCraftingRecipes(Ljava/lang/String;[Ljava/lang/Object;)V",
        at = @At(value = "INVOKE", target = "Lthaumcraft/api/ThaumcraftApi;getCraftingRecipes()Ljava/util/List;"))
    private List<Object> gtnl$showAllAugmentations(List<Object> registered) {
        return WandAugmentationDisplay.adaptRecipes(registered, null, false);
    }

    @ModifyExpressionValue(
        method = "loadCraftingRecipes(Lnet/minecraft/item/ItemStack;)V",
        at = @At(value = "INVOKE", target = "Lthaumcraft/api/ThaumcraftApi;getCraftingRecipes()Ljava/util/List;"))
    private List<Object> gtnl$showWandAugmentations(List<Object> registered,
        @Local(argsOnly = true) ItemStack queried) {
        return WandAugmentationDisplay.adaptRecipes(registered, queried, true);
    }

    @ModifyExpressionValue(
        method = "getInfusionRecipesByInput",
        at = @At(value = "INVOKE", target = "Lthaumcraft/api/ThaumcraftApi;getCraftingRecipes()Ljava/util/List;"))
    private static List<Object> gtnl$showAugmentationUsages(List<Object> registered,
        @Local(argsOnly = true) ItemStack queried) {
        return WandAugmentationDisplay.adaptRecipes(registered, queried, false);
    }

    @ModifyExpressionValue(
        method = "loadCraftingRecipes(Lnet/minecraft/item/ItemStack;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcodechicken/nei/NEIServerUtils;areStacksSameTypeCraftingWithNBT(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z"))
    private boolean gtnl$matchAugmentedWandView(boolean matches, @Local InfusionRecipe recipe) {
        return matches || recipe instanceof WandAugmentationDisplayRecipe;
    }
}
