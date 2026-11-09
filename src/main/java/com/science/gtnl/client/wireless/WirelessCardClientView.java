package com.science.gtnl.client.wireless;

import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_DETAILS;
import static com.science.gtnl.common.wireless.WirelessCardActions.rowIndex;
import static com.science.gtnl.common.wireless.WirelessCardActions.type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.me.InterfaceNameLocalization;
import com.science.gtnl.common.wireless.WirelessCardBinding;
import com.science.gtnl.common.wireless.WirelessCardContainer;
import com.science.gtnl.common.wireless.WirelessCardLayout;
import com.science.gtnl.common.wireless.WirelessCardViewCodec;
import com.science.gtnl.common.wireless.WirelessFrequency;
import com.science.gtnl.compat.nech.NechSearchMatcher;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

@SideOnly(Side.CLIENT)
public class WirelessCardClientView {

    public static final int STATES = 6;
    private static final int MAX_ORDERS = 64;
    private static final int MAX_SAVED_NETWORKS = 32;
    private static final Map<NetHandlerPlayClient, Object2ObjectLinkedOpenHashMap<WirelessFrequency, State>> SAVED = new WeakHashMap<>();
    private final WirelessCardContainer container;
    private final NechSearchMatcher matcher = new NechSearchMatcher();
    private final Object2ObjectLinkedOpenHashMap<SortView, List<RowOrder>> orders = new Object2ObjectLinkedOpenHashMap<>(
        16);
    private NBTTagCompound source = new NBTTagCompound();
    private List<Row> roots = List.of();
    private List<Row> listing = List.of();
    private List<Shown> shown = List.of();
    private NBTTagCompound listingData = new NBTTagCompound();
    private final Set<String> expanded = new ObjectOpenHashSet<>();
    private final Long2IntOpenHashMap displayModes = new Long2IntOpenHashMap();
    private final Long2ObjectOpenHashMap<PendingMode> pendingModes = new Long2ObjectOpenHashMap<>();
    private String query = "";
    private Integer dimension;
    private int state, scroll;
    private int visibleRows = WirelessCardLayout.MIN_ROWS;
    private List<int[]> dimensions = List.of();
    private byte[] incoming;
    private long incomingRevision = -1;
    private int received;
    private long version;
    private final LongOpenHashSet targets = new LongOpenHashSet();

    public record State(String search, Integer dimension, int filter, Set<String> expanded, int scroll,
        DeviceView devices) {}

    public record DeviceView(String key, Set<String> expanded, int scroll) {}

    public record DeviceGroup(NBTTagCompound header, List<Section> sections) {}

    public record Section(NBTTagCompound row, List<NBTTagCompound> devices, int clusters) {}

    private record PendingMode(long sequence, int mode) {}

    public WirelessCardClientView(WirelessCardContainer container) {
        this.container = container;
    }

    public State save(String search, DeviceView devices) {
        return new State(search, dimension, state, Set.copyOf(expanded), scroll, devices);
    }

    public long version() {
        return version;
    }

    public boolean hasTarget(long id) {
        return targets.contains(id);
    }

    public State recall() {
        var saved = SAVED.get(
            Minecraft.getMinecraft()
                .getNetHandler());
        return saved == null || container.binding == null ? null
            : saved.getAndMoveToLast(container.binding.frequency());
    }

    public void remember(String search) {
        var connection = Minecraft.getMinecraft()
            .getNetHandler();
        if (connection == null || container.binding == null || !source.hasKey("session")) return;
        var saved = SAVED.computeIfAbsent(connection, ignored -> new Object2ObjectLinkedOpenHashMap<>());
        saved.putAndMoveToLast(container.binding.frequency(), save(search, null));
        if (saved.size() > MAX_SAVED_NETWORKS) saved.removeFirst();
    }

