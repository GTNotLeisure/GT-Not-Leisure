package com.science.gtnl.common.circuit;

import net.minecraft.nbt.NBTTagCompound;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class CircuitUnlockState {

    private final Object2IntMap<String> paidByFamily = new Object2IntOpenHashMap<>();
    private long revision;

    public int paid(CircuitFamily family) {
        return Math.min(family.requiredItems(), paidByFamily.getInt(family.id()));
    }

    public boolean unlocked(CircuitFamily family) {
        return family != null && paid(family) >= family.requiredItems();
    }

    public int submit(CircuitFamily family, int offered) {
        int accepted = Math.min(Math.max(0, offered), family.requiredItems() - paid(family));
        if (accepted > 0) {
            paidByFamily.put(family.id(), paid(family) + accepted);
            revision++;
        }
        return accepted;
    }

    public long revision() {
        return revision;
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        for (Object2IntMap.Entry<String> entry : paidByFamily.object2IntEntrySet()) {
            tag.setInteger(entry.getKey(), entry.getIntValue());
        }
        return tag;
    }

    public void read(NBTTagCompound tag) {
        paidByFamily.clear();
        for (Object key : tag.func_150296_c()) {
            String id = (String) key;
            int paid = tag.getInteger(id);
            if (paid > 0) paidByFamily.put(id, paid);
        }
        revision++;
    }
}
