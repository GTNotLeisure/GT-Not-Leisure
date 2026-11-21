package com.science.gtnl.common.projector;

import java.util.Iterator;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.factory.GuiFactories;
import com.science.gtnl.common.item.items.MultiblockProjector;
import com.science.gtnl.common.wireless.WirelessCardInventory;
import com.science.gtnl.common.wireless.WirelessCardInventory.CardSlot;

import gregtech.api.GregTechAPI;

public class ProjectorServer {

    private ProjectorServer() {}

    public static ItemStack projectorAt(EntityPlayer player, int location) {
        CardSlot slot = WirelessCardInventory.resolve(player, location);
        if (slot == null) return null;
        ItemStack stack = slot.inventory()
            .getStackInSlot(slot.index());
        return stack != null && stack.getItem() instanceof MultiblockProjector ? stack : null;
    }

    public static void applyPlan(EntityPlayerMP player, int location, NBTTagCompound tag) {
        ItemStack stack = projectorAt(player, location);
        if (stack == null) return;
        ProjectorPlan plan = new ProjectorPlan();
        plan.readFromNBT(tag);
        if (plan.hasMachine() && !ProjectorMachines.isSupported(plan.machine)) plan.clear();
        Iterator<Map.Entry<Long, Integer>> overrides = plan.overrides.entrySet()
            .iterator();
        while (overrides.hasNext()) {
            int id = overrides.next()
                .getValue();
            if (id != ProjectorPlan.CASING
                && (id >= GregTechAPI.METATILEENTITIES.length || GregTechAPI.METATILEENTITIES[id] == null))
                overrides.remove();
        }
        plan.write(stack);
        WirelessCardInventory.sync(player, stack);
    }

    public static void openGui(EntityPlayerMP player, int location) {
        if (projectorAt(player, location) == null) return;
        var factory = GuiFactories.playerInventory();
        if (location < 0) factory.openFromBaubles(player, -location - 1);
        else if (location == 0) factory.openFromMainHand(player);
        else factory.openFromPlayerInventory(player, location - 1);
    }
}
