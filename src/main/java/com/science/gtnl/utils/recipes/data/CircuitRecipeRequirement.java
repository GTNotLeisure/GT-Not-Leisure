package com.science.gtnl.utils.recipes.data;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record CircuitRecipeRequirement(String familyId, int circuitTier, int requiredNaniteTier,
    long baseComputation) {}
