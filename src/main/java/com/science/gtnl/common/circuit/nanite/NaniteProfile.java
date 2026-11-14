package com.science.gtnl.common.circuit.nanite;

import java.util.Objects;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;

@Desugar
public record NaniteProfile(String id, ItemId item, int tier, NaniteBranch branch, int parallel, double euMultiplier,
    double durationMultiplier, double lossChance, double bonusChance, double bonusFraction) {

    public NaniteProfile {
        Objects.requireNonNull(id);
        Objects.requireNonNull(item);
        Objects.requireNonNull(branch);
        if (tier < 1 || tier > 10
            || parallel < 1
            || !Double.isFinite(euMultiplier)
            || euMultiplier <= 0
            || !Double.isFinite(durationMultiplier)
            || durationMultiplier <= 0
            || !probability(lossChance)
            || !probability(bonusChance)
            || !probability(bonusFraction)) {
            throw new IllegalArgumentException("Invalid nanite profile: " + id);
        }
    }

    private static boolean probability(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
