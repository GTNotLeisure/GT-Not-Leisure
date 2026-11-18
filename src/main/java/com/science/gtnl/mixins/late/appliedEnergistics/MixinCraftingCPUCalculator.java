package com.science.gtnl.mixins.late.appliedEnergistics;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.science.gtnl.common.block.blocks.tile.TileEntityMentalMathUnit;

import appeng.me.cluster.IAEMultiBlock;
import appeng.me.cluster.implementations.CraftingCPUCalculator;

@Mixin(value = CraftingCPUCalculator.class, remap = false)
public abstract class MixinCraftingCPUCalculator {

    @Inject(method = "isValidTile", at = @At("HEAD"), cancellable = true, require = 1)
    private void gtnl$excludeStandaloneUnit(TileEntity tile, CallbackInfoReturnable<Boolean> cir) {
        if (tile instanceof TileEntityMentalMathUnit) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(
        method = "verifyInternalStructure",
        at = @At(value = "INVOKE", target = "Lappeng/me/cluster/IAEMultiBlock;isValid()Z"),
        require = 1)
    private boolean gtnl$excludeStandaloneInterior(IAEMultiBlock tile, Operation<Boolean> original) {
        return !(tile instanceof TileEntityMentalMathUnit) && original.call(tile);
    }
}
