package com.science.gtnl.common.circuit.nanite;

import java.util.Random;

public class NaniteOutputSampler {

    public NaniteOutputRoll sample(Random random, int parallels, NaniteEffects effects) {
        return new NaniteOutputRoll(
            parallels,
            binomial(random, parallels, effects.lossChance()),
            binomial(
                random,
                parallels,
                effects.main()
                    .bonusChance()),
            effects.main()
                .bonusFraction());
    }

    public int binomial(Random random, int trials, double chance) {
        if (trials <= 0 || chance <= 0) return 0;
        if (chance >= 1) return trials;
        if (chance > 0.5) return trials - binomial(random, trials, 1 - chance);
        if (trials <= 64) {
            int successes = 0;
            for (int trial = 0; trial < trials; trial++) if (random.nextDouble() < chance) successes++;
            return successes;
        }
        double mean = trials * chance;
        if (mean < 16) {
            double logFailure = Math.log1p(-chance);
            long position = -1;
            int successes = 0;
            while (true) {
                double gap = Math.floor(Math.log1p(-random.nextDouble()) / logFailure) + 1;
                if (gap > trials - position) break;
                position += (long) gap;
                if (position >= trials) break;
                successes++;
            }
            return successes;
        }
        // The normal approximation is confined to the dense, large-sample region.
        return (int) Math
            .max(0, Math.min(trials, Math.round(mean + Math.sqrt(mean * (1 - chance)) * random.nextGaussian())));
    }
}
