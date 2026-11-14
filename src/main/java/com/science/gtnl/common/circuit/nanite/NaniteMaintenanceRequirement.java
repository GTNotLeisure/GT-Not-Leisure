package com.science.gtnl.common.circuit.nanite;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record NaniteMaintenanceRequirement(String fluidName, long milliBucketsPerSecond) {

    public NaniteMaintenanceRequirement {
        if (fluidName == null || fluidName.isEmpty() || milliBucketsPerSecond <= 0) {
            throw new IllegalArgumentException("Invalid nanite maintenance requirement");
        }
    }
}
