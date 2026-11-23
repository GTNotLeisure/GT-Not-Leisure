package com.science.gtnl.common.structureanimation;

public class StructureFlightPlan {

    public static final int MAX_BANDS = 8, BLOCKS_PER_BAND = 4, APPEAR = 10, HOVER = 9, SNAP = 7, LATCH = 11,
        LEAD_MIN = 14, LEAD_SMALL = 8, LEAD_MAX = 60, LEAD_CELLS = 2500, MIN_FLIGHT = 24, MAX_FLIGHT = 70,
        LAUNCH_DISTANCE = 32, LAUNCH_HEIGHT = 18, MIN_SPLIT = 3, LOCK_GAP = 3;
    public static final double SPEED = 1.2, PATH_STRETCH = 1.25, SIDE_SPREAD = 1.5, MAX_HOVER = 6;

    public record Timing(short[] flights, short[] launches, int total) {}

    public record Partition(int axis, int bands, byte[] segment, int[][] bounds) {

        public boolean empty(int band) {
            return bounds[band][3] < bounds[band][0];
        }

        public int used() {
            int used = 0;
            for (int band = 0; band < bands; band++) if (!empty(band)) used++;
            return used;
        }

        public int band(int cell) {
            return segment[cell] / BLOCKS_PER_BAND;
        }

        public int block(int cell) {
            return segment[cell] % BLOCKS_PER_BAND;
        }

        public double maxEdge(int band) {
            int[] b = bounds[band];
            return Math.max(b[3] - b[0] + 1, Math.max(b[4] - b[1] + 1, b[5] - b[2] + 1));
        }

        public double hover(int band) {
            return Math.min(MAX_HOVER, 1.5 + 0.15 * maxEdge(band));
        }

        public double[] anchor(int band) {
            int[] b = bounds[band];
            return new double[] { (b[0] + b[3] + 1) / 2.0, b[1], (b[2] + b[5] + 1) / 2.0 };
        }

        public double[] launch(int band, int launchX, int launchY, int launchZ, double centerX, double centerZ) {
            double dx = launchX - centerX, dz = launchZ - centerZ, length = Math.max(1.0E-6, Math.hypot(dx, dz));
            double side = (band - (bands - 1) / 2.0) * SIDE_SPREAD;
            return new double[] { launchX - dz / length * side, launchY + band, launchZ + dx / length * side };
        }
    }

    private StructureFlightPlan() {}

    // Integers only, so the server and every client split the same cells the same way.
    public static Partition partition(int[] xs, int[] ys, int[] zs, int minX, int minY, int minZ, int sizeX, int sizeY,
        int sizeZ, int launchX, int launchZ) {
        int axis = sizeY * 2 >= Math.max(sizeX, sizeZ) ? 1 : sizeX >= sizeZ ? 0 : 2;
        int[] min = { minX, minY, minZ }, size = { sizeX, sizeY, sizeZ };
        int length = size[axis];
        int band = Math.max(2, (length + 7) / 8);
        int bands = Math.max(1, Math.min(Math.min(MAX_BANDS, length), (length + band / 2) / band));
        int[] bandAt = new int[length];
        for (int i = 0; i < bands; i++)
            for (int o = i * length / bands; o < (i + 1) * length / bands; o++) bandAt[o] = i;
        int launch = axis == 0 ? launchX : launchZ;
        boolean reverse = axis != 1 && 2 * launch < 2 * min[axis] + size[axis] - 1;
        int splitA = axis == 0 ? 2 : 0, splitB = axis == 1 ? 2 : -1;
        boolean useA = size[splitA] >= MIN_SPLIT, useB = splitB >= 0 && size[splitB] >= MIN_SPLIT;
        byte[] segment = new byte[xs.length];
        int[][] bounds = new int[bands][];
        for (int cell = 0; cell < xs.length; cell++) {
            int[] c = { xs[cell], ys[cell], zs[cell] };
            int index = bandAt[c[axis] - min[axis]];
            if (reverse) index = bands - 1 - index;
            int block = (useA && 2 * c[splitA] >= 2 * min[splitA] + size[splitA] - 1 ? 1 : 0)
                + (useB && 2 * c[splitB] >= 2 * min[splitB] + size[splitB] - 1 ? 2 : 0);
            segment[cell] = (byte) (index * BLOCKS_PER_BAND + block);
            int[] b = bounds[index];
            if (b == null) bounds[index] = new int[] { c[0], c[1], c[2], c[0], c[1], c[2] };
            else for (int k = 0; k < 3; k++) {
                b[k] = Math.min(b[k], c[k]);
                b[k + 3] = Math.max(b[k + 3], c[k]);
            }
        }
        for (int i = 0; i < bands; i++) if (bounds[i] == null) bounds[i] = new int[] { 0, 0, 0, -1, -1, -1 };
        return new Partition(axis, bands, segment, bounds);
    }

