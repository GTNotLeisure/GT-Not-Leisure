package com.science.gtnl.mixins.early.minecraft;

import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.science.gtnl.common.wireless.remote.RemoteDeviceSessions;

@Mixin(NetworkManager.class)
public abstract class MixinNetworkManagerRemoteDevice {

    @Shadow
    private INetHandler netHandler;

    // FlameChunk redirects processPacket itself, so check right after the packet leaves the queue.
    @Inject(
        method = "processReceivedPackets",
        at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;", shift = At.Shift.AFTER))
    private void gtnl$checkRemoteDevice(CallbackInfo ci) {
        RemoteDeviceSessions.beforePacket(netHandler);
    }
}
