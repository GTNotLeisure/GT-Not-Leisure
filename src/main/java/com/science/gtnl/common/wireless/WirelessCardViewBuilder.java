package com.science.gtnl.common.wireless;

import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeDimensionName;
import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeIcon;
import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeInterfaceNames;
import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeStatistics;
import static com.science.gtnl.common.wireless.WirelessMachineGroups.coordinates;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.science.gtnl.common.me.InterfaceNameLocalization;
import com.science.gtnl.common.wireless.WirelessCardSnapshot.Device;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;
import com.science.gtnl.common.wireless.WirelessMachineGroups.Row;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class WirelessCardViewBuilder {

    private static final List<String> DEVICE_STATES = List.of("missing", "offline", "online");

    public IGridNode detailNode;
    public boolean detailDevices;
    private final WirelessMachineGroups.Startup startup = new WirelessMachineGroups.Startup();
    private Object2LongOpenHashMap<Target> targetIds = new Object2LongOpenHashMap<>();
    private Object2LongOpenHashMap<IGridNode> deviceIds = new Object2LongOpenHashMap<>();
    private long nextTargetId, nameRevision;
    private RenameState renameState;

    long nameRevision() {
        return nameRevision;
    }

    WirelessCardSnapshot build(WirelessCardBinding binding, ItemStack card) {
        var interfaces = new WirelessInterfaceView();
        List<IWirelessLinkView> all = binding == null ? List.of()
            : new ObjectArrayList<>(WirelessClusterManager.links(binding.frequency()));
        boolean initializing = startup.waiting(all);
        var focus = detailNode == null ? null
            : all.stream()
                .filter(
                    link -> link.members()
                        .contains(detailNode))
                .findFirst()
                .orElse(null);
        if (focus == null) detailNode = null;
        RenameState nextRenameState = focus == null ? null : new RenameState(focus.members(), focus.deviceNames());
        if (!Objects.equals(renameState, nextRenameState)) nameRevision++;
        renameState = nextRenameState;
        boolean devices = focus != null && detailDevices;
        List<Row> rows = initializing || devices ? List.of()
            : focus == null ? WirelessMachineGroups.group(all, interfaces)
                : WirelessClusterManager.entranceLinks(binding.frequency(), focus.cluster())
                    .stream()
                    .map(Row::cluster)
                    .toList();
        NBTTagCompound data = new NBTTagCompound();
        NBTTagCompound dimensions = new NBTTagCompound();
        data.setBoolean("detail", focus != null);
        data.setBoolean("devices", devices);
        data.setBoolean("initializing", initializing);
        data.setBoolean("collapseTypes", WirelessCardBinding.collapseTypes(card.getTagCompound()));
        data.setString("name", focus == null ? "" : focus.name());
        data.setLong("nameRevision", nameRevision);
        data.setString("detailTarget", focus == null ? "" : focus.target() + (devices ? "/devices" : ""));
        data.setBoolean(
            "multipleNames",
            focus != null && focus.names()
                .size() > 1);
        data.setBoolean(
            "renameable",
            focus != null && !focus.deviceNames()
                .isEmpty());
        data.setBoolean("auto", WirelessCardBinding.automatic(card.getTagCompound()));
        data.setInteger("visualMode", WirelessCardVisualisation.mode(card.getTagCompound()));
        data.setBoolean("bound", binding != null);
        writeStatistics(data, WirelessMachineGroups.summarize(focus == null ? all : List.of(focus)));
        data.setInteger("total", focus == null ? all.size() : 1);
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
        Long2ObjectLinkedOpenHashMap<IWirelessLinkView> targets = new Long2ObjectLinkedOpenHashMap<>();
        if (focus != null) data.setLong("detailId", identify(focus, nextIds, targets));
        NBTTagList entries = new NBTTagList();
        for (Row row : rows) entries.appendTag(writeRow(row, interfaces, dimensions, nextIds, targets, focus != null));
        Object2LongOpenHashMap<IGridNode> nextDeviceIds = new Object2LongOpenHashMap<>();
        Long2ObjectLinkedOpenHashMap<Device> deviceTargets = new Long2ObjectLinkedOpenHashMap<>();
        if (devices) for (Listed device : devices(focus))
            entries.appendTag(writeDevice(focus, device, dimensions, nextDeviceIds, deviceTargets));
        targetIds = nextIds;
        deviceIds = nextDeviceIds;
        data.setTag("rows", entries);
        data.setTag("dimensions", dimensions);
        return new WirelessCardSnapshot(data, targets, deviceTargets);
    }

    private long identify(IWirelessLinkView link, Object2LongMap<Target> nextIds,
        Long2ObjectMap<IWirelessLinkView> targets) {
        Target key = new Target(link.node(), link.members(), link instanceof IWirelessLinkView.EntranceView);
        long id = nextIds.computeIfAbsent(
            key,
            (Target ignored) -> targetIds.containsKey(key) ? targetIds.getLong(key) : ++nextTargetId);
        targets.put(id, link);
        return id;
    }

    private static List<Listed> devices(IWirelessLinkView link) {
        boolean channels = WirelessChannelManager.channelsEnabled();
        List<Listed> devices = new ObjectArrayList<>();
        for (IGridNode node : link.members()) {
            if (!node.hasFlag(GridFlags.REQUIRE_CHANNEL)) continue;
            Address address = WirelessClusterManager.deviceAddress(node);
            if (address == null) continue;
            String state = channels && !node.meetsChannelRequirements() ? "missing"
                : node.isActive() ? "online" : "offline";
            devices.add(new Listed(node, address, state));
        }
        devices.sort(
            Comparator.comparingInt((Listed device) -> DEVICE_STATES.indexOf(device.state()))
                .thenComparing(Listed::address, Address.POSITION_ORDER));
        return devices;
    }

    private NBTTagCompound writeDevice(IWirelessLinkView link, Listed device, NBTTagCompound dimensions,
        Object2LongMap<IGridNode> nextIds, Long2ObjectMap<Device> targets) {
        IGridNode node = device.node();
        Address address = device.address();
        long id = nextIds.computeIfAbsent(
            node,
            (IGridNode ignored) -> deviceIds.containsKey(node) ? deviceIds.getLong(node) : ++nextTargetId);
        targets.put(id, new Device(link, node));
        NBTTagCompound entry = new NBTTagCompound();
        writeDimensionName(dimensions, address.dimension());
        entry.setBoolean("device", true);
        entry.setLong("targetId", id);
        entry.setString("key", "device:" + id);
        entry.setInteger("dimension", address.dimension());
        entry.setString("position", coordinates(address));
        entry.setString(
            "name",
            link.deviceNames()
                .getOrDefault(node, ""));
        entry.setString("deviceState", device.state());
        writeIcon(
            entry,
            "icon",
            node.getGridBlock()
                .getMachineRepresentation());
        return entry;
    }

    private NBTTagCompound writeRow(Row row, WirelessInterfaceView interfaces, NBTTagCompound dimensions,
        Object2LongMap<Target> nextIds, Long2ObjectMap<IWirelessLinkView> targets, boolean detail) {
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
            row.group() ? ""
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
        writeStatistics(entry, row.statistics());
        var entrance = !row.group() && row.link() instanceof IWirelessLinkView.EntranceView runtime ? runtime : null;
        entry.setBoolean("runtime", entrance != null);
        entry.setInteger(
            "usedChannels",
            entrance == null ? -1
                : entrance.usedChannels()
                    .orElse(-1));
        ItemStack icon = row.icon();
        writeIcon(entry, "icon", icon);
        if (row.type() != null) writeIcon(
            entry,
            "typeIcon",
            row.type()
                .icon());
        entry.setString("serverIconName", icon == null ? "" : icon.getDisplayName());
        if (row.group()) {
            NBTTagList children = new NBTTagList();
            for (var link : row.links())
                children.appendTag(writeRow(Row.cluster(link), interfaces, dimensions, nextIds, targets, false));
            entry.setTag("children", children);
        } else {
            var link = row.link();
            entry.setLong("targetId", identify(link, nextIds, targets));
            if (!detail) {
                var names = interfaces.names(link);
                NBTTagList labels = new NBTTagList();
                for (var name : names) {
                    NBTTagCompound label = new NBTTagCompound();
                    label.setString("rawName", name.rawName());
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
                // Interface display icons can differ from the underlying device's searchable name.
                ItemStack device = link.node()
                    .getGridBlock()
                    .getMachineRepresentation();
                writeIcon(entry, "searchIcon", device);
                entry.setString("serverDeviceName", device == null ? "" : device.getDisplayName());
                writeInterfaceNames(entry, names);
            }
        }
        return entry;
    }

    private record Target(IGridNode node, Set<IGridNode> members, boolean entrance) {}

    private record Listed(IGridNode node, Address address, String state) {}

    private record RenameState(Set<IGridNode> members, Map<IGridNode, String> names) {}
}