    public void restore(State saved) {
        query = saved.search()
            .strip()
            .toLowerCase(Locale.ROOT);
        dimension = saved.dimension();
        state = saved.filter() >= 0 && saved.filter() < STATES ? saved.filter() : 0;
        expanded.clear();
        expanded.addAll(saved.expanded());
        scroll = saved.scroll();
        rebuild();
    }

    public void acceptChunk(long revision, int total, int offset, byte[] chunk) {
        if (revision < incomingRevision) return;
        if (offset == 0) {
            incoming = new byte[total];
            incomingRevision = revision;
            received = 0;
        }
        if (incoming == null || revision != incomingRevision || incoming.length != total || offset != received) return;
        System.arraycopy(chunk, 0, incoming, offset, chunk.length);
        received += chunk.length;
        if (received != total) return;
        byte[] complete = incoming;
        incoming = null;
        try {
            accept(CompressedStreamTools.readCompressed(new ByteArrayInputStream(complete)));
        } catch (IOException failure) {
            ScienceNotLeisure.LOG.warn("Cannot decode wireless card view", failure);
        }
    }

    private void accept(NBTTagCompound data) {
        if (source.hasKey("session") && data.getBoolean("collapseTypes") != source.getBoolean("collapseTypes"))
            scroll = 0;
        WirelessCardViewCodec.localizeDimensions(data);
        source = data;
        displayModes.clear();
        long acknowledged = data.getLong("displayAck");
        pendingModes.values()
            .removeIf(pending -> pending.sequence() <= acknowledged);
        targets.clear();
        List<Row> next = new ObjectArrayList<>();
        NBTTagList rows = data.getTagList("rows", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            String prefix = row.getBoolean("group") || !row.getString("typeKey")
                .isEmpty() ? iconName(row) : "";
            next.add(readRow(row, prefix, row.getString("position")));
        }
        roots = List.copyOf(next);
        dimensions = countDimensions(roots);
        if (data.hasKey("highlight", 9)) {
            WirelessWorldHighlight.show(Minecraft.getMinecraft(), data.getTagList("highlight", 10));
            data.removeTag("highlight");
        }
        rebuild();
    }

    private Row readRow(NBTTagCompound data, String parentNames, String position) {
        WirelessCardViewCodec.localizePosition(data, "position", source.getCompoundTag("dimensions"));
        WirelessCardViewCodec.localizeInterfaceNames(data);
        readDisplayMode(data);
        for (String id : new String[] { "targetId", "machineId" })
            if (data.hasKey(id, 4)) targets.add(data.getLong(id));
        String prefix = parentNames + " " + data.getString("name");
        StringBuilder names = new StringBuilder(prefix);
        StringBuilder positions = new StringBuilder(position).append(' ')
            .append(positionWithSide(data));
        NBTTagList devices = data.getTagList("displayDevices", 10);
        for (int i = 0; i < devices.tagCount(); i++) {
            NBTTagCompound device = devices.getCompoundTagAt(i);
            readDisplayMode(device);
            if (device.hasKey("targetId", 4)) targets.add(device.getLong("targetId"));
            WirelessCardViewCodec.localizePosition(device, "position", source.getCompoundTag("dimensions"));
            WirelessCardViewCodec.localizeInterfaceNames(device);
            names.append(' ')
                .append(iconName(device));
            positions.append(' ')
                .append(positionWithSide(device));
        }
        NBTTagList entrances = data.getTagList("entranceList", 10);
        for (int i = 0; i < entrances.tagCount(); i++) WirelessCardViewCodec
            .localizePosition(entrances.getCompoundTagAt(i), "position", source.getCompoundTag("dimensions"));
        List<Row> children = new ObjectArrayList<>();
        NBTTagList entries = data.getTagList("children", 10);
        for (int i = 0; i < entries.tagCount(); i++)
            children.add(readRow(entries.getCompoundTagAt(i), prefix, position));
        NBTTagList customNames = data.getTagList("customNames", 8);
        for (int i = 0; i < customNames.tagCount(); i++) names.append(' ')
            .append(customNames.getStringTagAt(i));
        names.append(' ')
            .append(iconName(data));
        NBTTagList labels = data.getTagList("searchLabels", 10);
        for (int i = 0; i < labels.tagCount(); i++) {
            NBTTagCompound label = labels.getCompoundTagAt(i);
            names.append(' ')
                .append(label.getString("customName"))
                .append(' ')
                .append(label.getString("serverName"))
                .append(' ')
                .append(
                    InterfaceNameLocalization.localizeName(
                        label.getString("rawName"),
                        label.getString("suffix"),
                        ItemStack.loadItemStackFromNBT(label.getCompoundTag("icon"))));
        }
        NBTTagCompound display = copy(data);
        display.removeTag("children");
        display.removeTag("searchLabels");
        return new Row(
            display,
            List.copyOf(children),
            names.toString()
                .toLowerCase(Locale.ROOT),
            positions.toString()
                .toLowerCase(Locale.ROOT));
    }

