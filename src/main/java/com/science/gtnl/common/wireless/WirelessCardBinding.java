package com.science.gtnl.common.wireless;

import java.io.IOException;
import java.util.UUID;
import java.util.function.BiPredicate;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;

import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

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
    // Vanilla item packets size compressed NBT with a signed short; leave room for the binding and a custom name.
    public static final int MAX_DISPLAY_NBT_BYTES = 30 * 1024;

    private record DisplaySetting(int mode, Address address) {}

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
        for (var entry : readDisplaySettings(itemTag).long2ObjectEntrySet()) modes.put(
            entry.getLongKey(),
            entry.getValue()
                .mode());
        return modes;
    }

    private static Long2ObjectLinkedOpenHashMap<DisplaySetting> readDisplaySettings(NBTTagCompound itemTag) {
        var settings = new Long2ObjectLinkedOpenHashMap<DisplaySetting>();
        if (itemTag == null || !itemTag.hasKey(DISPLAY_KEY, 10)) return settings;
        NBTTagCompound data = itemTag.getCompoundTag(DISPLAY_KEY);
        int[] ids = data.getIntArray("ids"), positions = data.getIntArray("positions");
        byte[] modes = data.getByteArray("modes");
        for (int i = 0; i < modes.length && i * 2 + 1 < ids.length && i * 5 + 4 < positions.length; i++) {
            int side = positions[i * 5 + 4];
            if (modes[i] <= 0 || modes[i] >= DISPLAY_MODES || side < 0 || side > 6) continue;
            settings.put(
                (long) ids[i * 2] << 32 | ids[i * 2 + 1] & 0xFFFFFFFFL,
                new DisplaySetting(
                    modes[i],
                    new Address(
                        positions[i * 5],
                        positions[i * 5 + 1],
                        positions[i * 5 + 2],
                        positions[i * 5 + 3],
                        ForgeDirection.getOrientation(side))));
        }
        return settings;
    }

    static boolean setDisplayModes(ItemStack card, long[] ids, int mode, Long2ObjectMap<Address> known) {
        if (mode < 0 || mode >= DISPLAY_MODES) return false;
        var settings = readDisplaySettings(card.getTagCompound());
        int before = settings.size();
        for (long id : ids) {
            Address address = known.get(id);
            if (mode == 0) settings.remove(id);
            else if (address == null) return false;
            else settings.put(id, new DisplaySetting(mode, address));
        }
        boolean grew = settings.size() > before;
        if (grew && settings.size() > MAX_DISPLAY_ENTRIES) return false;
        return writeDisplaySettings(card, settings, grew);
    }

    static boolean refreshDisplayModes(ItemStack card, BiPredicate<Long, Address> stillExists) {
        var settings = readDisplaySettings(card.getTagCompound());
        boolean removed = settings.long2ObjectEntrySet()
            .removeIf(
                entry -> !stillExists.test(
                    entry.getLongKey(),
                    entry.getValue()
                        .address()));
        return removed && writeDisplaySettings(card, settings, false);
    }

    private static boolean writeDisplaySettings(ItemStack card, Long2ObjectMap<DisplaySetting> settings,
        boolean checkSize) {
        NBTTagCompound current = card.getTagCompound();
        if (settings.isEmpty() && (current == null || !current.hasKey(DISPLAY_KEY))) return true;
        NBTTagCompound tag = current == null ? new NBTTagCompound() : (NBTTagCompound) current.copy();
        if (settings.isEmpty()) tag.removeTag(DISPLAY_KEY);
        else {
            int[] ids = new int[settings.size() * 2], positions = new int[settings.size() * 5];
            byte[] modes = new byte[settings.size()];
            int index = 0;
            for (var entry : settings.long2ObjectEntrySet()) {
                DisplaySetting setting = entry.getValue();
                Address address = setting.address();
                ids[index * 2] = (int) (entry.getLongKey() >>> 32);
                ids[index * 2 + 1] = (int) entry.getLongKey();
                modes[index] = (byte) setting.mode();
                positions[index * 5] = address.dimension();
                positions[index * 5 + 1] = address.x();
                positions[index * 5 + 2] = address.y();
                positions[index * 5 + 3] = address.z();
                positions[index++ * 5 + 4] = address.side()
                    .ordinal();
            }
            NBTTagCompound data = new NBTTagCompound();
            data.setIntArray("ids", ids);
            data.setByteArray("modes", modes);
            data.setIntArray("positions", positions);
            tag.setTag(DISPLAY_KEY, data);
            if (checkSize && compressedSize(tag) > MAX_DISPLAY_NBT_BYTES) return false;
        }
        card.setTagCompound(tag);
        return true;
    }

    private static int compressedSize(NBTTagCompound tag) {
        try {
            return CompressedStreamTools.compress(tag).length;
        } catch (IOException failure) {
            return Integer.MAX_VALUE;
        }
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
        WirelessCardBinding previous = read(itemTag);
        if (previous != null && !WirelessFrequencies.same(previous.frequency(), frequency))
            itemTag.removeTag(DISPLAY_KEY);
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
        if (itemTag != null) {
            itemTag.removeTag(KEY);
            itemTag.removeTag(DISPLAY_KEY);
        }
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
