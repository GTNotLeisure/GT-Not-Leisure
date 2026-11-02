package com.science.gtnl.mixins.late.aeFluidCraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.glodblock.github.inventory.InventoryHandler;
import com.glodblock.github.util.BlockPos;
import com.science.gtnl.common.wireless.remote.AE2FCRemoteDeviceGui;

@Mixin(value = InventoryHandler.class, remap = false)
public abstract class MixinInventoryHandlerRemoteDevice {

    @Inject(method = "openGui", at = @At("HEAD"), cancellable = true)
    private static void gtnl$openInRemoteSession(EntityPlayer player, World world, BlockPos pos, ForgeDirection face,
        Object screen, CallbackInfo ci) {
        if (AE2FCRemoteDeviceGui.openInSession(player, world, pos, screen)) ci.cancel();
    }
}
