package com.science.gtnl.client.wireless;

import static com.science.gtnl.common.wireless.WirelessCardActions.NEXT_PAGE;
import static com.science.gtnl.common.wireless.WirelessCardActions.OPEN_DETAILS;
import static com.science.gtnl.common.wireless.WirelessCardActions.PREVIOUS_PAGE;
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

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.me.InterfaceNameLocalization;
import com.science.gtnl.common.wireless.WirelessCardContainer;
import com.science.gtnl.common.wireless.WirelessCardLayout;
import com.science.gtnl.common.wireless.WirelessCardViewCodec;
import com.science.gtnl.compat.nech.NechSearchMatcher;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

@SideOnly(Side.CLIENT)
public class WirelessCardClientView {

    public static final int STATES = 6;
    private static final int MAX_ORDERS = 64;
    private final WirelessCardContainer container;
    private final NechSearchMatcher matcher = new NechSearchMatcher();
    private final Object2ObjectLinkedOpenHashMap<SortView, List<RowOrder>> orders = new Object2ObjectLinkedOpenHashMap<>(
        16);
    private NBTTagCompound source = new NBTTagCompound();
    private List<Row> roots = List.of();
    private final Set<String> expanded = new ObjectOpenHashSet<>();
    private String query = "";
    private Integer dimension;
    private int state, page, detailPage;
    private int pageSize = WirelessCardLayout.MIN_ROWS;
    private List<int[]> dimensions = List.of();
    private byte[] incoming;
    private long incomingRevision = -1;
    private int received;

