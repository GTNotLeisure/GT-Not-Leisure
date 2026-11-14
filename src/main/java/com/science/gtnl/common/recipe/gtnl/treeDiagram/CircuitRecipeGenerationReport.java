package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CircuitRecipeGenerationReport(int recipes, int targets, int skipped, int itemInputs, int itemOutputs,
    int fluidInputs, int fluidOutputs, long maximumComputation) {}
