package com.science.gtnl.common.block;

import static com.science.gtnl.ScienceNotLeisure.RESOURCE_ROOT_ID;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import com.gtnllib.registry.Registrar;
import com.science.gtnl.common.block.blocks.BlockMentalMathUnit;
import com.science.gtnl.common.block.blocks.MentalMathUnitDefinition;
import com.science.gtnl.common.block.blocks.tile.TileEntityMentalMathUnit;
import com.science.gtnl.common.item.GTNLCreativeTabs;

import appeng.block.AEBaseItemBlock;
import cpw.mods.fml.common.registry.GameRegistry;

public class MentalMathUnitRegistry {

    private static final Registrar BLOCKS = new Registrar(
        "gtnl.",
        RESOURCE_ROOT_ID,
        GTNLCreativeTabs.GTNotLeisureBlock);
    private static final Map<String, BlockMentalMathUnit> UNITS = new LinkedHashMap<>();

    public static BlockMentalMathUnit register(String name, long bytes, int parallelism) {
        return register(name, bytes, parallelism, BlockMentalMathUnit::new);
    }

    public static BlockMentalMathUnit register(String name, long bytes, int parallelism,
        Function<MentalMathUnitDefinition, ? extends BlockMentalMathUnit> factory) {
        Registrar.requirePreInit(name);
        MentalMathUnitDefinition definition = new MentalMathUnitDefinition(name, bytes, parallelism);
        Objects.requireNonNull(factory, "factory");
        if (UNITS.containsKey(name)) {
            throw new IllegalArgumentException("Crafting unit already registered: " + name);
        }
        if (UNITS.isEmpty()) {
            GameRegistry.registerTileEntity(TileEntityMentalMathUnit.class, RESOURCE_ROOT_ID + ":mentalmath_unit");
        }
        BlockMentalMathUnit block = BLOCKS.block("mentalmath_unit_" + name, factory.apply(definition))
            .itemBlock(AEBaseItemBlock.class)
            .texture(RESOURCE_ROOT_ID + ":mentalmath_unit/" + name + "/front")
            .register();
        UNITS.put(name, block);
        return block;
    }

    public static BlockMentalMathUnit get(String name) {
        BlockMentalMathUnit block = UNITS.get(name);
        if (block == null) {
            throw new IllegalArgumentException("Unknown crafting unit: " + name);
        }
        return block;
    }
}
