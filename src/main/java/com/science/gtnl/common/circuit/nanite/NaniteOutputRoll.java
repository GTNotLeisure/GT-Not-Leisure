package com.science.gtnl.common.circuit.nanite;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record NaniteOutputRoll(int parallels, int failedParallels, int bonusParallels, double bonusFraction) {

    public NaniteOutputRoll {
        if (parallels < 1 || failedParallels < 0
            || failedParallels > parallels
            || bonusParallels < 0
            || bonusParallels > parallels
            || !Double.isFinite(bonusFraction)
            || bonusFraction < 0
            || bonusFraction > 1) {
            throw new IllegalArgumentException("Invalid nanite output roll");
        }
    }

    public long apply(long amount) {
        return Math.addExact(
            amount - Math.round(amount * (failedParallels / (4.0 * parallels))),
            Math.round(amount * (bonusParallels / (double) parallels) * bonusFraction));
    }
}