    public static int lead(int cells, int bands) {
        int floor = bands <= 2 ? LEAD_SMALL : LEAD_MIN;
        return Math.max(floor, Math.min(LEAD_MAX, floor + cells / LEAD_CELLS));
    }

    public static int stagger(int bands) {
        return bands <= 2 ? 5 : bands <= 4 ? 8 : 6;
    }

    public static int arrival(int flight) {
        return APPEAR + flight + HOVER + SNAP + LATCH;
    }

    // Launches are staggered, then pushed back where needed so every band locks LOCK_GAP ticks after the one before.
    // Empty bands get no flight and take no slot.
    public static Timing timing(Partition partition, int cells, int launchX, int launchY, int launchZ, double centerX,
        double centerZ) {
        short[] flights = flights(partition, launchX, launchY, launchZ, centerX, centerZ);
        short[] launches = new short[flights.length];
        int used = partition.used(), lead = lead(cells, used), stagger = stagger(used), rank = 0, previous = -1;
        for (int band = 0; band < flights.length; band++) {
            if (flights[band] == 0) continue;
            int arrival = arrival(flights[band]);
            int launch = lead + rank++ * stagger;
            if (previous >= 0) launch = Math.max(launch, previous + LOCK_GAP - arrival);
            launches[band] = (short) launch;
            previous = launch + arrival;
        }
        return new Timing(flights, launches, total(flights, launches));
    }

    public static int total(short[] flights, short[] launches) {
        int total = 0;
        for (int band = 0; band < flights.length; band++)
            if (flights[band] > 0) total = Math.max(total, launches[band] + arrival(flights[band]));
        return total;
    }

    public static short[] flights(Partition partition, int launchX, int launchY, int launchZ, double centerX,
        double centerZ) {
        short[] flights = new short[partition.bands()];
        for (int band = 0; band < flights.length; band++) {
            if (partition.empty(band)) continue;
            double[] start = partition.launch(band, launchX, launchY, launchZ, centerX, centerZ);
            double[] anchor = partition.anchor(band);
            double dx = start[0] - anchor[0], dy = start[1] - anchor[1] - partition.hover(band),
                dz = start[2] - anchor[2];
            long ticks = Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz) * PATH_STRETCH / SPEED);
            flights[band] = (short) Math.max(MIN_FLIGHT, Math.min(MAX_FLIGHT, ticks));
        }
        return flights;
    }

    public static int[] launchPoint(int minX, int minZ, int sizeX, int sizeZ, int maxY, double builderX,
        double builderZ, double lookX, double lookZ) {
        double centerX = minX + sizeX / 2.0, centerZ = minZ + sizeZ / 2.0;
        double dx = builderX - centerX, dz = builderZ - centerZ;
        if (Math.hypot(dx, dz) < 1) {
            dx = lookX;
            dz = lookZ;
        }
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-6) {
            dx = 0;
            dz = 1;
            length = 1;
        }
        dx /= length;
        dz /= length;
        double edge = Math
            .min(sizeX / 2.0 / Math.max(Math.abs(dx), 1.0E-3), sizeZ / 2.0 / Math.max(Math.abs(dz), 1.0E-3));
        double distance = edge + LAUNCH_DISTANCE;
        return new int[] { (int) Math.round(centerX + dx * distance), maxY + 1 + LAUNCH_HEIGHT,
            (int) Math.round(centerZ + dz * distance) };
    }
}
