package com.science.gtnl.common.wireless;

import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeDimensionName;
import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeIcon;
import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeInterfaceNames;
import static com.science.gtnl.common.wireless.WirelessCardViewCodec.writeStatistics;
import static com.science.gtnl.common.wireless.WirelessMachineGroups.coordinates;

import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.jetbrains.annotations.Nullable;

import com.google.common.hash.Hashing;
import com.science.gtnl.common.me.InterfaceNameLocalization;
import com.science.gtnl.common.wireless.WirelessCardSnapshot.Device;
import com.science.gtnl.common.wireless.WirelessChannelManager.Address;
import com.science.gtnl.common.wireless.WirelessMachineGroups.Row;
import com.science.gtnl.common.wireless.remote.RemoteDeviceSessions;
import com.science.gtnl.common.wireless.remote.RemoteDeviceState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
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

    WirelessCardSnapshot build(EntityPlayer player, WirelessCardBinding binding, ItemStack card) {
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
        Long2IntMap modes = WirelessCardBinding.displayModes(card.getTagCompound());
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
        for (Row row : rows) entries.appendTag(
            writeRow(
                player,
                binding == null ? null : binding.frequency(),
                row,
                interfaces,
                dimensions,
                modes,
                nextIds,
                targets,
                focus != null));
        Object2LongOpenHashMap<IGridNode> nextDeviceIds = new Object2LongOpenHashMap<>();
        Long2ObjectLinkedOpenHashMap<Device> deviceTargets = new Long2ObjectLinkedOpenHashMap<>();
        if (devices) for (Listed device : devices(player, binding.frequency(), focus))
            entries.appendTag(writeDevice(focus, device, interfaces, dimensions, modes, nextDeviceIds, deviceTargets));
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

    private static List<Listed> devices(EntityPlayer player, WirelessFrequency frequency, IWirelessLinkView link) {
        boolean channels = WirelessChannelManager.channelsEnabled();
        List<Listed> devices = new ObjectArrayList<>();
        for (IGridNode node : link.members()) {
            var remote = RemoteDeviceSessions.probe(player, frequency, node);
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

    static @Nullable IGridNode onlyDevice(EntityPlayer player, WirelessFrequency frequency, IWirelessLinkView link) {
        if (!(link instanceof IWirelessLinkView.ClusterView)) return null;
        IGridNode only = null;
        for (IGridNode node : link.members()) {
            if (!node.hasFlag(GridFlags.REQUIRE_CHANNEL) || WirelessClusterManager.deviceAddress(node) == null)
                continue;
            if (only != null) return null;
            only = node;
        }
        for (IGridNode node : link.members()) {
            if (node.hasFlag(GridFlags.REQUIRE_CHANNEL) || WirelessClusterManager.deviceAddress(node) == null
                || !RemoteDeviceSessions.probe(player, frequency, node)
                    .hasScreen())
                continue;
            if (only != null) return null;
            only = node;
        }
        return only;
    }

    static IGridNode remoteTarget(EntityPlayer player, WirelessFrequency frequency, IWirelessLinkView link) {
        IGridNode only = onlyDevice(player, frequency, link);
        return only == null ? link.node() : only;
    }

    private NBTTagCompound writeDevice(IWirelessLinkView link, Listed device, WirelessInterfaceView interfaces,
        NBTTagCompound dimensions, Long2IntMap modes, Object2LongMap<IGridNode> nextIds,
        Long2ObjectMap<Device> targets) {
        IGridNode node = device.node();
        long id = nextIds.computeIfAbsent(
            node,
            (IGridNode ignored) -> deviceIds.containsKey(node) ? deviceIds.getLong(node) : ++nextTargetId);
        targets.put(id, new Device(link, node));
        NBTTagCompound entry = writePresentation(link, device, interfaces, dimensions, modes);
        entry.setBoolean("device", true);
        entry.setLong("targetId", id);
        entry.setString("key", "device:" + id);
        entry.setString(
            "remote",
            device.remote()
                .key());
        return entry;
    }

    private static NBTTagCompound writePresentation(IWirelessLinkView link, Listed device,
        WirelessInterfaceView interfaces, NBTTagCompound dimensions, Long2IntMap modes) {
        IGridNode node = device.node();
        Address address = device.address();
        NBTTagCompound entry = new NBTTagCompound();
        writeDimensionName(dimensions, address.dimension());
        entry.setInteger("dimension", address.dimension());
        entry.setString("position", coordinates(address));
        entry.setString(
            "name",
            link.deviceNames()
                .getOrDefault(node, ""));
        entry.setString("deviceState", device.state());
        ItemStack icon = node.getGridBlock()
            .getMachineRepresentation();
        writeIcon(entry, "icon", icon);
        String identity = icon == null || icon.getItem() == null ? node.getMachine()
            .getClass()
            .getName() : Item.itemRegistry.getNameForObject(icon.getItem()) + ":" + icon.getItemDamage();
        long displayId = displayId(address, identity);
        entry.setLong("displayId", displayId);
        entry.setInteger("displayMode", modes.get(displayId));
        entry.setString("serverIconName", icon == null ? "" : icon.getDisplayName());
        var name = interfaces.name(node);
        if (name != null) writeInterfaceNames(entry, List.of(name));
        return entry;
    }

    private static long displayId(Address address, String identity) {
        return Hashing.murmur3_128()
            .newHasher()
            .putInt(address.dimension())
            .putInt(address.x())
            .putInt(address.y())
            .putInt(address.z())
            .putInt(
                address.side()
                    .ordinal())
            .putString(identity, StandardCharsets.UTF_8)
            .hash()
            .asLong();
    }

    private NBTTagCompound writeRow(EntityPlayer player, WirelessFrequency frequency, Row row,
        WirelessInterfaceView interfaces, NBTTagCompound dimensions, Long2IntMap modes, Object2LongMap<Target> nextIds,
        Long2ObjectMap<IWirelessLinkView> targets, boolean detail) {
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
        if (row.machine() != null)
            entry.setIntArray("energyHatches", WirelessMachineGroups.energyHatches(row.machine()));
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
        List<Listed> listed = row.group() || detail ? List.of() : devices(player, frequency, row.link());
        IGridNode only = !row.group() && row.link() instanceof IWirelessLinkView.ClusterView && listed.size() == 1
            ? listed.get(0)
                .node()
            : null;
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
                writeRow(player, frequency, Row.cluster(link), interfaces, dimensions, modes, nextIds, targets, false));
            entry.setTag("children", children);
        } else {
            var link = row.link();
            entry.setLong("targetId", identify(link, nextIds, targets));
            entry.setBoolean("singleDevice", only != null);
            entry.setString(
                "remote",
                RemoteDeviceSessions.probe(player, frequency, only == null ? link.node() : only)
                    .state()
                    .key());
            if (!detail) {
                NBTTagList presentations = new NBTTagList();
                for (Listed device : listed)
                    presentations.appendTag(writePresentation(link, device, interfaces, dimensions, modes));
                entry.setTag("displayDevices", presentations);
                if (only != null) {
                    NBTTagCompound presentation = presentations.getCompoundTagAt(0);
                    entry.setLong("displayId", presentation.getLong("displayId"));
                    entry.setInteger("displayMode", presentation.getInteger("displayMode"));
                }
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
        }
        return entry;
    }

    private record Target(IGridNode node, Set<IGridNode> members, boolean entrance) {}

    private record Listed(IGridNode node, Address address, String state, RemoteDeviceState remote) {}

    private record RenameState(Set<IGridNode> members, Map<IGridNode, String> names) {}
}
