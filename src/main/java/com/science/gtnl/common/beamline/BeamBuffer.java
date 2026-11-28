package com.science.gtnl.common.beamline;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.jetbrains.annotations.Nullable;

import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.Particle;

public class BeamBuffer {

    private final NavigableMap<Integer, StoredBeam> beams = new TreeMap<>();
    private long particleCount;
    private int nextParticleId;

    public boolean add(@Nullable BeamInformation beam) {
        return beam != null && add(beam.getParticleId(), beam.getRate(), beam.getEnergy(), beam.getFocus());
    }

    private boolean add(int particleId, long amount, double energy, double focus) {
        if (particleId < 0 || particleId >= Particle.VALUES.length
            || amount <= 0
            || amount > Long.MAX_VALUE - particleCount
            || !Double.isFinite(energy)
            || energy < 0
            || energy > Float.MAX_VALUE
            || !Double.isFinite(focus)
            || focus < 0
            || focus > Float.MAX_VALUE) {
            return false;
        }

        StoredBeam stored = beams.get(particleId);
        if (stored == null) {
            beams.put(particleId, new StoredBeam(amount, energy, focus));
        } else {
            long combinedAmount = stored.amount() + amount;
            double inputWeight = (double) amount / combinedAmount;
            beams.put(
                particleId,
                new StoredBeam(
                    combinedAmount,
                    stored.energy() + (energy - stored.energy()) * inputWeight,
                    stored.focus() + (focus - stored.focus()) * inputWeight));
        }
        particleCount += amount;
        return true;
    }

    public @Nullable BeamInformation poll() {
        if (beams.isEmpty()) return null;
        Map.Entry<Integer, StoredBeam> entry = beams.ceilingEntry(nextParticleId);
        if (entry == null) entry = beams.firstEntry();
        int particleId = entry.getKey();
        StoredBeam stored = entry.getValue();
        int amount = (int) Math.min(Integer.MAX_VALUE, stored.amount());
        if (stored.amount() == amount) {
            beams.remove(particleId);
        } else {
            beams.put(particleId, new StoredBeam(stored.amount() - amount, stored.energy(), stored.focus()));
        }
        particleCount -= amount;
        nextParticleId = particleId + 1;
        return new BeamInformation((float) stored.energy(), amount, particleId, (float) stored.focus());
    }

    public int getParticleTypeCount() {
        return beams.size();
    }

    public long getParticleCount() {
        return particleCount;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList entries = new NBTTagList();
        for (Map.Entry<Integer, StoredBeam> entry : beams.entrySet()) {
            StoredBeam stored = entry.getValue();
            NBTTagCompound beamTag = new NBTTagCompound();
            beamTag.setInteger("ParticleId", entry.getKey());
            beamTag.setLong("Amount", stored.amount());
            beamTag.setDouble("Energy", stored.energy());
            beamTag.setDouble("Focus", stored.focus());
            entries.appendTag(beamTag);
        }
        tag.setTag("Beams", entries);
        tag.setInteger("NextParticleId", nextParticleId);
        return tag;
    }

    public void readFromNBT(NBTTagCompound tag) {
        beams.clear();
        particleCount = 0;
        NBTTagList entries = tag.getTagList("Beams", 10);
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound beamTag = entries.getCompoundTagAt(index);
            add(
                beamTag.getInteger("ParticleId"),
                beamTag.getLong("Amount"),
                beamTag.getDouble("Energy"),
                beamTag.getDouble("Focus"));
        }
        nextParticleId = Math.max(0, Math.min(Particle.VALUES.length, tag.getInteger("NextParticleId")));
    }

    private record StoredBeam(long amount, double energy, double focus) {}
}
