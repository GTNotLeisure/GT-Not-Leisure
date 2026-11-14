package com.science.gtnl.common.circuit;

import java.util.List;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;

import com.github.bsideup.jabel.Desugar;
import com.science.gtnl.common.circuit.nanite.NaniteMaintenanceRequirement;

import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class CircuitResourceLedger {

    private final Object2DoubleMap<String> fluidCredit = new Object2DoubleOpenHashMap<>();
    private double energyCredit;
    private long revision;

    public Charge quote(double energy, List<NaniteMaintenanceRequirement> maintenance, double maintenanceSeconds) {
        if (!Double.isFinite(maintenanceSeconds) || maintenanceSeconds < 0)
            throw new ArithmeticException("Invalid maintenance interval");
        long energyDue = charge(energy, energyCredit);
        List<FluidCharge> fluids = new ObjectArrayList<>(maintenance.size());
        Map<String, Double> nextCredits = new Object2ObjectOpenHashMap<>();
        for (NaniteMaintenanceRequirement requirement : maintenance) {
            double required = requirement.milliBucketsPerSecond() * maintenanceSeconds;
            double previous = fluidCredit.getDouble(requirement.fluidName());
            long due = charge(required, previous);
            if (due > 0) fluids.add(new FluidCharge(requirement.fluidName(), due));
            nextCredits.put(requirement.fluidName(), remainder(previous, due, required));
        }
        return new Charge(revision, energyDue, fluids, remainder(energyCredit, energyDue, energy), nextCredits);
    }

    private static long charge(double amount, double credit) {
        if (!Double.isFinite(amount) || amount < 0 || amount >= Long.MAX_VALUE) {
            throw new ArithmeticException("Circuit resource cost exceeds supported capacity");
        }
        return (long) Math.ceil(Math.max(0, amount - credit));
    }

    private static double remainder(double credit, long paid, double used) {
        return Math.max(0, credit + paid - used);
    }

    public void prepayFluid(String fluid, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative circuit fluid payment");
        fluidCredit.put(fluid, fluidCredit.getDouble(fluid) + amount);
        revision++;
    }

    public void commit(Charge charge) {
        if (charge.revision() != revision) throw new IllegalStateException("Stale circuit resource quote");
        energyCredit = charge.energyCredit();
        for (Map.Entry<String, Double> entry : charge.fluidCredits()
            .entrySet())
            fluidCredit.put(
                entry.getKey(),
                entry.getValue()
                    .doubleValue());
        revision++;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setDouble("energy", energyCredit);
        NBTTagCompound fluids = new NBTTagCompound();
        for (Object2DoubleMap.Entry<String> entry : fluidCredit.object2DoubleEntrySet())
            fluids.setDouble(entry.getKey(), entry.getDoubleValue());
        tag.setTag("fluids", fluids);
        return tag;
    }

    public void read(NBTTagCompound tag) {
        energyCredit = validCredit(tag.getDouble("energy"));
        fluidCredit.clear();
        NBTTagCompound fluids = tag.getCompoundTag("fluids");
        for (Object key : fluids.func_150296_c()) {
            String name = (String) key;
            fluidCredit.put(name, validCredit(fluids.getDouble(name)));
        }
        revision++;
    }

    private static double validCredit(double value) {
        return Double.isFinite(value) && value >= 0 && value < Long.MAX_VALUE ? value : 0;
    }

    @Desugar
    public record FluidCharge(String fluidName, long amount) {}

    @Desugar
    public record Charge(long revision, long energy, List<FluidCharge> fluids, double energyCredit,
        Map<String, Double> fluidCredits) {

        public Charge {
            fluids = List.copyOf(fluids);
            fluidCredits = Map.copyOf(fluidCredits);
        }
    }
}