    static String positionWithSide(NBTTagCompound row) {
        String side = row.getString("side");
        return row.getString("position") + (row.getBoolean("group") || side.isEmpty() ? "" : " / " + side);
    }

    static NBTTagCompound locationTarget(NBTTagCompound row) {
        return !row.getBoolean("group") && row.getBoolean("singleDevice") ? row.getTagList("displayDevices", 10)
            .getCompoundTagAt(0) : row;
    }

    private void readDisplayMode(NBTTagCompound data) {
        if (data.hasKey("displayId", 4)) displayModes.put(data.getLong("displayId"), data.getInteger("displayMode"));
    }

    private static String iconName(NBTTagCompound data) {
        ItemStack icon = ItemStack.loadItemStackFromNBT(data.getCompoundTag("icon"));
        return data.getString("serverIconName") + " " + (icon == null ? "" : icon.getDisplayName());
    }

    public void filter(String text, Integer dimension, int state) {
        if (text.length() > 64 || state < 0 || state >= STATES) return;
        String nextQuery = text.strip()
            .toLowerCase(Locale.ROOT);
        if (query.equals(nextQuery) && Objects.equals(this.dimension, dimension) && this.state == state) return;
        query = nextQuery;
        this.dimension = dimension;
        this.state = state;
        scroll = 0;
        rebuild();
    }

    public Integer dimension() {
        return dimension;
    }

    public int state() {
        return state;
    }

    public List<int[]> dimensions() {
        return dimensions;
    }

    public String dimensionName(int id) {
        return source.getCompoundTag("dimensions")
            .getString(Integer.toString(id));
    }

    public void resize(int rows) {
        if (!WirelessCardLayout.validRows(rows) || rows == visibleRows) return;
        visibleRows = rows;
        updateViewport(null);
    }

    public void scrollTo(int offset) {
        int next = Math.max(0, Math.min(offset, container.display.getInteger("maxScroll")));
        if (next == container.display.getInteger("scroll")) return;
        scroll = next;
        updateViewport(null);
    }

    public int displayMode(NBTTagCompound row) {
        if (!row.hasKey("displayId", 4)) return 0;
        long id = row.getLong("displayId");
        PendingMode pending = pendingModes.get(id);
        return pending == null ? displayModes.get(id) : pending.mode();
    }

    public boolean setDisplayMode(List<NBTTagCompound> devices, int mode) {
        if (mode < 0 || mode >= WirelessCardBinding.DISPLAY_MODES) return false;
        var ids = new LongLinkedOpenHashSet();
        for (NBTTagCompound device : devices) {
            if (!device.hasKey("displayId", 4)) continue;
            long id = device.getLong("displayId");
            ids.add(id);
        }
        if (mode > 0 && ids.size() > WirelessCardBinding.MAX_DISPLAY_ENTRIES) return false;
        if (ids.isEmpty()) return true;
        long sequence = container.sync.requestDisplay(ids.toLongArray(), mode);
        if (sequence == 0) return true;
        for (long id : ids) pendingModes.put(id, new PendingMode(sequence, mode));
        updateViewport(null);
        return true;
    }

