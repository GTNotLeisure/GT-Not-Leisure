package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import com.science.gtnl.common.circuit.CircuitTarget;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;

public class RecipeMapCircuitRecipeSource implements CircuitRecipeSource {

    private final String id;
    private final RecipeMap<?> recipeMap;
    private final int priority;

    public RecipeMapCircuitRecipeSource(String id, RecipeMap<?> recipeMap) {
        this(id, recipeMap, 10);
    }

    public RecipeMapCircuitRecipeSource(String id, RecipeMap<?> recipeMap, int priority) {
        this.id = id;
        this.recipeMap = recipeMap;
        this.priority = priority;
    }

    @Override
    public int priority(CircuitTarget target) {
        return priority;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Collection<GTRecipe> recipes() {
        return recipeMap.getAllRecipes();
    }

    @Override
    public ItemStack primaryOutput(GTRecipe recipe) {
        return recipe.mOutputs.length == 0 ? null : recipe.mOutputs[0];
    }

    @Override
    public List<CircuitRecipeDraft> expand(GTRecipe recipe) {
        ItemStack output = primaryOutput(recipe);
        if (output == null || output.stackSize <= 0 || 64 % output.stackSize != 0) {
            throw new IllegalArgumentException("Circuit output cannot be normalized to 64");
        }
        if (recipe.getOutputChance(0) != 10000) {
            throw new IllegalArgumentException("Circuit primary output must be guaranteed");
        }
        long runs = 64 / output.stackSize;
        CircuitRecipeDraft draft = new CircuitRecipeDraft();
        for (CircuitItemInput input : CircuitRecipeDraft.itemInputs(recipe, runs)) draft.addItem(input);
        draft.addRecipeFluids(recipe, runs);
        draft.addRecipeCost(recipe, runs);
        draft.setProcessNodes(1);
        for (int index = 0; index < recipe.mOutputs.length; index++) {
            ItemStack stack = recipe.mOutputs[index];
            if (stack != null)
                draft.addOutput(stack, Math.multiplyExact(stack.stackSize, runs), recipe.getOutputChance(index));
        }
        for (int index = 0; index < recipe.mFluidOutputs.length; index++) {
            if (recipe.mFluidOutputs[index] != null) draft.addFluidOutput(
                recipe.mFluidOutputs[index],
                Math.multiplyExact(recipe.mFluidOutputs[index].amount, runs),
                recipe.getFluidOutputChance(index));
        }
        return Collections.singletonList(draft);
    }
}
