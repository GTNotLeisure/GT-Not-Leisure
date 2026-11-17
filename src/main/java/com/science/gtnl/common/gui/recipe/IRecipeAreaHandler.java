package com.science.gtnl.common.gui.recipe;

import java.util.List;

import gregtech.api.util.GTRecipe;

public interface IRecipeAreaHandler {

    /**
     * Adds tooltip lines for the point under the mouse on one NEI recipe. Coordinates are relative to the recipe, the
     * same space GT uses for description text. Frontends must account for the slot template offset when placing areas.
     */
    void addAreaTooltip(GTRecipe recipe, int mouseX, int mouseY, List<String> tooltip);

    /**
     * Handles a mouse click on one NEI recipe, in the same coordinates as {@link #addAreaTooltip}. Returns true when
     * the click was used, so NEI skips its own handling.
     */
    boolean clickArea(GTRecipe recipe, int mouseX, int mouseY, int button);
}
