package com.science.gtnl.mixins.late.appliedEnergistics;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.science.gtnl.common.wireless.remote.AE2RemoteDeviceGui;

import appeng.core.sync.GuiBridge;
import appeng.util.Platform;

@Mixin(value = Platform.class, remap = false)
public abstract class MixinPlatformRemoteDevice {

    @Inject(
        method = "openGUI(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraftforge/common/util/ForgeDirection;Lappeng/core/sync/GuiBridge;I)V",
        at = @At("HEAD"),
        cancellable = true)
    private static void gtnl$openInRemoteSession(EntityPlayer player, TileEntity tile, ForgeDirection side,
        GuiBridge bridge, int slotIndex, CallbackInfo ci) {
        if (AE2RemoteDeviceGui.openInSession(player, tile, side, bridge)) ci.cancel();
    }
}
