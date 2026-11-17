package com.science.gtnl.common.circuit;

import java.util.Objects;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;

@Desugar
public record CircuitTarget(ItemId item, int circuitTier, String familyId) {

    public static final int NANITE_TIER_OFFSET = 7;
    public static final int MAX_CIRCUIT_TIER = 14;

    public static int highestCircuitTier(int naniteTier) {
        return Math.min(MAX_CIRCUIT_TIER, naniteTier + NANITE_TIER_OFFSET);
    }

    public CircuitTarget {
        Objects.requireNonNull(item);
        Objects.requireNonNull(familyId);
        if (circuitTier < 0 || circuitTier > MAX_CIRCUIT_TIER)
            throw new IllegalArgumentException("Invalid circuit tier");
    }

    public int requiredNaniteTier() {
        return Math.max(1, circuitTier - NANITE_TIER_OFFSET);
    }
}
