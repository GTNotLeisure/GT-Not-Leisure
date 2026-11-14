package com.science.gtnl.common.circuit;

import java.util.Objects;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;

@Desugar
public record CircuitTarget(ItemId item, int circuitTier, String familyId) {

    public CircuitTarget {
        Objects.requireNonNull(item);
        Objects.requireNonNull(familyId);
        if (circuitTier < 0 || circuitTier > 14) throw new IllegalArgumentException("Invalid circuit tier");
    }

    public int requiredNaniteTier() {
        return Math.max(1, circuitTier - 7);
    }
}
