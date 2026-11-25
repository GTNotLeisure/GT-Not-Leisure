package com.science.gtnl.mixins.late.notEnoughItems;

import java.util.Map;

import net.minecraft.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import codechicken.nei.ItemListLoader;

@Mixin(value = ItemListLoader.class, remap = false)
public interface AccessorItemListLoader {

    @Accessor("ordering")
    static Map<ItemStack, Integer> getOrdering() {
        throw new AssertionError();
    }
}
