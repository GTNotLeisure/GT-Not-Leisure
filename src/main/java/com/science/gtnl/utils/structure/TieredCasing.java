package com.science.gtnl.utils.structure;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import net.minecraft.block.Block;

import org.apache.commons.lang3.tuple.Pair;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ITierConverter;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import gregtech.api.casing.ICasing;
import gregtech.api.structure.IStructureChannels;

public record TieredCasing<TIER> (ITierConverter<TIER> tierConverter, Supplier<List<Pair<Block, Integer>>> blocks,
    TIER unsetTier) {

    public TieredCasing {
        Objects.requireNonNull(tierConverter, "tierConverter");
        Objects.requireNonNull(blocks, "blocks");
    }

    /** Resolves registered blocks when the structure element is created. */
    public <T> IStructureElement<T> create(BiConsumer<T, TIER> setter, Function<T, TIER> getter) {
        return StructureUtility.ofBlocksTiered(tierConverter, blocks.get(), unsetTier, setter, getter);
    }

    public <T> IStructureElement<T> create(IStructureChannels channel, BiConsumer<T, TIER> setter,
        Function<T, TIER> getter) {
        return channel.use(create(setter, getter));
    }

    /** Accepts casing enums and other implementations without resolving their blocks during construction. */
    public static <TIER> TieredCasing<TIER> ofCasings(ITierConverter<TIER> tierConverter, TIER unsetTier,
        ICasing... casings) {
        List<ICasing> variants = Arrays.stream(casings)
            .map(Objects::requireNonNull)
            .collect(Collectors.toList());
        return ofCasings(tierConverter, unsetTier, () -> variants);
    }

    /** Defers both casing enum initialization and block lookup until the element is created. */
    public static <TIER> TieredCasing<TIER> ofCasings(ITierConverter<TIER> tierConverter, TIER unsetTier,
        Supplier<? extends List<? extends ICasing>> casings) {
        Objects.requireNonNull(casings, "casings");
        return new TieredCasing<>(
            tierConverter,
            () -> casings.get()
                .stream()
                .map(casing -> Pair.of(casing.getBlock(), casing.getBlockMeta()))
                .collect(Collectors.toList()),
            unsetTier);
    }
}
