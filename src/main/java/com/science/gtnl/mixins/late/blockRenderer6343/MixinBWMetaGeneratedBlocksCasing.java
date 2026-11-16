package com.science.gtnl.mixins.late.blockRenderer6343;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import bartworks.system.material.BWMetaGeneratedBlocksCasing;
import blockrenderer6343.client.world.ObserverWorld;

@Deprecated
@Mixin(value = BWMetaGeneratedBlocksCasing.class, remap = false)
public abstract class MixinBWMetaGeneratedBlocksCasing {

    @WrapOperation(
        method = "setBlock",
        at = @At(value = "INVOKE", target = "Ljava/lang/Thread;sleep(J)V"),
        require = 0,
        expect = 0)
    private void gtnl$skipSynchronousScanDelay(long millis, Operation<Void> original,
        @Local(argsOnly = true) World world) {
        if (world instanceof ObserverWorld) return;
        original.call(millis);
    }
}
