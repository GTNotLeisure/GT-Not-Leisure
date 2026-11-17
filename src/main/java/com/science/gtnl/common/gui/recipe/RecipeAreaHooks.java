package com.science.gtnl.common.gui.recipe;

import java.awt.Point;
import java.util.List;

import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.nei.GTNEIDefaultHandler;

public class RecipeAreaHooks {

    private RecipeAreaHooks() {}

    public static void tooltip(RecipeMapFrontend frontend, GuiRecipe<?> gui,
        List<TemplateRecipeHandler.CachedRecipe> recipes, int index, List<String> tooltip) {
        GTNEIDefaultHandler.CachedDefaultRecipe cached = cached(frontend, recipes, index);
        if (cached == null || tooltip == null) return;
        Point mouse = gui.getRecipeMousePosition(index);
        ((IRecipeAreaHandler) frontend).addAreaTooltip(cached.mRecipe, mouse.x, mouse.y, tooltip);
    }

    public static boolean click(RecipeMapFrontend frontend, GuiRecipe<?> gui,
        List<TemplateRecipeHandler.CachedRecipe> recipes, int index, int button) {
        GTNEIDefaultHandler.CachedDefaultRecipe cached = cached(frontend, recipes, index);
        if (cached == null) return false;
        Point mouse = gui.getRecipeMousePosition(index);
        return ((IRecipeAreaHandler) frontend).clickArea(cached.mRecipe, mouse.x, mouse.y, button);
    }

    private static GTNEIDefaultHandler.CachedDefaultRecipe cached(RecipeMapFrontend frontend,
        List<TemplateRecipeHandler.CachedRecipe> recipes, int index) {
        if (!(frontend instanceof IRecipeAreaHandler) || index < 0 || index >= recipes.size()) return null;
        return recipes.get(index) instanceof GTNEIDefaultHandler.CachedDefaultRecipe cached ? cached : null;
    }
}
