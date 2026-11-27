package com.science.gtnl.mixins.late.gregtech;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.science.gtnl.common.projector.ProjectorBuilder;

import gregtech.common.blocks.ItemMachines;

@Mixin(ItemMachines.class)
public abstract class MixinItemMachinesProjector {

    // GT sends a placed machine straight to the placing player, past the block sync the projector holds back.
    @WrapWithCondition(
        method = "placeBlockAt",
        remap = false,
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/NetHandlerPlayServer;sendPacket(Lnet/minecraft/network/Packet;)V",
            remap = true))
    private boolean gtnl$holdProjectorMachine(NetHandlerPlayServer handler, Packet packet, ItemStack stack,
        EntityPlayer player, World world, int x, int y, int z) {
        return !(world instanceof WorldServer server && ProjectorBuilder.holdUpdate(server, x, y, z));
    }
}
