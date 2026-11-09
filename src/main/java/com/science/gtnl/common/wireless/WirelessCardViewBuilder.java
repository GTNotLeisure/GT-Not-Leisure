package com.science.gtnl.common.wireless;

import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeDimensionName;
import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeIcon;
import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeInterfaceNames;
import static com.science.gtnl.common.wireless.WirelessMachineGroups.coordinates;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.science.gtnl.common.me.InterfaceNameLocalization;
import com.science.gtnl.common.wireless.WirelessCardSnapshot.Device;
import com.science.gtnl.common.wireless.WirelessCardSnapshot.Machine;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;
import com.science.gtnl.common.wireless.WirelessMachineGroups.Row;
import com.science.gtnl.common.wireless.remote.RemoteDeviceSessions;
import com.science.gtnl.common.wireless.remote.RemoteDeviceState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

public class WirelessCardViewBuilder {

    private static final List<String> DEVICE_STATES = List.of("missing", "offline", "online");
    public static final long PRESENTATION_REFRESH_TICKS = 100;

    private final WirelessMachineGroups.Startup startup = new WirelessMachineGroups.Startup();
    private Object2LongOpenHashMap<Target> targetIds = new Object2LongOpenHashMap<>();
    private Object2LongOpenHashMap<IGridNode> deviceIds = new Object2LongOpenHashMap<>(),
        nextDeviceIds = new Object2LongOpenHashMap<>();
    private final Long2ObjectMap<Device> devices = new Long2ObjectOpenHashMap<>();
    private long nextTargetId;
    private final Long2ObjectMap<Address> displayAddresses = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectMap<Machine> machines = new Long2ObjectOpenHashMap<>();
    private Reference2LongOpenHashMap<MTEMultiBlockBase> machineIds = new Reference2LongOpenHashMap<>(),
        nextMachineIds = new Reference2LongOpenHashMap<>();
    private long presentationTick = Long.MIN_VALUE;
    private WirelessInterfaceView interfaces = new WirelessInterfaceView();
    private final Map<IGridNode, RemoteDeviceSessions.Availability> availability = new Reference2ObjectOpenHashMap<>();
    private final Map<IGridNode, Presentation> presentations = new Reference2ObjectOpenHashMap<>();

    void invalidatePresentation() {
        presentationTick = Long.MIN_VALUE;
    }

    boolean refreshPresentation(long tick) {
        if (presentationTick != Long.MIN_VALUE && tick >= presentationTick
            && tick - presentationTick < PRESENTATION_REFRESH_TICKS) return false;
        presentationTick = tick;
        interfaces = new WirelessInterfaceView();
        availability.clear();
        presentations.clear();
        return true;
    }

    private RemoteDeviceSessions.Availability probe(EntityPlayer player, WirelessFrequency frequency, IGridNode node) {
        // Display hints only; opening and using a remote screen still checks live permissions.
        return availability.computeIfAbsent(node, key -> RemoteDeviceSessions.probe(player, frequency, key));
    }

