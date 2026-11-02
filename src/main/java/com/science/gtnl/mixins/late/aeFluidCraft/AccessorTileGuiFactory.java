package com.science.gtnl.mixins.late.aeFluidCraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.glodblock.github.inventory.gui.TileGuiFactory;

@Mixin(value = TileGuiFactory.class, remap = false)
public interface AccessorTileGuiFactory {

    @Invoker("getInventory")
    Object gtnl$getInventory(TileEntity tile, ForgeDirection face);

    @Invoker("createClientGui")
    Object gtnl$createClientGui(EntityPlayer player, Object inventory);
}
