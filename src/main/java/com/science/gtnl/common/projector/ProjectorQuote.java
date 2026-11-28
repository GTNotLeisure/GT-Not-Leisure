package com.science.gtnl.common.projector;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

import com.gtnewhorizon.structurelib.util.InventoryUtility;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.ProjectorQuoteResultPacket;
import com.science.gtnl.common.projector.ProjectorStructure.Choice;

// Tells a player how much of each material the build could take right now, from the same places and in the same way
// the build takes it, without taking anything.
public class ProjectorQuote {

    public static final int MAX_KINDS = 1024, ANSWER_TICKS = 20;
    private static final Map<UUID, Integer> LAST_ANSWER = new WeakHashMap<>();

    private ProjectorQuote() {}

    public static void answer(EntityPlayerMP player, int[] items, int[] damages, int[] counts) {
        int now = MinecraftServer.getServer()
            .getTickCounter();
        Integer last = LAST_ANSWER.get(player.getUniqueID());
        if (last != null && now - last < ANSWER_TICKS) return;
        LAST_ANSWER.put(player.getUniqueID(), now);
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