    public void cycleDisplayMode(NBTTagCompound device) {
        setDisplayMode(List.of(device), (displayMode(device) + 1) % WirelessCardBinding.DISPLAY_MODES);
    }

    public DeviceGroup deviceGroup(String key) {
        Row group = findRow(listing, key);
        if (group == null) return null;
        List<Section> sections = new ObjectArrayList<>();
        if (group.children()
            .isEmpty()) sections.add(section(group));
        else {
            List<Row> children = new ObjectArrayList<>(group.children());
            order(children, key, source.getBoolean("collapseTypes"));
            for (Row child : children) sections.add(section(child));
        }
        return new DeviceGroup(group.data(), List.copyOf(sections));
    }

    private static Section section(Row row) {
        List<NBTTagCompound> devices = new ObjectArrayList<>();
        int clusters = collectDevices(row, devices);
        return new Section(row.data(), List.copyOf(devices), clusters);
    }

    private static Row findRow(List<Row> rows, String key) {
        for (Row row : rows) {
            if (key.equals(
                row.data()
                    .getString("key")))
                return row;
            Row child = findRow(row.children(), key);
            if (child != null) return child;
        }
        return null;
    }

    private static int collectDevices(Row row, List<NBTTagCompound> devices) {
        if (!row.children()
            .isEmpty()) {
            int clusters = 0;
            for (Row child : row.children()) clusters += collectDevices(child, devices);
            return clusters;
        }
        NBTTagList entries = row.data()
            .getTagList("displayDevices", 10);
        for (int i = 0; i < entries.tagCount(); i++) devices.add(entries.getCompoundTagAt(i));
        return entries.tagCount() > 0 ? 1 : 0;
    }

    public boolean navigate(int action) {
        if (type(action) != OPEN_DETAILS) return false;
        NBTTagList rows = container.display.getTagList("rows", 10);
        int index = rowIndex(action);
        if (index < 0 || index >= rows.tagCount()) return true;
        NBTTagCompound row = rows.getCompoundTagAt(index);
        if (!row.getBoolean("group")) return false;
        String key = row.getString("key"), revealGroup = null;
        if (!expanded.remove(key)) {
            expanded.add(key);
            revealGroup = key;
        }
        rebuild(revealGroup);
        return true;
    }

    public void rebuild() {
        rebuild(null);
    }

    private void rebuild(String revealGroup) {
        if (!source.hasKey("session")) return;
        buildListing();
        updateViewport(revealGroup);
    }

    private void updateViewport(String revealGroup) {
        if (!source.hasKey("session")) return;
        container.display = buildViewport(scroll, revealGroup);
        // The first snapshot can be empty while GT rebuilds machine ownership.
        if (!source.getBoolean("initializing")) scroll = container.display.getInteger("scroll");
    }

    private void buildListing() {
        version++;
        NBTTagCompound data = copy(source);
        List<Shown> shown = new ObjectArrayList<>();
        int[] counts = new int[STATES];
        List<Row> rows = new ObjectArrayList<>();
        for (Row row : roots) {
            for (Row leaf : row.children()
                .isEmpty() ? List.of(row) : row.children()) {
                if (!matchesPlace(leaf)) continue;
                for (int s = 0; s < STATES; s++) if (matchesState(leaf.data(), s)) counts[s]++;
            }
            Row matched = filtered(row);
            if (matched != null) rows.add(matched);
        }
        data.setIntArray("stateCounts", counts);
        if (!data.getBoolean("initializing")) {
            NBTTagCompound statistics = aggregate(new NBTTagCompound(), rows);
            for (String key : List.of("devices", "online", "missing", "stable"))
                data.setTag(key, statistics.getTag(key));
            data.setInteger(
                "total",
                rows.stream()
                    .mapToInt(
                        row -> row.data()
                            .getInteger("clusters"))
                    .sum());
        }
        boolean collapse = data.getBoolean("collapseTypes");
        Map<Integer, List<Row>> elsewhere = elsewhere(rows);
        if (collapse) rows = collapse(rows);
        order(rows, "", collapse);
        for (var entry : elsewhere.entrySet())
            rows.add(dimensionRow(entry.getKey(), collapse ? collapse(entry.getValue()) : entry.getValue()));
        listing = List.copyOf(rows);
        flatten(rows, 0, 0, collapse, shown);
        data.setInteger("rowCount", shown.size());
        data.setInteger("filterState", state);
        data.setBoolean("filtered", !query.isEmpty() || dimension != null || state != 0);
        // The full tree changes on snapshots, filters and expansion, not on scrollbar movement.
        data.removeTag("rows");
        listingData = data;
        this.shown = List.copyOf(shown);
    }

