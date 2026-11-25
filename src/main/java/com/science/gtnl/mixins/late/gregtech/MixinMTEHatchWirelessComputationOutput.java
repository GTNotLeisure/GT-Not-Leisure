package com.science.gtnl.mixins.late.gregtech;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.science.gtnl.api.mixinHelper.IContinuousComputationOutput;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import tectech.mechanics.dataTransport.QuantumDataPacket;
import tectech.thing.metaTileEntity.hatch.MTEHatchDataOutput;
import tectech.thing.metaTileEntity.hatch.MTEHatchWirelessComputationOutput;

@Mixin(value = MTEHatchWirelessComputationOutput.class, remap = false)
public abstract class MixinMTEHatchWirelessComputationOutput extends MTEHatchDataOutput
    implements IContinuousComputationOutput {

    @Unique
    private boolean gtnl$continuousComputationOutput;

    public MixinMTEHatchWirelessComputationOutput(String name, int tier, String[] description,
        ITexture[][][] textures) {
        super(name, tier, description, textures);
    }

    @Override
    public void setContinuousComputationOutput(boolean enabled) {
        gtnl$continuousComputationOutput = enabled;
    }

    @Override
    public void moveAround(IGregTechTileEntity tile) {
        QuantumDataPacket packet = q;
        super.moveAround(tile);
        // The producer owns invalidation; wireless uploads still need this rate on the next tick.
        if (gtnl$continuousComputationOutput) q = packet;
    }
}
