package com.science.gtnl.common.recipe.gtnl;

import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.science.gtnl.api.IRecipePool;
import com.science.gtnl.common.circuit.nanite.CircuitNaniteRegistry;
import com.science.gtnl.common.circuit.nanite.NaniteProfile;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.utils.recipes.RecipeBuilder;
import com.science.gtnl.utils.recipes.data.CircuitNanitesRecipeData;
import com.science.gtnl.utils.recipes.metadata.CircuitNanitesDataMetadata;

import gregtech.api.recipe.RecipeMap;

public class CircuitNanitesDataRecipes implements IRecipePool {

    public static volatile Long loadedSeed;
    public static volatile CircuitNaniteRegistry loadedRegistry;

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
        loadedRegistry = null;
    }

    @Override
    public void loadRecipes() {
        CircuitNaniteRegistry registry = new CircuitNaniteRegistry(worldSeed);
        for (NaniteProfile profile : registry.standardProfiles()) {
            CircuitNanitesRecipeData data = rated(profile);
            CircuitNanitesRecipeData.recipeDataMap.put(data.stack, data);
            add(profile.item(), data);
        }
        for (ItemId item : registry.unratedCandidates()) {
            CircuitNanitesRecipeData data = new CircuitNanitesRecipeData();
            data.stack = item;
            data.worldSeed = worldSeed;
            data.nantiesTier = 0;
            add(item, data);
        }
        loadedRegistry = registry;
    }

    private CircuitNanitesRecipeData rated(NaniteProfile profile) {
        int index = profile.tier() - 1;
        double low = 1 - CircuitNaniteRegistry.ROLL_SPREAD, high = 1 + CircuitNaniteRegistry.ROLL_SPREAD;
        CircuitNanitesRecipeData data = new CircuitNanitesRecipeData();
        data.stack = profile.item();
        data.worldSeed = worldSeed;
        data.nantiesTier = profile.tier();
        data.parallelCount = data.parallelCountMin = data.parallelCountMax = profile.parallel();
        data.speedBoost = profile.durationMultiplier();
        data.speedBoostMin = CircuitNaniteRegistry.DURATION[index] * low;
        data.speedBoostMax = CircuitNaniteRegistry.DURATION[index] * high;
        data.euModifier = profile.euMultiplier();
        data.euModifierMin = CircuitNaniteRegistry.ENERGY[index] * low;
        data.euModifierMax = CircuitNaniteRegistry.ENERGY[index] * high;
        data.failedChance = data.failedChanceMin = data.failedChanceMax = profile.lossChance();
        data.outputMultiplier = data.outputMultiplierMin = data.outputMultiplierMax = profile.bonusChance();
        return data;
    }

    private void add(ItemId item, CircuitNanitesRecipeData data) {
        RecipeBuilder.builder()
            .itemInputs(item.toStack(1))
            .metadata(RECIPE_DATA, data)
            .duration(0)
            .eut(0)
            .addTo(CNDR);
    }
}
