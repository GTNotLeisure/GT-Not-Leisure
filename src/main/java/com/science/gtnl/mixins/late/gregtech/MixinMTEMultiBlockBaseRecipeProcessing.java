package com.science.gtnl.mixins.late.gregtech;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.Nxer.TwistSpaceTechnology.common.modularizedMachine.ModularizedMachineLogic.MultiExecutionCoreMachineBase;
import com.science.gtnl.api.IRecipeProcessingAwareDualHatch;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatch;

@Pseudo
@Mixin(value = { MTEMultiBlockBase.class, MultiExecutionCoreMachineBase.class }, remap = false)
public abstract class MixinMTEMultiBlockBaseRecipeProcessing {

    @Unique
    private MTEMultiBlockBase gtnl$cast() {
        return (MTEMultiBlockBase) (Object) this;
    }

    @Inject(method = "startRecipeProcessing", at = { @At(value = "RETURN") }, require = 1)
    public void startRecipeProcessing(CallbackInfo c) {

        @SuppressWarnings({ "unchecked", "rawtypes" })
        Iterable<IDualInputHatch> l = (Iterable<IDualInputHatch>) (Iterable) GTUtility
            .validMTEList((List<MetaTileEntity>) (Object) (gtnl$cast().mDualInputHatches));
        for (IDualInputHatch hatch : l) {

            if (hatch instanceof IRecipeProcessingAwareDualHatch dualHatch) {
                dualHatch.startRecipeProcessing();
            }
        }
    }

    @Inject(method = "endRecipeProcessing", at = { @At(value = "RETURN") }, require = 1)
    public void endRecipeProcessing(CallbackInfo c) {
        for (IDualInputHatch hatch : (gtnl$cast().mDualInputHatches)) {
            if (hatch == null || !((MetaTileEntity) hatch).isValid()) continue;
            if (hatch instanceof IRecipeProcessingAwareDualHatch dualHatch) {
                gtnl$cast().setResultIfFailure(dualHatch.endRecipeProcessing((MTEMultiBlockBase) (Object) this));
            }
        }
    }

}
