package com.science.gtnl.utils.recipes;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import gregtech.api.recipe.check.SingleRecipeCheck;
import gregtech.api.util.GTRecipe;

public class TransactionalProcessingLogic extends GTNLProcessingLogic {

    @Override
    public CalculationResult validateAndCalculateRecipe(@NotNull GTRecipe recipe) {
        ItemStack[] items = inputItems;
        FluidStack[] fluids = inputFluids;
        int[] itemAmounts = new int[items.length];
        int[] fluidAmounts = new int[fluids.length];
        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) itemAmounts[i] = items[i].stackSize;
        }
        for (int i = 0; i < fluids.length; i++) {
            if (fluids[i] != null) fluidAmounts[i] = fluids[i].amount;
        }
        SingleRecipeCheck previousLock = recipeLockableMachine == null ? null
            : recipeLockableMachine.getSingleRecipeCheck();
        boolean committed = false;
        try {
            CalculationResult result = super.validateAndCalculateRecipe(recipe);
            committed = result.checkRecipeResult.wasSuccessful();
            return committed ? result : CalculationResult.ofFailure(result.checkRecipeResult);
        } finally {
            if (!committed) {
                for (int i = 0; i < items.length; i++) {
                    if (items[i] != null) items[i].stackSize = itemAmounts[i];
                }
                for (int i = 0; i < fluids.length; i++) {
                    if (fluids[i] != null) fluids[i].amount = fluidAmounts[i];
                }
                if (recipeLockableMachine != null) recipeLockableMachine.setSingleRecipeCheck(previousLock);
            }
        }
    }
}
