package com.science.gtnl.utils.recipes;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.metadata.SimpleRecipeMetadataKey;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class RecipeItemTransformer {

    public static final RecipeMetadataKey<RecipeItemTransformer> METADATA_KEY = SimpleRecipeMetadataKey
        .create(RecipeItemTransformer.class, "gtnl_item_transformer");

    private final ItemStack input;
    private final UnaryOperator<ItemStack> outputFactory;

    public RecipeItemTransformer(ItemStack input, UnaryOperator<ItemStack> outputFactory) {
        this.input = Objects.requireNonNull(input)
            .copy();
        this.outputFactory = Objects.requireNonNull(outputFactory);
    }

    @Nullable
    public Binding bind(GTRecipe recipe, ItemStack[] inputs, Predicate<ItemStack[]> acceptsInputs) {
        for (ItemStack candidate : inputs) {
            if (candidate == null || candidate.stackSize <= 0 || !matchesInput(candidate)) continue;

            ItemStack[] groupedInputs = collectInputs(inputs, candidate);
            if (!acceptsInputs.test(groupedInputs)) continue;

            ItemStack central = candidate.copy();
            central.stackSize = 1;
            ItemStack output = outputFactory.apply(central);
            if (output == null || output.getItem() == null || output.stackSize <= 0) continue;

            GTRecipe boundRecipe = recipe.copy();
            boundRecipe.mOutputs[0] = output.copy();
            return new Binding(boundRecipe, groupedInputs);
        }
        return null;
    }

    private ItemStack[] collectInputs(ItemStack[] inputs, ItemStack central) {
        List<ItemStack> grouped = new ObjectArrayList<>(inputs.length);
        for (ItemStack stack : inputs) {
            if (stack == null) continue;
            if (!matchesInput(stack) || GTUtility.areStacksEqual(stack, central, false)) grouped.add(stack);
        }
        return grouped.toArray(new ItemStack[0]);
    }

    private boolean matchesInput(ItemStack stack) {
        return GTUtility.areStacksEqual(input, stack, true);
    }

    public record Binding(GTRecipe recipe, ItemStack[] inputs) {}
}
