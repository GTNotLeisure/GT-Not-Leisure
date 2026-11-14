package com.science.gtnl.common.circuit.computation;

public class CircuitComputationPolicy {

    private static final double MINIMUM_SPEED = 1.0 / 64;
    private static final double UNDER_SUPPLY_PENALTY = 3;
    private static final double MAX_WORK_ENERGY_DISCOUNT = 0.10;

    public long recipeRequirement(int tier, int itemTypes, int fluidTypes, int processNodes) {
        if (tier < 0 || tier > 14 || itemTypes < 0 || fluidTypes < 0 || processNodes < 0) {
            throw new IllegalArgumentException("Invalid circuit complexity");
        }
        long complexity = Math.addExact(
            100L,
            Math.addExact(4L * Math.max(0, itemTypes - 1), Math.addExact(8L * fluidTypes, 2L * processNodes)));
        long numerator = Math.multiplyExact(32L * (tier + 1) * (tier + 1), complexity);
        return Math.multiplyExact(Math.addExact(numerator, 1599) / 1600, 16);
    }

    public long required(long recipeCu, int parallels, double baseWorkRate) {
        if (recipeCu <= 0 || parallels <= 0 || !Double.isFinite(baseWorkRate) || baseWorkRate <= 0) {
            throw new IllegalArgumentException("Invalid circuit workload");
        }
        double required = Math.multiplyExact(recipeCu, (long) parallels) * baseWorkRate;
        if (!Double.isFinite(required) || required >= Long.MAX_VALUE) {
            throw new ArithmeticException("Circuit computation demand exceeds supported capacity");
        }
        return Math.max(1, (long) Math.ceil(required));
    }

    public ComputationEffects evaluate(long available, long required) {
        if (required <= 0) throw new IllegalArgumentException("Computation requirement must be positive");
        available = Math.max(0, available);
        double ratio = available / (double) required;
        double speed;
        double energy;
        if (ratio < 1) {
            speed = Math.max(MINIMUM_SPEED, ratio * ratio);
            energy = 1 + UNDER_SUPPLY_PENALTY * (1 - ratio) * (1 - ratio);
        } else {
            speed = ratio <= 2 ? ratio : 2 + Math.log(ratio - 1);
            double log = Math.log(ratio);
            energy = speed * (1 - MAX_WORK_ENERGY_DISCOUNT * log / (1 + log));
        }
        return new ComputationEffects(available, required, ratio, speed, energy);
    }
}
