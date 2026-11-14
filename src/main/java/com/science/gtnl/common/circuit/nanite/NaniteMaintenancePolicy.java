package com.science.gtnl.common.circuit.nanite;

import java.util.List;

import gregtech.api.enums.Materials;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class NaniteMaintenancePolicy {

    private final List<String> fluids;

    public NaniteMaintenancePolicy() {
        this(
            List.of(
                Materials.Grade4PurifiedWater.getFluid(1)
                    .getFluid()
                    .getName(),
                Materials.Grade6PurifiedWater.getFluid(1)
                    .getFluid()
                    .getName(),
                Materials.Grade8PurifiedWater.getFluid(1)
                    .getFluid()
                    .getName()));
    }

    public NaniteMaintenancePolicy(List<String> fluids) {
        if (fluids.size() != 3 || fluids.stream()
            .distinct()
            .count() != 3) {
            throw new IllegalArgumentException("Maintenance requires three distinct fluids");
        }
        this.fluids = List.copyOf(fluids);
    }

    public List<NaniteMaintenanceRequirement> requirements(NaniteEffects effects) {
        long[] amounts = new long[3];
        for (NaniteParticipation participant : effects.participants()) {
            for (int stage = 0; stage < 3; stage++) {
                int exponent = participant.tier() - 1 - 3 * stage;
                if (exponent >= 0) amounts[stage] += 8L << exponent;
            }
        }
        List<NaniteMaintenanceRequirement> result = new ObjectArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            if (amounts[index] > 0) result.add(new NaniteMaintenanceRequirement(fluids.get(index), amounts[index]));
        }
        return List.copyOf(result);
    }

    public static double lossCancellation(int tier) {
        return Math.min(3, 1 + (tier - 1) / 3) * 0.08;
    }
}
