package com.science.gtnl.utils.structure;

import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.science.gtnl.api.casing.GTNLCasings;
import com.science.gtnl.common.machine.multiMachineBase.SteamMultiMachineBase;

import goodgenerator.loader.Loaders;
import gregtech.api.GregTechAPI;
import gregtech.api.casing.Casings;
import gregtech.api.interfaces.IHatchElement;
import gregtech.common.misc.GTStructureChannels;

public class MachineStructureElements {

    private static final TieredCasing<Integer> MACHINE_CASING = TieredCasing.ofCasings(
        SteamMultiMachineBase::getTierMachineCasing,
        -1,
        () -> ImmutableList.of(Casings.BronzePlatedBricks, Casings.SolidSteelMachineCasing));
    private static final TieredCasing<Integer> PIPE_CASING = TieredCasing.ofCasings(
        SteamMultiMachineBase::getTierPipeCasing,
        -1,
        () -> ImmutableList.of(Casings.BronzePipeCasing, Casings.SteelPipeCasing));
    private static final TieredCasing<Integer> FIREBOX_CASING = TieredCasing.ofCasings(
        SteamMultiMachineBase::getTierFireboxCasing,
        -1,
        () -> ImmutableList.of(Casings.BronzeFireboxCasing, Casings.SteelFireboxCasing));
    private static final TieredCasing<Integer> FRAME_CASING = new TieredCasing<>(
        SteamMultiMachineBase::getTierFrameCasing,
        () -> ImmutableList.of(Pair.of(GregTechAPI.sBlockFrames, 300), Pair.of(GregTechAPI.sBlockFrames, 305)),
        -1);
    private static final TieredCasing<Integer> BRICK_CASING = TieredCasing.ofCasings(
        SteamMultiMachineBase::getTierBrickCasing,
        -1,
        () -> ImmutableList.of(GTNLCasings.BronzeBrickCasing, GTNLCasings.SteelBrickCasing));
    private static final TieredCasing<Integer> INDUSTRIAL_CASING = TieredCasing.ofCasings(
        SteamMultiMachineBase::getTierIndustrialCasing,
        -1,
        () -> ImmutableList.of(GTNLCasings.IndustrialSteamCasing, GTNLCasings.AdvancedIndustrialSteamCasing));
    private static final TieredCasing<Integer> GEAR_CASING = TieredCasing.ofCasings(
        SteamMultiMachineBase::getTierGearCasing,
        -1,
        () -> ImmutableList.of(Casings.BronzeGearBoxCasing, Casings.SteelGearBoxCasing));
    private static final TieredCasing<Integer> MATERIAL_BLOCK = new TieredCasing<>(
        SteamMultiMachineBase::getTierMaterialBlockCasing,
        () -> ImmutableList.of(Pair.of(Blocks.iron_block, 0), Pair.of(GregTechAPI.sBlockMetal6, 13)),
        -1);
    private static final TieredCasing<Integer> PLATED_CASING = TieredCasing.ofCasings(
        SteamMultiMachineBase::getTierPlatedCasing,
        -1,
        () -> ImmutableList.of(Casings.StrongBronzeMachineCasing, Casings.SolidSteelMachineCasing));
    private static final TieredCasing<Integer> MACHINE_FRAME = TieredCasing.ofCasings(
        SteamMultiMachineBase::getTierMachineFrame,
        -1,
        () -> ImmutableList.of(GTNLCasings.BronzeMachineFrame, GTNLCasings.SteelMachineFrame));

    private MachineStructureElements() {}

    public static TieredCasing<Integer> componentAssemblylineCasing(Supplier<List<Pair<Block, Integer>>> variants) {
        return new TieredCasing<>(
            (block, meta) -> block == Loaders.componentAssemblylineCasing ? meta : -1,
            variants,
            -2);
    }

    public static List<Pair<Block, Integer>> componentCasingVariants(int tierCount) {
        List<Pair<Block, Integer>> variants = new ArrayList<>(tierCount);
        for (int tier = 0; tier < tierCount; tier++) {
            variants.add(Pair.of(Loaders.componentAssemblylineCasing, tier));
        }
        return variants;
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> machineCasing() {
        return GTStructureChannels.TIER_MACHINE_CASING.use(MachineStructureElements.<T>bareMachineCasing());
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> pipeCasing() {
        return tiered(PIPE_CASING, (t, tier) -> t.tierPipeCasing = tier, t -> t.tierPipeCasing);
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> fireboxCasing() {
        return tiered(FIREBOX_CASING, (t, tier) -> t.tierFireboxCasing = tier, t -> t.tierFireboxCasing);
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> frameCasing() {
        return tiered(FRAME_CASING, (t, tier) -> t.tierFrameCasing = tier, t -> t.tierFrameCasing);
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> brickCasing() {
        return tiered(BRICK_CASING, (t, tier) -> t.tierBrickCasing = tier, t -> t.tierBrickCasing);
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> industrialCasing() {
        return tiered(INDUSTRIAL_CASING, (t, tier) -> t.tierIndustrialCasing = tier, t -> t.tierIndustrialCasing);
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> gearCasing() {
        return tiered(GEAR_CASING, (t, tier) -> t.tierGearCasing = tier, t -> t.tierGearCasing);
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> materialBlock() {
        return tiered(MATERIAL_BLOCK, (t, tier) -> t.tierMaterialBlock = tier, t -> t.tierMaterialBlock);
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> platedCasing() {
        return tiered(PLATED_CASING, (t, tier) -> t.tierPlatedCasing = tier, t -> t.tierPlatedCasing);
    }

    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> machineFrame() {
        return tiered(MACHINE_FRAME, (t, tier) -> t.tierMachineFrame = tier, t -> t.tierMachineFrame);
    }

    @SafeVarargs
    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> machineCasingWithHatches(Class<T> type,
        int casingIndex, int hint, IHatchElement<? super T>... hatches) {
        IStructureElement<T> casing = StructureUtility
            .onElementPass(t -> ++t.mCountCasing, MachineStructureElements.<T>bareMachineCasing());
        return GTStructureChannels.TIER_MACHINE_CASING.use(ofSteamHatches(type, casingIndex, hint, casing, hatches));
    }

    @SafeVarargs
    public static <T extends SteamMultiMachineBase<T>> IStructureElement<T> ofSteamHatches(Class<T> type,
        int casingIndex, int hint, IStructureElement<T> fallback, IHatchElement<? super T>... hatches) {
        return StructureUtility.ofChain(
            SteamMultiMachineBase.buildSteamWirelessInput(type)
                .casingIndex(casingIndex)
                .hint(hint)
                .build(),
            SteamMultiMachineBase.buildSteamBigInput(type)
                .casingIndex(casingIndex)
                .hint(hint)
                .build(),
            SteamMultiMachineBase.buildStandardSteamInput(type)
                .casingIndex(casingIndex)
                .hint(hint)
                .build(),
            buildHatchAdder(type).casingIndex(casingIndex)
                .hint(hint)
                .atLeast(hatches)
                .buildAndChain(fallback));
    }

    private static <T extends SteamMultiMachineBase<T>> IStructureElement<T> bareMachineCasing() {
        return MACHINE_CASING.create((t, tier) -> t.tierMachineCasing = tier, t -> t.tierMachineCasing);
    }

    private static <T> IStructureElement<T> tiered(TieredCasing<Integer> casing, BiConsumer<T, Integer> setter,
        Function<T, Integer> getter) {
        return GTStructureChannels.TIER_MACHINE_CASING.use(casing.create(setter, getter));
    }
}
