package com.science.gtnl.utils.recipes.metadata;

import javax.annotation.ParametersAreNonnullByDefault;

import org.jetbrains.annotations.Nullable;

import com.science.gtnl.utils.recipes.data.CircuitNanitesRecipeData;

import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.util.MethodsReturnNonnullByDefault;
import gregtech.nei.RecipeDisplayInfo;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CircuitNanitesDataMetadata extends RecipeMetadataKey<CircuitNanitesRecipeData> {

    public static final CircuitNanitesDataMetadata INSTANCE = new CircuitNanitesDataMetadata();

    public CircuitNanitesDataMetadata() {
        super(CircuitNanitesRecipeData.class, "circuit_nanites_data");
    }

    @Override
    public void drawInfo(RecipeDisplayInfo recipeInfo, @Nullable Object value) {}
}