    WirelessCardSnapshot build(EntityPlayer player, WirelessCardBinding binding, ItemStack card) {
        displayAddresses.clear();
        machines.clear();
        devices.clear();
        nextMachineIds = new Reference2LongOpenHashMap<>();
        nextDeviceIds = new Object2LongOpenHashMap<>();
        if (refreshPresentation(player.worldObj.getTotalWorldTime()) && binding != null)
            WirelessMachineGroups.forgetRemovedGroupNames(binding.frequency());
        WirelessFrequency frequency = binding == null ? null : binding.frequency();
        List<IWirelessLinkView> all = frequency == null ? List.of()
            : new ObjectArrayList<>(WirelessClusterManager.links(frequency));
        Map<IWirelessLinkView, List<Listed>> members = new Reference2ObjectOpenHashMap<>();
        for (IWirelessLinkView link : all) members.put(link, devices(player, frequency, link));
        boolean initializing = startup.waiting(all);
        List<Row> rows = initializing ? List.of() : WirelessMachineGroups.group(all, interfaces);
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound dimensions = new NBTTagCompound();
        Long2IntMap modes = WirelessCardBinding.displayModes(card.getTagCompound());
        data.setBoolean("initializing", initializing);
        data.setBoolean("collapseTypes", WirelessCardBinding.collapseTypes(card.getTagCompound()));
        data.setBoolean("auto", WirelessCardBinding.automatic(card.getTagCompound()));
        data.setInteger("visualMode", WirelessCardVisualisation.mode(card.getTagCompound()));
        data.setBoolean("bound", binding != null);
        writeStatistics(data, all, members);
        data.setInteger("total", all.size());
        data.setInteger("entrances", binding == null ? 0 : WirelessChannelManager.entranceCount(binding.frequency()));
        data.setInteger("entranceLimit", WirelessChannelManager.entranceLimit());
        if (binding != null) {
            var controller = WirelessFrequencies.controller(binding.frequency());
            var sourceAddress = controller == null ? binding.location() : controller;
            data.setString("source", coordinates(sourceAddress));
            writeDimensionName(dimensions, sourceAddress.dimension());
            IGridNode source = WirelessFrequencies.controllerNode(binding.frequency());
            data.setBoolean("sourceLoaded", source != null);
            data.setBoolean("sourceSplit", WirelessFrequencies.split(binding.frequency()));
            data.setBoolean("channels", WirelessChannelManager.channelsEnabled());
            if (source != null && source.getGrid() != null) {
                var allocation = WirelessChannelManager.allocation(source.getGrid());
                data.setInteger("capacity", WirelessChannelManager.capacity(source.getGrid()));
                data.setInteger("used", allocation == null ? -1 : allocation.used());
            }
        }
        Object2LongOpenHashMap<Target> nextIds = new Object2LongOpenHashMap<>();
        Long2ObjectLinkedOpenHashMap<Device> targets = new Long2ObjectLinkedOpenHashMap<>();
        NBTTagList entries = new NBTTagList();
        for (Row row : rows)
            entries.appendTag(writeRow(player, frequency, row, members, dimensions, modes, nextIds, targets));
        targetIds = nextIds;
        deviceIds = nextDeviceIds;
        machineIds = nextMachineIds;
        data.setTag("rows", entries);
        data.setTag("dimensions", dimensions);
        return new WirelessCardSnapshot(data, targets, devices, machines, displayAddresses);
    }

    private long identifyMachine(Row group) {
        MTEMultiBlockBase machine = group.machine();
        long id = nextMachineIds.computeIfAbsent(
            machine,
            (MTEMultiBlockBase ignored) -> machineIds.containsKey(machine) ? machineIds.getLong(machine)
                : ++nextTargetId);
        machines.put(id, new Machine(machine, group.links()));
        return id;
    }

    private long identify(IWirelessLinkView link, IGridNode remote, Object2LongMap<Target> nextIds,
        Long2ObjectMap<Device> targets) {
        Target key = new Target(link.node(), link.members());
        long id = nextIds.computeIfAbsent(
            key,
            (Target ignored) -> targetIds.containsKey(key) ? targetIds.getLong(key) : ++nextTargetId);
        targets.put(id, new Device(link, remote));
        return id;
    }

    private List<Listed> devices(EntityPlayer player, WirelessFrequency frequency, IWirelessLinkView link) {
        boolean channels = WirelessChannelManager.channelsEnabled();
        List<Listed> devices = new ObjectArrayList<>();
        for (IGridNode node : link.members()) {
            var remote = probe(player, frequency, node);
            if (!node.hasFlag(GridFlags.REQUIRE_CHANNEL) && !remote.hasScreen()) continue;
            Address address = WirelessClusterManager.deviceAddress(node);
            if (address == null) continue;
            String state = channels && node.hasFlag(GridFlags.REQUIRE_CHANNEL) && !node.meetsChannelRequirements()
                ? "missing"
                : node.isActive() ? "online" : "offline";
            devices.add(new Listed(node, address, state, remote.state()));
        }
        devices.sort(
            Comparator.comparingInt((Listed device) -> DEVICE_STATES.indexOf(device.state()))
                .thenComparing(Listed::address, Address.POSITION_ORDER));
        return devices;
    }

