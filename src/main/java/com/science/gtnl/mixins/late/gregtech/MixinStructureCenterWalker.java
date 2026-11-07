package com.science.gtnl.mixins.late.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase.StructureSize;

// TODO: Remove this compatibility mixin once the minimum GT5 dependency includes the StructureCenterWalker reset.
@Deprecated
@Mixin(
    targets = "gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase$StructureCenterWalker",
    remap = false)
public abstract class MixinStructureCenterWalker {

    @Shadow
    private StructureSize size;

    @Inject(method = "finish", at = @At("RETURN"))
    private void gtnl$releaseCompletedBounds(CallbackInfoReturnable<StructureSize> cir) {
        // Keep all pieces within one check, then release the completed accumulator.
        size = null;
    }
}
