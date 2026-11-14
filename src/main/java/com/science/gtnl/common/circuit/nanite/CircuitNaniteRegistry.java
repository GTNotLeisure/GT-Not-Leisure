package com.science.gtnl.common.circuit.nanite;

import java.util.Map;
import java.util.Random;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.science.gtnl.common.circuit.CircuitIdentity;
import com.science.gtnl.utils.recipes.data.CircuitNanitesRecipeData;

import goodgenerator.items.GGMaterial;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class CircuitNaniteRegistry {

    private static final int[] PARALLELS = { 8, 16, 32, 64, 96, 128, 192, 256, 384, 512 };
    private static final double[] ENERGY = { 1, 0.97, 0.94, 0.90, 0.86, 0.82, 0.78, 0.74, 0.70, 0.66 };
    private static final double[] DURATION = { 1, 0.90, 0.82, 0.75, 0.68, 0.62, 0.57, 0.52, 0.48, 0.44 };

    private final Map<ItemId, NaniteProfile> profiles = new Object2ObjectOpenHashMap<>();
    private final Map<ItemId, String> candidates = new Object2ObjectOpenHashMap<>();
    private final long worldSeed;

    public CircuitNaniteRegistry(long worldSeed) {
        this.worldSeed = worldSeed;
        registerStandard(Materials.Carbon, 1, NaniteBranch.STABLE);
        registerStandard(Materials.Glowstone, 1, NaniteBranch.GAMBLING);
        registerStandard(Materials.Silver, 2, NaniteBranch.STABLE);
        registerStandard(Materials.Neutronium, 2, NaniteBranch.GAMBLING);
        registerStandard(Materials.Gold, 3, NaniteBranch.GAMBLING);
        registerStandard(Materials.TranscendentMetal, 4, NaniteBranch.STABLE);
        registerStandard(Materials.SixPhasedCopper, 5, NaniteBranch.GAMBLING);
        registerStandard(Materials.WhiteDwarfMatter, 6, NaniteBranch.STABLE);
        registerStandard(Materials.BlackDwarfMatter, 7, NaniteBranch.GAMBLING);
        registerStandard(Materials.Universium, 8, NaniteBranch.STABLE);
        registerStandard(Materials.Eternity, 9, NaniteBranch.STABLE);
        registerStandard(Materials.MagMatter, 10, NaniteBranch.GAMBLING);
        registerCandidate(
            "bw/atomic_separation_catalyst",
            GGMaterial.atomicSeparationCatalyst.get(OrePrefixes.nanite, 1));
        registerCandidate("bw/precious_metal_alloy", GGMaterial.preciousMetalAlloy.get(OrePrefixes.nanite, 1));
        registerCandidate("bw/metastable_oganesson", GGMaterial.metastableOganesson.get(OrePrefixes.nanite, 1));
        registerCandidate(
            "bw/extremely_unstable_naquadah",
            GGMaterial.extremelyUnstableNaquadah.get(OrePrefixes.nanite, 1));
        registerCandidate("bw/shirabon", GGMaterial.shirabon.get(OrePrefixes.nanite, 1));
    }

    private void registerStandard(Materials material, int tier, NaniteBranch branch) {
        ItemStack stack = GTOreDictUnificator.get(OrePrefixes.nanite, material, 1);
        if (stack == null) return;
        String id = "gt/" + material.mName;
        Random random = new Random(
            worldSeed ^ CircuitIdentity.item(stack)
                .hashCode());
        double energy = ENERGY[tier - 1] * (0.95 + 0.10 * random.nextDouble());
        double duration = DURATION[tier - 1] * (0.95 + 0.10 * random.nextDouble());
        double rawLoss = branch == NaniteBranch.STABLE ? NaniteMaintenancePolicy.lossCancellation(tier) : 0.30;
        register(
            new NaniteProfile(
                id,
                ItemId.createWithoutNBT(stack),
                tier,
                branch,
                PARALLELS[tier - 1],
                energy,
                duration,
                rawLoss,
                branch == NaniteBranch.GAMBLING ? 0.30 : 0,
                0.30));
    }

    public void registerCandidate(String id, ItemStack item) {
        if (item != null) candidates.put(ItemId.createWithoutNBT(item), id);
    }

    public void register(NaniteProfile profile) {
        profiles.put(profile.item(), profile);
        candidates.put(profile.item(), profile.id());
    }

    public boolean recognizes(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && candidates.containsKey(ItemId.createWithoutNBT(stack));
    }

    public NaniteProfile profile(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0) return null;
        ItemId item = ItemId.createWithoutNBT(stack);
        NaniteProfile baseline = profiles.get(item);
        CircuitNanitesRecipeData data = CircuitNanitesRecipeData.recipeDataMap.get(item);
        if (data == null || data.worldSeed != worldSeed
            || data.nantiesTier < 1
            || data.nantiesTier > 10
            || baseline != null && data.nantiesTier != baseline.tier()) return baseline;
        String id = candidates.get(item);
        if (id == null) return null;
        NaniteBranch branch = baseline == null
            ? (data.failedChance > NaniteMaintenancePolicy.lossCancellation(data.nantiesTier) ? NaniteBranch.GAMBLING
                : NaniteBranch.STABLE)
            : baseline.branch();
        try {
            return new NaniteProfile(
                id,
                item,
                data.nantiesTier,
                branch,
                data.parallelCount,
                data.euModifier,
                data.speedBoost,
                data.failedChance,
                branch == NaniteBranch.STABLE ? 0 : data.outputMultiplier,
                0.30);
        } catch (IllegalArgumentException ignored) {
            return baseline;
        }
    }
}
