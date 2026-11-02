package com.science.gtnl.mixins.early.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import gregtech.api.metatileentity.CommonMetaTileEntity;

@Mixin(value = CommonMetaTileEntity.class, remap = false)
public interface AccessorCommonMetaTileEntity {

    @Invoker("forceUseMui2")
    boolean gtnl$forceUseMui2();
}
