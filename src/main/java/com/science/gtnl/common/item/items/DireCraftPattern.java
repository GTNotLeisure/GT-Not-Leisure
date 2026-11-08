package com.science.gtnl.common.item.items;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.science.gtnl.utils.DireCraftingPatternDetails;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.items.misc.ItemEncodedPattern;

public class DireCraftPattern extends ItemEncodedPattern {

    public DireCraftPattern() {}

    @Override
    public String getUnlocalizedName() {
        return "item.gtnl.dire_craft_pattern";
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(final ItemStack is, final World w) {
        return getPatternForItem(is, w, 1);
    }

    public DireCraftingPatternDetails getPatternForItem(final ItemStack is, final World w, final int multiply) {
        try {
            return new DireCraftingPatternDetails(is, multiply);
        } catch (final Throwable t) {
            return null;
        }
    }
}