    private NBTTagCompound buildViewport(int requestedScroll, String revealGroup) {
        NBTTagCompound data = copy(listingData);
        int maxScroll = Math.max(0, shown.size() - visibleRows);
        int offset = Math.max(0, Math.min(requestedScroll, maxScroll));
        if (revealGroup != null) {
            for (int start = 0; start < shown.size(); start++) {
                Shown group = shown.get(start);
                if (!revealGroup.equals(
                    group.row()
                        .data()
                        .getString("key")))
                    continue;
                int end = start + 1;
                while (end < shown.size() && shown.get(end)
                    .depth() > group.depth()) end++;
                // Reveal the expanded subtree with minimal movement, keeping its header visible.
                offset = Math.min(start, Math.max(offset, end - visibleRows));
                break;
            }
        }
        data.setInteger("scroll", offset);
        data.setInteger("visibleRows", visibleRows);
        data.setInteger("maxScroll", maxScroll);
        NBTTagList entries = new NBTTagList();
        for (Shown entry : shown.subList(offset, Math.min(shown.size(), offset + visibleRows))) {
            NBTTagCompound row = copy(
                entry.row()
                    .data());
            if (row.getBoolean("category"))
                WirelessCardViewCodec.localizePosition(row, "position", data.getCompoundTag("dimensions"));
            row.setInteger("depth", entry.depth());
            row.setInteger("treeLines", entry.lines());
            row.setBoolean("treeLast", entry.last());
            row.setBoolean("expanded", row.getBoolean("group") && expanded.contains(row.getString("key")));
            row.setInteger("displayMode", displayMode(row));
            entries.appendTag(row);
        }
        data.setTag("rows", entries);
        return data;
    }

