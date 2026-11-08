package com.science.gtnl.compat.thaumcraft;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.science.gtnl.common.celestweave.CelestweaveContext;
import com.science.gtnl.common.celestweave.CelestweaveData;
import com.science.gtnl.common.celestweave.CelestweaveEnergy;
import com.science.gtnl.common.celestweave.CelestweaveModule;
import com.science.gtnl.common.celestweave.CelestweaveModuleSetting;
import com.science.gtnl.common.celestweave.ICelestweaveBehavior;
import com.science.gtnl.common.equipment.EquipmentEnergy;
import com.science.gtnl.config.ItemConfig;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.wands.ItemWandCasting;

public class CelestweaveVis implements ICelestweaveBehavior {

    private static final int INTERVAL_TICKS = 20, VIS_SCALE = 100;
    private static final Aspect[] PRIMAL_ASPECTS = { Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER,
        Aspect.ENTROPY };

    public static int discount(EntityPlayer player, int piece, ItemStack stack) {
        if (!CelestweaveModule.THAUMIC.fits(piece) || !CelestweaveData.active(stack, CelestweaveModule.THAUMIC))
            return 0;
        boolean powered = player == null ? EquipmentEnergy.read(stack) > 0
            : CelestweaveEnergy.hasEnergy(player, piece, stack);
        return powered ? ItemConfig.celestweave.visDiscount : 0;
    }

    @Override
    public void tick(CelestweaveContext ctx) {
        if (!ctx.server() || !CelestweaveModule.THAUMIC.fits(ctx.piece())
            || ctx.player().worldObj.getTotalWorldTime() % INTERVAL_TICKS != 0
            || !CelestweaveModuleSetting.THAUMIC_RECHARGE.on(ctx.stack())
            || !ctx.affords(0)) return;
        int limit = ItemConfig.celestweave.visPerSecond * VIS_SCALE;
        if (limit <= 0) return;
        double euPerUnit = ItemConfig.celestweave.visEu / VIS_SCALE;
        EntityPlayer player = ctx.player();
        ItemStack held = player.getHeldItem();
        boolean inventory = CelestweaveModuleSetting.THAUMIC_INVENTORY.on(ctx.stack());
        boolean changed = false;
        for (Aspect aspect : PRIMAL_ASPECTS) {
            int remaining = limit - recharge(ctx, held, aspect, limit, euPerUnit);
            if (inventory && remaining > 0) {
                for (ItemStack target : player.inventory.mainInventory) {
                    if (target == held) continue;
                    remaining -= recharge(ctx, target, aspect, remaining, euPerUnit);
                    if (remaining <= 0) break;
                }
            }
            changed |= remaining < limit;
        }
        if (changed) player.inventory.markDirty();
    }

    private static int recharge(CelestweaveContext ctx, ItemStack stack, Aspect aspect, int limit, double euPerUnit) {
        if (stack == null || !(stack.getItem() instanceof ItemWandCasting wand)) return 0;
        CelestweaveWandCapacity.track(stack, ctx.player());
        int amount = Math.min(limit, Math.max(0, wand.getMaxVis(stack) - wand.getVis(stack, aspect)));
        if (amount <= 0) return 0;
        if (euPerUnit > 0 && !EquipmentEnergy.free(ctx.player())) amount = (int) Math
            .min(amount, CelestweaveEnergy.available(ctx.player(), ctx.piece(), ctx.stack()) / euPerUnit);
        if (amount <= 0) return 0;
        // Thaumcraft uses centivis and returns the amount it could not accept, including during simulation.
        int accepted = amount - wand.addRealVis(stack, aspect, amount, false);
        if (accepted <= 0 || !ctx.affords(accepted * euPerUnit)) return 0;
        // Commit before settling EU so the last charge keeps its capacity bonus throughout the transfer.
        int supplied = accepted - wand.addRealVis(stack, aspect, accepted, true);
        if (supplied > 0) ctx.drain(supplied * euPerUnit);
        return supplied;
    }
}
