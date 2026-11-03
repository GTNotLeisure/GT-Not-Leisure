package com.science.gtnl.common.wireless;

import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;

public record WirelessCardBinding(WirelessFrequency frequency, Address location, UUID creator, UUID owner,
    String ownerName) {

    private static final String KEY = "GTNLWirelessCard";
    private static final int LEGACY_VERSION = 1;
    private static final int VERSION = 2;
    private static final String AUTO_KEY = "GTNLWirelessAutoConnect";
    private static final String GROUP_KEY = "GTNLWirelessCollapseTypes";
    private static final String ID_KEY = "GTNLWirelessCardIdentity";
    private static final String DISPLAY_KEY = "GTNLWirelessDisplayModes";
    public static final int DISPLAY_MODES = 3;
    public static final int MAX_DISPLAY_ENTRIES = 1024;

    static UUID identity(NBTTagCompound itemTag) {
        if (itemTag == null || !itemTag.hasKey(ID_KEY, 8)) return null;
        try {
            return UUID.fromString(itemTag.getString(ID_KEY));
        } catch (IllegalArgumentException invalidId) {
            return null;
        }
    }

    static boolean identify(ItemStack card) {
        if (identity(card.getTagCompound()) != null) return false;
        ItemStackNBT.get(card)
            .setString(
                ID_KEY,
                UUID.randomUUID()
                    .toString());
        return true;
    }

    public static boolean automatic(NBTTagCompound itemTag) {
        return itemTag != null && itemTag.getBoolean(AUTO_KEY);
    }

    public static boolean toggleAutomatic(ItemStack card) {
        return ItemStackNBT.invertBoolean(card, AUTO_KEY);
    }

    static boolean collapseTypes(NBTTagCompound itemTag) {
        return itemTag == null || !itemTag.hasKey(GROUP_KEY, 1) || itemTag.getBoolean(GROUP_KEY);
    }

    static boolean toggleGrouping(ItemStack card) {
        boolean collapse = !collapseTypes(card.getTagCompound());
        ItemStackNBT.setBoolean(card, GROUP_KEY, collapse);
        return collapse;
    }

    static Long2IntLinkedOpenHashMap displayModes(NBTTagCompound itemTag) {
        Long2IntLinkedOpenHashMap modes = new Long2IntLinkedOpenHashMap();
        if (itemTag == null || !itemTag.hasKey(DISPLAY_KEY, 10)) return modes;
        NBTTagCompound data = itemTag.getCompoundTag(DISPLAY_KEY);
        int[] ids = data.getIntArray("ids");
        byte[] values = data.getByteArray("modes");
        for (int i = 0; i < values.length && i * 2 + 1 < ids.length; i++) {
            if (values[i] > 0 && values[i] < DISPLAY_MODES)
                modes.put((long) ids[i * 2] << 32 | ids[i * 2 + 1] & 0xFFFFFFFFL, values[i]);
        }
        return modes;
    }

    static boolean setDisplayModes(ItemStack card, long[] ids, int mode) {
        var modes = displayModes(card.getTagCompound());
        for (long id : ids) {
            modes.remove(id);
            if (mode > 0) modes.put(id, mode);
        }
        // Reject the whole change before touching the card, preserving every previous setting.
        if (mode > 0 && modes.size() > MAX_DISPLAY_ENTRIES) return false;
        if (modes.isEmpty()) {
            if (card.getTagCompound() != null) card.getTagCompound()
                .removeTag(DISPLAY_KEY);
            return true;
        }
        int[] packed = new int[modes.size() * 2];
        byte[] values = new byte[modes.size()];
        int index = 0;
        for (var entry : modes.long2IntEntrySet()) {
            packed[index * 2] = (int) (entry.getLongKey() >>> 32);
            packed[index * 2 + 1] = (int) entry.getLongKey();
            values[index++] = (byte) entry.getIntValue();
        }
        NBTTagCompound data = new NBTTagCompound();
        data.setIntArray("ids", packed);
        data.setByteArray("modes", values);
        ItemStackNBT.get(card)
            .setTag(DISPLAY_KEY, data);
        return true;
    }

    public static WirelessCardBinding read(NBTTagCompound itemTag) {
        if (itemTag == null || !itemTag.hasKey(KEY, 10)) return null;
        NBTTagCompound data = itemTag.getCompoundTag(KEY);
        int version = data.getInteger("version");
        Address location = WirelessLinkData.readPosition(data);
        UUID owner = WirelessFrequencies.uuid(data, "owner");
        if (!data.hasKey("version", 3) || location == null || owner == null) return null;
        WirelessFrequency frequency;
        if (version == LEGACY_VERSION) frequency = WirelessFrequency.legacy(location);
        else if (version == VERSION && WirelessFrequencies.uuid(data, "frequency") != null)
            frequency = WirelessFrequency.of(WirelessFrequencies.uuid(data, "frequency"));
        else return null;
        return new WirelessCardBinding(
            frequency,
            location,
            WirelessFrequencies.uuid(data, "creator"),
            owner,
            data.getString("ownerName"));
    }

    public void write(NBTTagCompound itemTag) {
        NBTTagCompound data = WirelessLinkData
            .writePosition(new NBTTagCompound(), frequency.isLegacy() ? frequency.legacy() : location);
        data.setInteger("version", frequency.isLegacy() ? LEGACY_VERSION : VERSION);
        if (!frequency.isLegacy()) data.setString(
            "frequency",
            frequency.id()
                .toString());
        if (creator != null) data.setString("creator", creator.toString());
        data.setString("owner", owner.toString());
        data.setString("ownerName", ownerName);
        itemTag.setTag(KEY, data);
    }

    public static void clear(NBTTagCompound itemTag) {
        if (itemTag != null) itemTag.removeTag(KEY);
    }

    public boolean belongsTo(UUID player) {
        return owner.equals(player);
    }

    public static boolean upgrade(ItemStack card, EntityPlayer holder) {
        WirelessCardBinding binding = read(card.getTagCompound());
        if (binding == null || !binding.frequency()
            .isLegacy()) return false;
        WirelessFrequency frequency = WirelessFrequencies.upgrade(binding.frequency());
        if (frequency.isLegacy()) return false;
        Address controller = WirelessFrequencies.controller(frequency);
        new WirelessCardBinding(
            frequency,
            controller == null ? binding.location() : controller,
            WirelessFrequencies.creator(frequency),
            binding.owner(),
            binding.ownerName()).write(card.getTagCompound());
        WirelessCardInventory.sync(holder, card);
        return true;
    }
}
