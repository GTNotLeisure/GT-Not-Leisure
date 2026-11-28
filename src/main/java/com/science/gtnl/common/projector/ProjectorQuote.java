package com.science.gtnl.common.projector;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.structurelib.util.InventoryUtility;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.ProjectorQuoteResultPacket;
import com.science.gtnl.common.projector.ProjectorStructure.Choice;

// Tells a player how much of each material the build could take right now, from the same places and in the same way
// the build takes it, without taking anything.
public class ProjectorQuote {

    public static final int MAX_KINDS = 1024;

    private ProjectorQuote() {}

    public static void answer(EntityPlayerMP player, int[] items, int[] damages, int[] counts) {
        if (!ProjectorServer.QUOTES.pass(player.getUniqueID())) return;
        int[] kinds = new int[items.length], available = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            Item item = Item.getItemById(items[i]);
            int wanted = Math.max(0, counts[i]);
            kinds[i] = item == null ? -1 : Choice.idOf(item, damages[i]);
            if (item == null || wanted == 0) continue;
            available[i] = player.capabilities.isCreativeMode ? wanted
                : InventoryUtility.takeFromInventory(player, new ItemStack(item, wanted, damages[i]), true);
        }
        ScienceNotLeisure.network.sendTo(new ProjectorQuoteResultPacket(kinds, available), player);
    }
}
