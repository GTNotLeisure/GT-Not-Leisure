package com.science.gtnl.utils.recipes.metadata;

import com.science.gtnl.utils.recipes.data.CircuitRecipeRequirement;

import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.nei.RecipeDisplayInfo;

public class TreeDiagramCircuitMetadata extends RecipeMetadataKey<CircuitRecipeRequirement> {

    public static final TreeDiagramCircuitMetadata INSTANCE = new TreeDiagramCircuitMetadata();

    public TreeDiagramCircuitMetadata() {
        super(CircuitRecipeRequirement.class, "tree_diagram_circuit");
    }

    @Override
    public void drawInfo(RecipeDisplayInfo recipeInfo, Object value) {}
}
