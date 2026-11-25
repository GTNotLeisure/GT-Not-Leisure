package com.science.gtnl.mixins.early.minecraft;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.science.gtnl.api.IEntityLandingBlock;

@Mixin(Entity.class)
public abstract class MixinEntityLandingBlock {

    @WrapOperation(
        method = "moveEntity",
        at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;motionY:D", opcode = Opcodes.PUTFIELD),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;updateFallState(DZ)V")),
        require = 1)
    private void gtnl$applyLandingVelocity(Entity entity, double velocity, Operation<Void> original) {
        IEntityLandingBlock bounceBlock = null;
        if (entity.motionY < 0) {
            Block block = entity.worldObj.getBlock(
                MathHelper.floor_double(entity.posX),
                MathHelper.floor_double(entity.boundingBox.minY - 0.2),
                MathHelper.floor_double(entity.posZ));
            if (block instanceof IEntityLandingBlock landingBlock) {
                velocity = landingBlock.getLandingVelocity(entity, entity.motionY);
                bounceBlock = landingBlock;
            }
        }
        original.call(entity, velocity);
        if (velocity > 0 && bounceBlock != null) {
            if (!(entity instanceof EntityLivingBase)) {
                // Nonliving entities can apply another ground response after moveEntity returns.
                entity.onGround = false;
            }
            bounceBlock.onEntityBounce(entity, velocity);
        }
    }
}
