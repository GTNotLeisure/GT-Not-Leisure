package com.science.gtnl.common.wireless;

import java.util.List;
import java.util.Objects;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.science.gtnl.common.wireless.WirelessChannelManager.Address;

import appeng.api.networking.IGridNode;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class WirelessCardSnapshot {

    public static final int CHUNK_BYTES = 24 * 1024;

    private final NBTTagCompound payload;
    private final Long2ObjectMap<Device> targets;
    private final Long2ObjectMap<Device> devices;
    private final Long2ObjectMap<Machine> machines;
    final Long2ObjectMap<Address> displayAddresses;

    WirelessCardSnapshot(NBTTagCompound payload, Long2ObjectMap<Device> targets, Long2ObjectMap<Device> devices,
        Long2ObjectMap<Machine> machines, Long2ObjectMap<Address> displayAddresses) {
        this.payload = (NBTTagCompound) payload.copy();
        this.targets = Long2ObjectMaps.unmodifiable(new Long2ObjectOpenHashMap<>(targets));
        this.devices = Long2ObjectMaps.unmodifiable(new Long2ObjectOpenHashMap<>(devices));
        this.machines = Long2ObjectMaps.unmodifiable(new Long2ObjectOpenHashMap<>(machines));
        this.displayAddresses = Long2ObjectMaps.unmodifiable(new Long2ObjectOpenHashMap<>(displayAddresses));
    }

    Machine machine(long id) {
        return machines.get(id);
    }

    IWirelessLinkView target(long id) {
        Device target = targets.get(id);
        return target == null ? null : target.link();
    }

    IGridNode remoteTarget(long id) {
        Device target = targets.get(id);
        return target == null ? null : target.node();
    }

    Device device(long id) {
        return devices.get(id);
    }

    boolean sameView(WirelessCardSnapshot previous) {
        return previous != null && payload.equals(previous.payload);
    }

    boolean sameActions(WirelessCardSnapshot previous) {
        if (previous == null || !targets.keySet()
            .equals(previous.targets.keySet())
            || !devices.keySet()
                .equals(previous.devices.keySet()))
            return false;
        // Icon, availability and channel-count changes do not change the target of a queued click.
        for (String key : new String[] { "auto", "visualMode", "collapseTypes" }) {
            if (!Objects.equals(payload.getTag(key), previous.payload.getTag(key))) return false;
        }
        for (var entry : targets.long2ObjectEntrySet()) {
            Device target = entry.getValue(), old = previous.targets.get(entry.getLongKey());
            if (target.node() != old.node() || !sameTarget(target.link(), old.link())) return false;
        }
        for (var entry : devices.long2ObjectEntrySet()) {
            Device device = entry.getValue(), old = previous.devices.get(entry.getLongKey());
            if (device.node() != old.node()) return false;
        }
        return true;
    }

    private static boolean sameTarget(IWirelessLinkView current, IWirelessLinkView previous) {
        return current.node() == previous.node() && current.target()
            .equals(previous.target())
            && current.members()
                .equals(previous.members())
            && current.paused() == previous.paused()
            && current.conflicted() == previous.conflicted();
    }

    NBTTagCompound packet(long session, long revision, NBTTagList highlight) {
        // Nested snapshot data is immutable after construction. Transport callers only add root fields.
        var data = new NBTTagCompound();
        for (Object key : payload.func_150296_c()) data.setTag((String) key, payload.getTag((String) key));
        data.setLong("session", session);
        data.setLong("revision", revision);
        if (highlight != null) data.setTag("highlight", highlight.copy());
        return data;
    }

    public record Device(IWirelessLinkView link, IGridNode node) {}

    public record Machine(MTEMultiBlockBase machine, List<IWirelessLinkView> links) {}
}
