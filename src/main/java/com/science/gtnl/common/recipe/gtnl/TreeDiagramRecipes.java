package com.science.gtnl.common.recipe.gtnl;

import java.util.Arrays;

import com.science.gtnl.api.IRecipePool;
import com.science.gtnl.common.circuit.CircuitCatalog;
import com.science.gtnl.common.circuit.computation.CircuitComputationPolicy;
import com.science.gtnl.common.gui.recipe.TreeDiagramRecipeFrontend;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.common.recipe.gtnl.treeDiagram.CircuitIngredientNormalizer;
import com.science.gtnl.common.recipe.gtnl.treeDiagram.CircuitRecipeGenerationReport;
import com.science.gtnl.common.recipe.gtnl.treeDiagram.NacCircuitRecipeSource;
import com.science.gtnl.common.recipe.gtnl.treeDiagram.RecipeMapCircuitRecipeSource;
import com.science.gtnl.common.recipe.gtnl.treeDiagram.TreeDiagramRecipeGenerator;

import bartworks.API.recipe.BartWorksRecipeMaps;
import gregtech.api.recipe.RecipeMaps;

public class TreeDiagramRecipes implements IRecipePool {

    private static CircuitRecipeGenerationReport generationReport;

    @Override
    public void loadRecipes() {
        if (generationReport != null) return;
        CircuitComputationPolicy computation = new CircuitComputationPolicy();
        TreeDiagramRecipeGenerator generator = new TreeDiagramRecipeGenerator(
            Arrays.asList(
                new NacCircuitRecipeSource(),
                new RecipeMapCircuitRecipeSource("cal", BartWorksRecipeMaps.circuitAssemblyLineRecipes, 20),
                new RecipeMapCircuitRecipeSource("ca", RecipeMaps.circuitAssemblerRecipes)),
            CircuitCatalog.INSTANCE,
            new CircuitIngredientNormalizer(computation));
        generationReport = generator.generate(GTNLRecipeMaps.TreeDiagramRecipes);
        ((TreeDiagramRecipeFrontend) GTNLRecipeMaps.TreeDiagramRecipes.getFrontend()).configure(generationReport);
    }

    public static CircuitRecipeGenerationReport generationReport() {
        return generationReport;
    }
}
