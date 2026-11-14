package com.science.gtnl.common.circuit.nanite;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record NaniteParticipation(int slot, int tier, int parallelContribution) {

    public NaniteParticipation {
        if (slot < 0 || slot >= 4 || tier < 1 || tier > 10 || parallelContribution <= 0) {
            throw new IllegalArgumentException("Invalid participating nanite");
        }
    }
}
