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

    public IGridNode detailNode;
    public boolean detailDevices;
    private final WirelessMachineGroups.Startup startup = new WirelessMachineGroups.Startup();
    private Object2LongOpenHashMap<Target> targetIds = new Object2LongOpenHashMap<>();
    private Object2LongOpenHashMap<IGridNode> deviceIds = new Object2LongOpenHashMap<>();
    private long nextTargetId, nameRevision;
    private RenameState renameState;
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

    long nameRevision() {
        return nameRevision;
    }

    WirelessCardSnapshot build(EntityPlayer player, WirelessCardBinding binding, ItemStack card) {
        displayAddresses.clear();
        machines.clear();
        nextMachineIds = new Reference2LongOpenHashMap<>();
        if (refreshPresentation(player.worldObj.getTotalWorldTime()) && binding != null)
            WirelessMachineGroups.forgetRemovedGroupNames(binding.frequency());
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
        data.setBoolean("devicePage", devices);
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
        Long2ObjectLinkedOpenHashMap<Device> targets = new Long2ObjectLinkedOpenHashMap<>();
        if (focus != null) data.setLong("detailId", identify(focus, focus.node(), nextIds, targets));
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
        machineIds = nextMachineIds;
        data.setTag("rows", entries);
        data.setTag("dimensions", dimensions);
        return new WirelessCardSnapshot(data, targets, deviceTargets, machines, displayAddresses);
    }

    private long identify(Row group) {
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
        Target key = new Target(link.node(), link.members(), link instanceof IWirelessLinkView.EntranceView);
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
        entry.setString("position", coordinates(address));
        entry.setString(
            "name",
            link.deviceNames()
                .getOrDefault(node, ""));
        entry.setString("deviceState", device.state());
        long displayId = entry.getLong("displayId");
        displayAddresses.put(displayId, address);
        entry.setInteger("displayMode", modes.get(displayId));
        return entry;
    }

    private NBTTagCompound writeRow(EntityPlayer player, WirelessFrequency frequency, Row row,
        WirelessInterfaceView interfaces, NBTTagCompound dimensions, Long2IntMap modes, Object2LongMap<Target> nextIds,
        Long2ObjectMap<Device> targets, boolean detail) {
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
            if (WirelessMachineGroups.canName(frequency)) entry.setLong("machineId", identify(row));
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
            IGridNode remote = only == null ? link.node() : only;
            entry.setLong("targetId", identify(link, remote, nextIds, targets));
            entry.setBoolean("singleDevice", only != null);
            entry.setString(
                "remote",
                probe(player, frequency, remote).state()
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

    private record Presentation(Address address, NBTTagCompound data) {}

    private record RenameState(Set<IGridNode> members, Map<IGridNode, String> names) {}
}
