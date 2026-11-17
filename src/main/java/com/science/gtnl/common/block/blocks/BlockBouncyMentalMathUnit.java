package com.science.gtnl.common.block.blocks;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import com.science.gtnl.api.IEntityLandingBlock;

public class BlockBouncyMentalMathUnit extends BlockMentalMathUnit implements IEntityLandingBlock {

    // Standing entities still collide downward with ordinary gravity each tick.
    private static final double MIN_BOUNCE_SPEED = 0.1;
    private static final int BOUNCE_PARTICLE_COUNT = 8;

    public BlockBouncyMentalMathUnit(MentalMathUnitDefinition definition) {
        super(definition);
    }

    @Override
    public double getLandingVelocity(Entity entity, double fallingVelocity) {
        if (entity.isSneaking() || fallingVelocity >= -MIN_BOUNCE_SPEED) {
            return 0;
        }
        return -fallingVelocity * (entity instanceof EntityLivingBase ? 1.0 : 0.8);
    }

    @Override
    public void onFallenUpon(World world, int x, int y, int z, Entity entity, float fallDistance) {
        if (!entity.isSneaking()) {
            entity.fallDistance = 0;
            // Minecraft already broadcasts landing dust for falls greater than three blocks.
            if (fallDistance > 0 && fallDistance <= 3) {
                spawnBounceParticles(entity);
            }
        }
        super.onFallenUpon(world, x, y, z, entity, fallDistance);
    }

    @Override
    public void onEntityBounce(Entity entity, double velocity) {
        // Living entities receive their landing particles through onFallenUpon.
        if (!(entity instanceof EntityLivingBase)) {
            spawnBounceParticles(entity);
        }
    }

    private void spawnBounceParticles(Entity entity) {
        if (entity.worldObj instanceof WorldServer world) {
            world.func_147487_a(
                "blockdust_" + Block.getIdFromBlock(this) + "_0",
                entity.posX,
                entity.boundingBox.minY + 0.05,
                entity.posZ,
                BOUNCE_PARTICLE_COUNT,
                0.2,
                0.025,
                0.2,
                0.12);
        }
    }
}
