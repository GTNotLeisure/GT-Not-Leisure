package com.science.gtnl.utils.machine.crop;

import com.gtnewhorizon.cropsnh.api.ICropCard;
import com.science.gtnl.config.MachineConfig;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public final class CropBreedingChance {

    private static final int[] DEFAULT_TIER_CHANCES = { 100, 80, 60, 40, 20 };

    private static volatile Overrides overrides = new Overrides(null, Object2IntMaps.emptyMap());

    private CropBreedingChance() {}

    public static int percent(ICropCard crop) {
        if (crop == null) return 0;
        int[] chances = MachineConfig.crop_breeder.tierChances;
        if (chances == null || chances.length == 0) chances = DEFAULT_TIER_CHANCES;
        int band = Math.min(
            Math.min(chances.length, DEFAULT_TIER_CHANCES.length) - 1,
            CropBreedingRecipeRules.breedingGrade(crop) - 1);
        Object2IntMap<String> overrides = overrides();
        return overrides.containsKey(crop.getId()) ? overrides.getInt(crop.getId())
            : Math.max(0, Math.min(100, chances[band]));
    }

    public static int washIncreaseChance() {
        return Math.max(0, Math.min(100, MachineConfig.crop_breeder.washIncreaseChance));
    }

    public static int washDecreaseChance() {
        return Math.max(0, Math.min(100 - washIncreaseChance(), MachineConfig.crop_breeder.washDecreaseChance));
    }

    public static int washUnchangedChance() {
        return 100 - washIncreaseChance() - washDecreaseChance();
    }

    public static int rollWashDelta(int roll) {
        int increase = washIncreaseChance();
        if (roll < increase) return 1;
        return roll < increase + washDecreaseChance() ? -1 : 0;
    }

    private static Object2IntMap<String> overrides() {
        Overrides current = overrides;
        String[] source = MachineConfig.crop_breeder.cropOverrides;
        if (current.source == source) return current.values;
        Object2IntOpenHashMap<String> result = new Object2IntOpenHashMap<>();
        if (source != null) for (String entry : source) {
            if (entry == null) continue;
            int separator = entry.lastIndexOf('=');
            if (separator < 0) continue;
            try {
                int override = Integer.parseInt(
                    entry.substring(separator + 1)
                        .trim());
                if (override >= 0 && override <= 100) result.put(
                    entry.substring(0, separator)
                        .trim(),
                    override);
            } catch (NumberFormatException ignored) {}
        }
        overrides = new Overrides(source, result);
        return result;
    }

    private record Overrides(String[] source, Object2IntMap<String> values) {}
}
