package com.science.gtnl.common.circuit.computation;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record ComputationEffects(long availableCu, long requiredCu, double supplyRatio, double speedMultiplier,
    double euMultiplier) {}
