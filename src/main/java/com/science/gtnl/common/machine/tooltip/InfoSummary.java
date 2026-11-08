package com.science.gtnl.common.machine.tooltip;

import java.util.List;
import java.util.OptionalInt;

import com.science.gtnl.api.IMultiblockTooltipRules.HeatRules;
import com.science.gtnl.api.IMultiblockTooltipRules.TieredPart;
import com.science.gtnl.api.IMultiblockTooltipRules.TooltipStats;

public record InfoSummary(String machineType, TooltipStats stats, List<TierBonus> tierBonuses, List<String> lines,
    List<String> features, HeatRules heat, List<TierThreshold> perfectOverclockFrom, OptionalInt tierSkips,
    int pollution, List<List<String>> groups, boolean supportAny, boolean supportMultiAmp) {

    public InfoSummary {
        tierBonuses = List.copyOf(tierBonuses);
        lines = List.copyOf(lines);
        features = List.copyOf(features);
        perfectOverclockFrom = List.copyOf(perfectOverclockFrom);
        groups = groups.stream()
            .map(List::copyOf)
            .toList();
    }

    public enum BonusStat {
        PARALLEL,
        SPEED,
        DURATION,
        ENERGY
    }

    public record TierBonus(TieredPart part, BonusStat stat, boolean multiplicative, double value, boolean perTier) {}

    public record TierThreshold(TieredPart part, int tier, String tierName) {}
}
