package com.science.gtnl.compat.thaumcraft;

import java.util.Map;
import java.util.function.IntSupplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import com.google.common.collect.MapMaker;
import com.science.gtnl.common.celestweave.CelestweaveContext;
import com.science.gtnl.common.celestweave.CelestweaveModuleSetting;
import com.science.gtnl.common.celestweave.ICelestweaveBehavior;
import com.science.gtnl.config.ItemConfig;

import thaumcraft.common.config.Config;
import thaumcraft.common.lib.potions.PotionWarpWard;

public class CelestweaveWarp implements ICelestweaveBehavior {

    private static final int WARD_DURATION = 300, REFRESH_BELOW = 220;
    private static final IntSupplier[] WARP_EFFECTS = { () -> Config.potionVisExhaustID,
        () -> Config.potionInfVisExhaustID, () -> Config.potionThaumarhiaID, () -> Config.potionUnHungerID,
        () -> Config.potionBlurredID, () -> Config.potionSunScornedID, () -> Config.potionDeathGazeID };
    private static final Potion[] AMBIENT_EFFECTS = { Potion.blindness, Potion.digSlowdown };
    private final Map<EntityPlayer, PotionEffect> wards = new MapMaker().weakKeys()
        .makeMap();

    @Override
    public void tick(CelestweaveContext ctx) {
        if (!ctx.server()) return;
        EntityPlayer player = ctx.player();
        double cost = ItemConfig.celestweave.warpSuppressionEuPerTick;
        if (!CelestweaveModuleSetting.THAUMIC_WARP_SUPPRESSION.on(ctx.stack()) || !ctx.affords(cost)
            || !ctx.drain(cost)) {
            clear(player);
            return;
        }
        maintainWard(player);
        for (IntSupplier effect : WARP_EFFECTS) {
            int id = effect.getAsInt();
            if (player.isPotionActive(id)) player.removePotionEffect(id);
        }
        for (Potion potion : AMBIENT_EFFECTS) {
            PotionEffect effect = player.getActivePotionEffect(potion);
            if (effect != null && effect.getIsAmbient()) player.removePotionEffect(potion.id);
        }
    }

    @Override
    public void unequip(CelestweaveContext ctx) {
        if (ctx.server()) clear(ctx.player());
    }

    private void maintainWard(EntityPlayer player) {
        PotionEffect current = player.getActivePotionEffect(PotionWarpWard.instance);
        if (current == null) {
            PotionEffect ward = new PotionEffect(Config.potionWarpWardID, WARD_DURATION, 0, true);
            player.addPotionEffect(ward);
            wards.put(player, ward);
        } else if (current == wards.get(player) && owned(current)) {
            if (current.getDuration() < REFRESH_BELOW)
                player.addPotionEffect(new PotionEffect(Config.potionWarpWardID, WARD_DURATION, 0, true));
        } else wards.remove(player);
    }

    private void clear(EntityPlayer player) {
        PotionEffect ward = wards.remove(player);
        if (ward != null && ward == player.getActivePotionEffect(PotionWarpWard.instance) && owned(ward))
            player.removePotionEffect(Config.potionWarpWardID);
    }

    private static boolean owned(PotionEffect ward) {
        // Vanilla merges potion effects in place, so a stronger or longer external ward must be preserved.
        return ward.getIsAmbient() && ward.getAmplifier() == 0 && ward.getDuration() <= WARD_DURATION;
    }
}