    private void flatten(List<Row> rows, int depth, int lines, boolean collapse, List<Shown> shown) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            boolean last = i == rows.size() - 1;
            shown.add(new Shown(row, depth, lines, last));
            String key = row.data()
                .getString("key");
            if (!row.data()
                .getBoolean("group") || !expanded.contains(key)) continue;
            List<Row> children = new ObjectArrayList<>(row.children());
            order(children, key, collapse);
            flatten(children, depth + 1, depth == 0 || last ? lines : lines | 1 << depth - 1, collapse, shown);
        }
    }

    private void order(List<Row> rows, String parent, boolean collapse) {
        var view = new SortView(parent, collapse, query, dimension, state);
        List<RowOrder> previous = orders.getAndMoveToLast(view);
        orders.putAndMoveToLast(view, sort(rows, previous == null ? List.of() : previous));
        if (orders.size() > MAX_ORDERS) orders.removeFirst();
    }

    private Row filtered(Row row) {
        if (!row.children()
            .isEmpty()) {
            List<Row> children = row.children()
                .stream()
                .filter(this::matches)
                .toList();
            return children.isEmpty() ? null
                : new Row(aggregate(row.data(), children), children, row.names(), row.positions());
        }
        return matches(row) ? row : null;
    }

    private boolean matches(Row row) {
        return matchesState(row.data(), state) && matchesPlace(row);
    }

    private boolean matchesPlace(Row row) {
        if (dimension != null && dimension != row.data()
            .getInteger("dimension")) return false;
        return query.isEmpty() || (row.names() + " " + row.positions()).contains(query)
            || matcher.contains(row.names(), query);
    }

    private static boolean matchesState(NBTTagCompound data, int state) {
        boolean paused = data.getBoolean("paused");
        return switch (state) {
            case 1 -> paused;
            case 2 -> data.getBoolean("conflicted");
            case 3 -> !paused && data.getString("state")
                .equals("active");
            case 4 -> !paused && data.getString("state")
                .equals("waiting");
            case 5 -> !paused && data.getInteger("missing") > 0;
            default -> true;
        };
    }

    private static List<int[]> countDimensions(List<Row> rows) {
        Map<Integer, int[]> counts = new Int2ObjectAVLTreeMap<>();
        for (Row row : rows) for (Row leaf : row.children()
            .isEmpty() ? List.of(row) : row.children()) {
                int id = leaf.data()
                    .getInteger("dimension");
                counts.computeIfAbsent(id, key -> new int[] { key, 0 })[1]++;
            }
        return List.copyOf(counts.values());
    }

    private static List<Row> collapse(List<Row> rows) {
        List<Row> result = new ObjectArrayList<>();
        Map<String, List<Row>> types = new Object2ObjectLinkedOpenHashMap<>();
        for (Row row : rows) {
            String key = row.data()
                .getString("typeKey");
            if (key.isEmpty()) result.add(row);
            else types.computeIfAbsent(key, ignored -> new ObjectArrayList<>())
                .add(row);
        }
        types.forEach((key, children) -> {
            children.sort(
                Comparator.comparing(
                    row -> row.data()
                        .getString("sortKey")));
            if (children.size() == 1) result.add(children.get(0));
            else {
                NBTTagCompound category = aggregate(
                    children.get(0)
                        .data(),
                    children);
                category.setBoolean("group", true);
                category.setBoolean("category", true);
                category.setString("key", "type:" + key);
                category.setString("sortKey", key);
                category.setString("name", "");
                category.setTag("icon", category.getCompoundTag("typeIcon"));
                category.removeTag("targetName");
                category.setString("position", "D" + category.getInteger("dimension"));
                category.setInteger("items", children.size());
                category.removeTag("targetId");
                category.removeTag("machineId");
                category.removeTag("energyHatches");
                result.add(new Row(category, List.copyOf(children), "", ""));
            }
        });
        return result;
    }

    private Map<Integer, List<Row>> elsewhere(List<Row> rows) {
        Map<Integer, List<Row>> elsewhere = new Int2ObjectAVLTreeMap<>();
        if (dimension != null || rows.stream()
            .mapToInt(
                row -> row.data()
                    .getInteger("dimension"))
            .distinct()
            .count() < 2) return elsewhere;
        int here = container.player.dimension;
        var iterator = rows.iterator();
        while (iterator.hasNext()) {
            Row row = iterator.next();
            int id = row.data()
                .getInteger("dimension");
            if (id == here) continue;
            elsewhere.computeIfAbsent(id, ignored -> new ObjectArrayList<>())
                .add(row);
            iterator.remove();
        }
        return elsewhere;
    }

    private Row dimensionRow(int id, List<Row> children) {
        NBTTagCompound data = aggregate(new NBTTagCompound(), children);
        String name = dimensionName(id);
        data.setInteger("dimension", id);
        data.setBoolean("group", true);
        data.setBoolean("category", true);
        data.setBoolean("dimensionGroup", true);
        data.setString(
            "dimensionKey",
            source.getCompoundTag("dimensionKeys")
                .getString(Integer.toString(id)));
        data.setString("key", "dimension:" + id);
        data.setString("sortKey", "");
        data.setString("name", name.isEmpty() ? "D" + id : name);
        data.setString("position", "D" + id);
        data.setInteger("items", children.size());
        return new Row(data, List.copyOf(children), "", "");
    }

    private static NBTTagCompound aggregate(NBTTagCompound template, List<Row> rows) {
        NBTTagCompound data = copy(template);
        data.removeTag("entranceList");
        int devices = 0, online = 0, missing = 0, entrances = 0, clusters = 0, groupItems = 0;
        boolean stable = true, paused = true, conflict = false, limited = false, active = true;
        for (Row row : rows) {
            NBTTagCompound child = row.data();
            if (child.getBoolean("group") || !child.getBoolean("singleDevice")) groupItems++;
            devices += child.getInteger("devices");
            online += child.getInteger("online");
            missing += child.getInteger("missing");
            entrances += child.getInteger("entrances");
            clusters += child.getInteger("clusters");
            stable &= child.getBoolean("stable");
            paused &= child.getBoolean("paused");
            conflict |= child.getBoolean("conflicted");
            limited |= child.getString("state")
                .equals("limited");
            active &= child.getString("state")
                .equals("active");
        }
        data.setInteger("devices", devices);
        data.setInteger("online", online);
        data.setInteger("missing", missing);
        data.setInteger("entrances", entrances);
        data.setInteger("clusters", clusters);
        data.setInteger("groupItems", groupItems);
        data.setBoolean("stable", stable);
        data.setBoolean("paused", paused);
        data.setBoolean("conflicted", conflict);
        data.setString(
            "state",
            conflict ? "conflict" : limited ? "limited" : paused ? "paused" : active ? "active" : "waiting");
        return data;
    }

    private static List<RowOrder> sort(List<Row> rows, List<RowOrder> previous) {
        rows.sort(
            Comparator.comparing(
                (Row row) -> row.data()
                    .getBoolean("paused"))
                .thenComparing(
                    row -> !row.data()
                        .getBoolean("group"))
                .thenComparingInt(
                    row -> row.data()
                        .getBoolean("stable")
                        && row.data()
                            .getInteger("missing") > 0 ? 0 : 1)
                .thenComparing(
                    Comparator.comparingInt(
                        (Row row) -> row.data()
                            .getInteger("online"))
                        .reversed())
                .thenComparing(
                    row -> row.data()
                        .getString("sortKey")));
        boolean[] pinned = new boolean[rows.size()];
        if (!previous.isEmpty()) {
            Map<String, Row> remaining = new Object2ObjectLinkedOpenHashMap<>();
            for (Row row : rows) remaining.put(
                row.data()
                    .getString("key"),
                row);
            Row[] ordered = new Row[rows.size()];
            int lastSlot = ordered.length - 1;
            for (int i = previous.size() - 1; i >= 0; i--) {
                RowOrder old = previous.get(i);
                Row row = remaining.get(old.key());
                if (row == null || !row.data()
                    .getBoolean("paused") || old.paused() && !old.pinned()) continue;
                int slot = Math.min(i, lastSlot);
                ordered[slot] = row;
                pinned[slot] = true;
                lastSlot = slot - 1;
                remaining.remove(old.key());
            }
            var moving = remaining.values()
                .iterator();
            for (int i = 0; i < ordered.length; i++) rows.set(i, ordered[i] == null ? moving.next() : ordered[i]);
        }
        List<RowOrder> order = new ObjectArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            NBTTagCompound row = rows.get(i)
                .data();
            order.add(new RowOrder(row.getString("key"), row.getBoolean("paused"), pinned[i]));
        }
        return order;
    }

    private static NBTTagCompound copy(NBTTagCompound source) {
        NBTTagCompound copy = new NBTTagCompound();
        for (Object key : source.func_150296_c()) copy.setTag((String) key, source.getTag((String) key));
        return copy;
    }

    private record Row(NBTTagCompound data, List<Row> children, String names, String positions) {}

    private record RowOrder(String key, boolean paused, boolean pinned) {}

    private record SortView(String parent, boolean collapse, String query, Integer dimension, int state) {}

    private record Shown(Row row, int depth, int lines, boolean last) {}

}