    private static void writeStatistics(NBTTagCompound data, List<IWirelessLinkView> links,
        Map<IWirelessLinkView, List<Listed>> members) {
        int total = 0, online = 0, missing = 0;
        boolean stable = true;
        for (IWirelessLinkView link : links) {
            List<Listed> listed = members.get(link);
            total += listed.size();
            stable &= link.statistics()
                .stable();
            for (Listed device : listed) {
                switch (device.state()) {
                    case "online" -> online++;
                    case "missing" -> missing++;
                }
            }
        }
        data.setInteger("devices", total);
        data.setInteger("online", online);
        data.setInteger("missing", missing);
        data.setBoolean("stable", stable);
    }

    private long identifyDevice(IWirelessLinkView link, IGridNode node) {
        long id = nextDeviceIds.computeIfAbsent(
            node,
            (IGridNode ignored) -> deviceIds.containsKey(node) ? deviceIds.getLong(node) : ++nextTargetId);
        devices.put(id, new Device(link, node));
        return id;
    }

    private NBTTagCompound writePresentation(IWirelessLinkView link, Listed device, WirelessInterfaceView interfaces,
        NBTTagCompound dimensions, Long2IntMap modes) {
        IGridNode node = device.node();
        Address address = device.address();
        Presentation cached = presentations.get(node);
        if (cached == null || !cached.address()
            .equals(address)) {
            NBTTagCompound data = new NBTTagCompound();
            ItemStack icon = node.getGridBlock()
                .getMachineRepresentation();
            writeIcon(data, "icon", icon);
            String position = coordinates(address);
            if (node.getMachine() instanceof IPart) position += " / " + address.side()
                .name();
            data.setString("position", position);
            data.setLong("displayId", WirelessDeviceDisplay.id(address, node));
            data.setString("serverIconName", icon == null ? "" : icon.getDisplayName());
            var name = interfaces.name(node);
            if (name != null) writeInterfaceNames(data, List.of(name));
            cached = new Presentation(address, data);
            presentations.put(node, cached);
        }
        NBTTagCompound entry = (NBTTagCompound) cached.data()
            .copy();
        writeDimensionName(dimensions, address.dimension());
        entry.setInteger("dimension", address.dimension());
        entry.setString(
            "name",
            link.deviceNames()
                .getOrDefault(node, ""));
        entry.setString("deviceState", device.state());
        entry.setLong("targetId", identifyDevice(link, node));
        entry.setString(
            "remote",
            device.remote()
                .key());
        entry.setBoolean(
            "renameable",
            link.deviceNames()
                .containsKey(node));
        long displayId = entry.getLong("displayId");
        displayAddresses.put(displayId, address);
        entry.setInteger("displayMode", modes.get(displayId));
        return entry;
    }

