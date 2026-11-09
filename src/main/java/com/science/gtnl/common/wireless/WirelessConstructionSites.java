package com.science.gtnl.common.wireless;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.science.gtnl.common.wireless.WirelessChannelManager.Address;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

// Bulk tools such as Matter Manipulator place a structure over many ticks; its fragments must not open entrances.
public class WirelessConstructionSites {

    public static final int QUIET_TICKS = 100;
    private static final Map<Object, Activity> ACTIVE = new Reference2ObjectOpenHashMap<>();
    private static long ticks;

    private record Activity(List<Site> sites, long lastTick, Runnable finished) {}

    public record Site(int dimension, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

        public static Site of(int dimension, int x, int y, int z) {
            return new Site(dimension, x, y, z, x, y, z);
        }

        public Site include(int x, int y, int z) {
            if (contains(dimension, x, y, z)) return this;
            return new Site(
                dimension,
                Math.min(minX, x),
                Math.min(minY, y),
                Math.min(minZ, z),
                Math.max(maxX, x),
                Math.max(maxY, y),
                Math.max(maxZ, z));
        }

        public Site offset(int dx, int dy, int dz) {
            return new Site(dimension, minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
        }

        public boolean contains(int dimension, int x, int y, int z) {
            return this.dimension == dimension && x >= minX
                && x <= maxX
                && y >= minY
                && y <= maxY
                && z >= minZ
                && z <= maxZ;
        }
    }

    public static void update(Object task, boolean building, Site... sites) {
        update(task, building, (Runnable) null, sites);
    }

    public static void update(Object task, boolean building, Runnable finished, Site... sites) {
        if (task == null) return;
        List<Site> regions = Arrays.stream(sites)
            .filter(Objects::nonNull)
            .toList();
        if (building && !regions.isEmpty()) ACTIVE.put(task, new Activity(regions, ticks, finished));
        else {
            Activity ended = ACTIVE.remove(task);
            if (ended != null) finish(List.of(ended));
            else if (finished != null) finished.run();
        }
    }

    public static Runnable once(Runnable action) {
        boolean[] done = { false };
        return () -> {
            if (done[0]) return;
            done[0] = true;
            action.run();
        };
    }

    private static void finish(List<Activity> ended) {
        if (ended.isEmpty()) return;
        WirelessClusterManager.invalidateTopology();
        for (Activity activity : ended) if (activity.finished() != null) activity.finished()
            .run();
    }

    public static boolean contains(Address address) {
        if (address == null || ACTIVE.isEmpty()) return false;
        for (Activity activity : ACTIVE.values()) {
            for (Site site : activity.sites()) {
                if (site.contains(address.dimension(), address.x(), address.y(), address.z())) return true;
            }
        }
        return false;
    }

    static boolean any() {
        return !ACTIVE.isEmpty();
    }

    static void tick() {
        ticks++;
        List<Activity> quiet = new ObjectArrayList<>();
        ACTIVE.values()
            .removeIf(activity -> ticks - activity.lastTick() > QUIET_TICKS && quiet.add(activity));
        finish(quiet);
    }

    static void clear() {
        ACTIVE.clear();
        ticks = 0;
    }
}
