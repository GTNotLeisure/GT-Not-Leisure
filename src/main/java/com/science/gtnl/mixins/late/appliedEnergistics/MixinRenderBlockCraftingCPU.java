package com.science.gtnl.mixins.late.appliedEnergistics;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.science.gtnl.common.block.blocks.tile.TileEntityMentalMathUnit;

import appeng.client.render.blocks.RenderBlockCraftingCPU;

@Mixin(value = RenderBlockCraftingCPU.class, remap = false)
public abstract class MixinRenderBlockCraftingCPU {

    @ModifyExpressionValue(
        method = "isConnected",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/IBlockAccess;getTileEntity(III)Lnet/minecraft/tileentity/TileEntity;",
            remap = true),
        require = 1)
    private TileEntity gtnl$excludeStandaloneUnit(TileEntity tile) {
        return tile instanceof TileEntityMentalMathUnit ? null : tile;
    }
}
