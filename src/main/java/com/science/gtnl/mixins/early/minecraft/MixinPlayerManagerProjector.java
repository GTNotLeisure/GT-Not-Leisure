package com.science.gtnl.mixins.early.minecraft;

import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.science.gtnl.common.projector.ProjectorBuilder;

@Mixin(PlayerManager.class)
public abstract class MixinPlayerManagerProjector {

    @Shadow
    @Final
    private WorldServer theWorldServer;

    @Inject(method = "markBlockForUpdate", at = @At("HEAD"), cancellable = true)
    private void gtnl$holdProjectorBlock(int x, int y, int z, CallbackInfo ci) {
        if (ProjectorBuilder.holdUpdate(theWorldServer, x, y, z)) ci.cancel();
    }
}
