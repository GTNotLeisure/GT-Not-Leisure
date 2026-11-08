package com.science.gtnl.utils.machine.crop;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.cropsnh.api.ICropCard;
import com.gtnewhorizon.cropsnh.api.ICropMutation;
import com.gtnewhorizon.cropsnh.api.ISoilList;
import com.gtnewhorizon.cropsnh.farming.requirements.SubSoilRequirement;
import com.gtnewhorizon.cropsnh.utility.CropsNHUtils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class CropBreedingSubstrate {

    private static final Map<ICropCard, List<CropBreedingSubstrate>> CROP_CACHE = new ConcurrentHashMap<>();
    private static final Map<ICropMutation, List<CropBreedingSubstrate>> MUTATION_CACHE = new ConcurrentHashMap<>();

    private final SubSoilRequirement extraSoil;
    private final ISoilList plantingSoil;
    private volatile List<ItemStack> alternatives;
    private volatile Boolean automatic;

    private CropBreedingSubstrate(SubSoilRequirement extraSoil, ISoilList plantingSoil) {
        this.extraSoil = extraSoil;
        this.plantingSoil = plantingSoil;
    }

    public static List<CropBreedingSubstrate> forCrop(ICropCard crop) {
        return CROP_CACHE.computeIfAbsent(crop, c -> from(c, c.getGrowthRequirements()));
    }

    public static List<CropBreedingSubstrate> forMutation(ICropMutation mutation) {
        return MUTATION_CACHE.computeIfAbsent(mutation, m -> from(m.getOutput(), m.getRequirements()));
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return false;
        return extraSoil != null ? extraSoil.isValidSubSoil(stack)
            : plantingSoil.isRegistered(CropsNHUtils.getBlockFromItem(stack), CropsNHUtils.getItemMeta(stack));
    }

    public List<ItemStack> alternatives() {
        List<ItemStack> result = alternatives;
        if (result == null) {
            result = Collections.unmodifiableList(
                extraSoil != null ? extraSoil.getItemsForNEI()
                    : plantingSoil.getNEIItemList()
                        .collect(Collectors.toList()));
            alternatives = result;
        }
        return result;
    }

    public boolean isAutomaticallySatisfied() {
        Boolean result = automatic;
        if (result == null) automatic = result = computeAutomatic();
        return result;
    }

    private boolean computeAutomatic() {
        for (ItemStack stack : alternatives()) {
            if (!matches(stack)) continue;
            Block block = CropsNHUtils.getBlockFromItem(stack);
            if (block == null || block == Blocks.air) continue;
            String name = Block.blockRegistry.getNameForObject(block);
            if (name != null && name.startsWith("minecraft:")) return true;
        }
        return false;
    }

    private static List<CropBreedingSubstrate> from(ICropCard crop, Iterable<?> requirements) {
        List<CropBreedingSubstrate> result = new ObjectArrayList<>();
        for (Object requirement : requirements) {
            if (requirement instanceof SubSoilRequirement soil) result.add(new CropBreedingSubstrate(soil, null));
        }
        if (result.isEmpty()) result.add(new CropBreedingSubstrate(null, crop.getSoilTypes()));
        return Collections.unmodifiableList(result);
    }
}
