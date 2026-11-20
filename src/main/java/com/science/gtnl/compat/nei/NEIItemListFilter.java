package com.science.gtnl.compat.nei;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import com.science.gtnl.mixins.late.notEnoughItems.AccessorItemListLoader;

import codechicken.nei.ItemList;
import codechicken.nei.ItemPanel;
import codechicken.nei.NEIClientConfig;
import codechicken.nei.RestartableTask;
import codechicken.nei.api.ItemFilter;

public class NEIItemListFilter {

    public static void filter(RestartableTask task) {
        if (!ItemList.loadFinished || task.interrupted()) return;

        List<ItemStack> items = ItemList.items;
        Map<ItemStack, Integer> ordering = AccessorItemListLoader.getOrdering();
        ItemFilter filter = ItemList.getItemListFilter();
        ArrayList<ItemStack> filtered = new ArrayList<>();

        try {
            // NEI worker threads can race legacy class resolution on newer JVMs.
            for (ItemStack item : items) {
                if (task.interrupted()) return;
                if (filter.matches(item)) filtered.add(item);
            }
        } catch (RuntimeException exception) {
            NEIClientConfig.logger.error("Exception filtering item list sequentially", exception);
            task.stop();
            return;
        }

        if (task.interrupted()) {
            return;
        }

        // NEI assigns the same ordering index to items in a collapsible group.
        filtered.sort(Comparator.comparingInt(ordering::get));

        if (task.interrupted()) {
            return;
        }

        ItemPanel.updateItemList(filtered);
    }
}
