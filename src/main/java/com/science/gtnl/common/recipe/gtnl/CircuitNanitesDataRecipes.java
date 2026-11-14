package com.science.gtnl.common.recipe.gtnl;

import com.science.gtnl.api.IRecipePool;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.utils.recipes.RecipeBuilder;
import com.science.gtnl.utils.recipes.data.CircuitNanitesRecipeData;
import com.science.gtnl.utils.recipes.metadata.CircuitNanitesDataMetadata;

import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTOreDictUnificator;

public class CircuitNanitesDataRecipes implements IRecipePool {

    public static volatile Long loadedSeed;

    public RecipeMap<?> CNDR = GTNLRecipeMaps.CircuitNanitesDataRecipes;
    public CircuitNanitesDataMetadata RECIPE_DATA = CircuitNanitesDataMetadata.INSTANCE;
    public long worldSeed;

    public CircuitNanitesDataRecipes(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    public static synchronized void loadForWorld(long worldSeed) {
        if (Long.valueOf(worldSeed)
            .equals(loadedSeed)) return;
        clear();
        new CircuitNanitesDataRecipes(worldSeed).loadRecipes();
        loadedSeed = worldSeed;
    }

    public static synchronized void clear() {
        CircuitNanitesRecipeData.recipeDataMap.clear();
        GTNLRecipeMaps.CircuitNanitesDataRecipes.getBackend()
            .clearRecipes();
        loadedSeed = null;
    }

    @Override
    public void loadRecipes() {
        RecipeBuilder.builder()
            .itemInputs(GTOreDictUnificator.get(OrePrefixes.nanite, Materials.Carbon, 1))
            .metadata(
                RECIPE_DATA,
                CircuitNanitesRecipeData.getOrCreate(
                    GTOreDictUnificator.get(OrePrefixes.nanite, Materials.Carbon, 1),
                    worldSeed,
                    1,
                    0.95,
                    1.05,
                    0.95,
                    1.05,
                    0.08,
                    0.08,
                    8,
                    8,
                    0,
                    0))
            .duration(0)
            .eut(0)
            .addTo(CNDR);
    }
}
