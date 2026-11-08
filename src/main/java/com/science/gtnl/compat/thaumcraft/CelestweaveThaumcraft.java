package com.science.gtnl.compat.thaumcraft;

import com.science.gtnl.common.celestweave.CelestweaveContext;
import com.science.gtnl.common.celestweave.ICelestweaveBehavior;

public class CelestweaveThaumcraft implements ICelestweaveBehavior {

    private final ICelestweaveBehavior[] behaviors = { new CelestweaveWarp(), new CelestweaveVis() };

    @Override
    public void tick(CelestweaveContext ctx) {
        for (ICelestweaveBehavior behavior : behaviors) behavior.tick(ctx);
    }

    @Override
    public void equip(CelestweaveContext ctx) {
        for (ICelestweaveBehavior behavior : behaviors) behavior.equip(ctx);
    }

    @Override
    public void unequip(CelestweaveContext ctx) {
        for (ICelestweaveBehavior behavior : behaviors) behavior.unequip(ctx);
    }
}