    private NBTTagCompound writeRow(EntityPlayer player, WirelessFrequency frequency, Row row,
        Map<IWirelessLinkView, List<Listed>> members, NBTTagCompound dimensions, Long2IntMap modes,
        Object2LongMap<Target> nextIds, Long2ObjectMap<Device> targets) {
        var target = row.target();
        NBTTagCompound entry = new NBTTagCompound();
        writeDimensionName(dimensions, target.dimension());
        entry.setInteger("dimension", target.dimension());
        entry.setString("position", coordinates(target));
        entry.setString("key", (row.group() ? "machine:" : "cluster:") + target);
        entry.setString("sortKey", target.toString());
        entry.setString(
            "typeKey",
            row.type() == null ? ""
                : row.type()
                    .sortKey());
        entry.setBoolean("group", row.group());
        if (row.machine() != null) {
            entry.setIntArray("energyHatches", WirelessMachineGroups.energyHatches(row.machine()));
            if (WirelessMachineGroups.canName(frequency)) entry.setLong("machineId", identifyMachine(row));
        }
        entry.setBoolean(
            "shared",
            row.type() != null && row.type()
                .shared());
        entry.setInteger(
            "clusters",
            row.links()
                .size());
        entry.setString(
            "name",
            row.group() ? WirelessMachineGroups.groupName(frequency, row.machine())
                : row.link()
                    .name());
        entry.setString(
            "side",
            target.side()
                .name());
        entry.setString("state", row.conflicted() ? "conflict" : row.state());
        entry.setBoolean("paused", row.paused());
        entry.setBoolean("conflicted", row.conflicted());
        entry.setInteger("entrances", row.entrances());
        writeStatistics(entry, row.links(), members);
        List<Listed> listed = row.group() ? List.of() : members.get(row.link());
        IGridNode only = !row.group() && listed.size() == 1 ? listed.get(0)
            .node() : null;
        ItemStack icon = only == null ? row.icon()
            : only.getGridBlock()
                .getMachineRepresentation();
        writeIcon(entry, "icon", icon);
        if (row.type() != null) writeIcon(
            entry,
            "typeIcon",
            row.type()
                .icon());
        entry.setString("serverIconName", icon == null ? "" : icon.getDisplayName());
        if (row.group()) {
            NBTTagList children = new NBTTagList();
            for (var link : row.links()) children.appendTag(
                writeRow(player, frequency, Row.cluster(link), members, dimensions, modes, nextIds, targets));
            entry.setTag("children", children);
        } else {
            var link = row.link();
            IGridNode remote = only == null ? link.node() : only;
            entry.setLong("targetId", identify(link, remote, nextIds, targets));
            entry.setBoolean("singleDevice", only != null);
            if (only == null) {
                String title = WirelessMachineGroups.clusterTitle(frequency, link);
                if (!title.isEmpty()) entry.setString("name", title);
                entry.setString("clusterTitle", title);
                entry.setBoolean("titleable", WirelessMachineGroups.canName(frequency));
            }
            entry.setString(
                "remote",
                probe(player, frequency, remote).state()
                    .key());
            NBTTagList presentations = new NBTTagList();
            for (Listed device : listed)
                presentations.appendTag(writePresentation(link, device, interfaces, dimensions, modes));
            entry.setTag("displayDevices", presentations);
            if (only != null) {
                NBTTagCompound presentation = presentations.getCompoundTagAt(0);
                entry.setLong("displayId", presentation.getLong("displayId"));
                entry.setInteger("displayMode", presentation.getInteger("displayMode"));
            }
            if (link.entrances() > 1) entry.setTag("entranceList", writeEntrances(frequency, link, dimensions));
            var names = interfaces.names(link);
            NBTTagList labels = new NBTTagList();
            for (var name : names) {
                NBTTagCompound label = new NBTTagCompound();
                label.setString("rawName", name.rawName());
                label.setString("customName", name.customName());
                label.setString("suffix", name.suffix());
                label.setString(
                    "serverName",
                    InterfaceNameLocalization.localizeName(name.rawName(), name.suffix(), name.icon()));
                writeIcon(label, "icon", name.icon());
                labels.appendTag(label);
            }
            entry.setTag("searchLabels", labels);
            NBTTagList customNames = new NBTTagList();
            link.names()
                .stream()
                .sorted()
                .forEach(name -> customNames.appendTag(new NBTTagString(name)));
            entry.setTag("customNames", customNames);
            writeInterfaceNames(entry, names);
        }
        return entry;
    }

    private static NBTTagList writeEntrances(WirelessFrequency frequency, IWirelessLinkView link,
        NBTTagCompound dimensions) {
        NBTTagList entrances = new NBTTagList();
        for (var usage : WirelessClusterManager.entranceUsage(frequency, link.cluster())) {
            NBTTagCompound entrance = new NBTTagCompound();
            Address target = usage.target();
            writeDimensionName(dimensions, target.dimension());
            entrance.setInteger("dimension", target.dimension());
            entrance.setString("position", coordinates(target));
            entrance.setInteger(
                "usedChannels",
                usage.usedChannels()
                    .orElse(-1));
            entrances.appendTag(entrance);
        }
        return entrances;
    }

    private record Target(IGridNode node, Set<IGridNode> members) {}

    private record Listed(IGridNode node, Address address, String state, RemoteDeviceState remote) {}

    private record Presentation(Address address, NBTTagCompound data) {}
}
