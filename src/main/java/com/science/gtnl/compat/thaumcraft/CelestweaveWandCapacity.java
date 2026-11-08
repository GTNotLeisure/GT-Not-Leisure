package com.science.gtnl.compat.thaumcraft;

import java.lang.ref.WeakReference;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import com.google.common.collect.MapMaker;
import com.science.gtnl.common.celestweave.CelestweaveData;
import com.science.gtnl.common.celestweave.CelestweaveEnergy;
import com.science.gtnl.common.celestweave.CelestweaveModule;
import com.science.gtnl.common.celestweave.CelestweaveModuleSetting;
import com.science.gtnl.common.item.items.armor.CelestweaveArmor;
import com.science.gtnl.config.ItemConfig;

public class CelestweaveWandCapacity {

    private static final Map<ItemStack, Holder> HOLDERS = new MapMaker().weakKeys()
        .makeMap();

    private record Holder(WeakReference<EntityPlayer> player, int slot) {

        public EntityPlayer owner(ItemStack stack) {
            EntityPlayer owner = player.get();
            return owner != null && !owner.isDead
                && slot < owner.inventory.mainInventory.length
                && owner.inventory.mainInventory[slot] == stack ? owner : null;
        }
    }

    public static void track(ItemStack stack, EntityPlayer player, int slot) {
        if (stack == null || player == null
            || slot < 0
            || slot >= player.inventory.mainInventory.length
            || player.inventory.mainInventory[slot] != stack) return;
        Holder holder = HOLDERS.get(stack);
        if (holder == null || holder.slot() != slot
            || holder.player()
                .get() != player)
            HOLDERS.put(stack, new Holder(new WeakReference<>(player), slot));
    }

    public static void track(ItemStack stack, EntityPlayer player) {
        if (stack == null || player == null) return;
        Holder holder = HOLDERS.get(stack);
        if (holder != null && holder.owner(stack) == player) return;
        for (int slot = 0; slot < player.inventory.mainInventory.length; slot++) {
            if (player.inventory.mainInventory[slot] == stack) {
                track(stack, player, slot);
                return;
            }
        }
    }

    public static int capacity(ItemStack stack, int baseCapacity) {
        int multiplier = ItemConfig.celestweave.visCapacityMultiplier;
        if (stack == null || baseCapacity <= 0 || multiplier <= 1) return baseCapacity;
        Holder holder = HOLDERS.get(stack);
        if (holder == null) return baseCapacity;
        EntityPlayer player = holder.owner(stack);
        if (player == null) {
            HOLDERS.remove(stack);
            return baseCapacity;
        }
        ItemStack chest = CelestweaveData.piece(player, CelestweaveArmor.CHEST);
        if (chest == null || !CelestweaveData.active(chest, CelestweaveModule.THAUMIC)
            || !CelestweaveModuleSetting.THAUMIC_CAPACITY.on(chest)
            || !CelestweaveEnergy.hasEnergy(player, CelestweaveArmor.CHEST, chest)) return baseCapacity;
        return (int) Math.min(Integer.MAX_VALUE, (long) baseCapacity * multiplier);
    }
}
