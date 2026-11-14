package com.science.gtnl.common.gui.recipe;

import java.util.Collections;
import java.util.List;

import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.science.gtnl.mixins.early.gregtech.AccessorRecipeDisplayInfo;

import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.nei.RecipeDisplayInfo;

public class CircuitNanitesDataFrontend extends GTNLRecipeFrontend {

    public CircuitNanitesDataFrontend(BasicUIPropertiesBuilder ui, NEIRecipePropertiesBuilder nei) {
        super(ui, nei);
    }

    @Override
    public List<Pos2d> getItemInputPositions(int itemInputCount) {
        return Collections.singletonList(position(9, 13));
    }

    @Override
    public void drawDescription(RecipeDisplayInfo recipeInfo) {
        ((AccessorRecipeDisplayInfo) recipeInfo).setYPos(13);
        drawMetadataInfo(recipeInfo);
    }
}