    public WirelessCardClientView(WirelessCardContainer container) {
        this.container = container;
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
        if (data.getBoolean("detail") && (!source.getBoolean("detail") || !data.getString("detailTarget")
            .equals(source.getString("detailTarget")))) detailPage = 0;
        if (source.hasKey("session") && data.getBoolean("collapseTypes") != source.getBoolean("collapseTypes"))
            page = 0;
        WirelessCardViewCodec.localizeDimensions(data);
        source = data;
        List<Row> next = new ObjectArrayList<>();
        NBTTagList rows = data.getTagList("rows", 10);
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            String prefix = row.getBoolean("group") || !row.getString("typeKey")
                .isEmpty() ? iconName(row, "icon") : "";
            next.add(readRow(row, prefix, row.getString("position")));
        }
        roots = List.copyOf(next);
        if (!data.getBoolean("detail")) dimensions = countDimensions(roots);
        if (data.hasKey("highlight", 9)) {
            WirelessWorldHighlight.show(Minecraft.getMinecraft(), data.getTagList("highlight", 10));
            data.removeTag("highlight");
        }
        rebuild();
    }

    private Row readRow(NBTTagCompound data, String prefix, String position) {
        WirelessCardViewCodec.localizePosition(data, "position", source.getCompoundTag("dimensions"));
        WirelessCardViewCodec.localizeInterfaceNames(data);
        List<Row> children = new ObjectArrayList<>();
        NBTTagList entries = data.getTagList("children", 10);
        for (int i = 0; i < entries.tagCount(); i++)
            children.add(readRow(entries.getCompoundTagAt(i), prefix, position));
        StringBuilder names = new StringBuilder(prefix);
        NBTTagList customNames = data.getTagList("customNames", 8);
        for (int i = 0; i < customNames.tagCount(); i++) names.append(' ')
            .append(customNames.getStringTagAt(i));
        names.append(' ')
            .append(iconName(data, "searchIcon"));
        NBTTagList labels = data.getTagList("searchLabels", 10);
        for (int i = 0; i < labels.tagCount(); i++) {
            NBTTagCompound label = labels.getCompoundTagAt(i);
            names.append(' ')
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
            (position + " " + data.getString("position")).toLowerCase(Locale.ROOT));
    }

    private static String iconName(NBTTagCompound data, String key) {
        ItemStack icon = ItemStack.loadItemStackFromNBT(data.getCompoundTag(key));
        return data.getString(key.equals("searchIcon") ? "serverDeviceName" : "serverIconName") + " "
            + (icon == null ? "" : icon.getDisplayName());
    }

    public void filter(String text, Integer dimension, int state) {
        if (text.length() > 64 || state < 0 || state >= STATES) return;
        String nextQuery = text.strip()
            .toLowerCase(Locale.ROOT);
        if (query.equals(nextQuery) && Objects.equals(this.dimension, dimension) && this.state == state) return;
        query = nextQuery;
        this.dimension = dimension;
        this.state = state;
        page = 0;
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
        if (!WirelessCardLayout.validRows(rows) || rows == pageSize) return;
        page = page * pageSize / rows;
        detailPage = detailPage * pageSize / rows;
        pageSize = rows;
        rebuild();
    }

    public boolean navigate(int action) {
        boolean detail = source.getBoolean("detail");
        if (action == PREVIOUS_PAGE || action == NEXT_PAGE) {
            int change = action == NEXT_PAGE ? 1 : -1;
            if (detail) detailPage += change;
            else page += change;
        } else if (type(action) == OPEN_DETAILS && !detail) {
            NBTTagList rows = container.display.getTagList("rows", 10);
            int index = rowIndex(action);
            if (index < 0 || index >= rows.tagCount()) return true;
            NBTTagCompound row = rows.getCompoundTagAt(index);
            if (!row.getBoolean("group")) return false;
            String key = row.getString("key");
            if (!expanded.remove(key)) expanded.add(key);
        } else return false;
        rebuild();
        return true;
    }

    public void rebuild() {
        if (!source.hasKey("session")) return;
        boolean detail = source.getBoolean("detail");
        container.display = buildListing(detail ? detailPage : page);
        if (detail) detailPage = container.display.getInteger("page");
        else page = container.display.getInteger("page");
    }

    private NBTTagCompound buildListing(int requestedPage) {
        boolean detail = source.getBoolean("detail");
        NBTTagCompound data = copy(source);
        List<Shown> shown = new ObjectArrayList<>();
        if (detail) {
            for (Row row : roots) shown.add(new Shown(row, 0, 0, false));
        } else {
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
            if (data.getBoolean("collapseTypes")) rows = collapse(rows);
            order(rows, "", data.getBoolean("collapseTypes"));
            flatten(rows, 0, 0, data.getBoolean("collapseTypes"), shown);
        }
        data.setInteger("rowCount", shown.size());
        int pages = Math.max(1, (shown.size() + pageSize - 1) / pageSize);
        int currentPage = Math.max(0, Math.min(requestedPage, pages - 1));
        data.setInteger("page", currentPage);
        data.setInteger("pageSize", pageSize);
        data.setInteger("pages", pages);
        data.setInteger("filterState", state);
        data.setBoolean("filtered", !query.isEmpty() || dimension != null || state != 0);
        NBTTagList entries = new NBTTagList();
        for (Shown entry : shown
            .subList(currentPage * pageSize, Math.min(shown.size(), (currentPage + 1) * pageSize))) {
            NBTTagCompound row = copy(
                entry.row()
                    .data());
            if (row.getBoolean("category"))
                WirelessCardViewCodec.localizePosition(row, "position", data.getCompoundTag("dimensions"));
            row.setInteger("depth", entry.depth());
            row.setInteger("treeLines", entry.lines());
            row.setBoolean("treeLast", entry.last());
            row.setBoolean("expanded", row.getBoolean("group") && expanded.contains(row.getString("key")));
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
            case 5 -> !paused && data.getBoolean("stable") && data.getInteger("missing") > 0;
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
                result.add(new Row(category, List.copyOf(children), "", ""));
            }
        });
        return result;
    }

    private static NBTTagCompound aggregate(NBTTagCompound template, List<Row> rows) {
        NBTTagCompound data = copy(template);
        int devices = 0, online = 0, missing = 0, entrances = 0, clusters = 0;
        boolean stable = true, paused = true, conflict = false, limited = false, active = true;
        for (Row row : rows) {
            NBTTagCompound child = row.data();
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
