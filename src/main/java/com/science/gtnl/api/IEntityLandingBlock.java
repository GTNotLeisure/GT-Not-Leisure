package com.science.gtnl.api;

import net.minecraft.entity.Entity;

public interface IEntityLandingBlock {

    /** Returns the vertical velocity after a downward collision with this block. */
    double getLandingVelocity(Entity entity, double fallingVelocity);

    /** Called after a landing produces an upward velocity. */
    default void onEntityBounce(Entity entity, double velocity) {}
}
