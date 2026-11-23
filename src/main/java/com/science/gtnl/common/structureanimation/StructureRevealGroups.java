package com.science.gtnl.common.structureanimation;

import java.util.Arrays;

public class StructureRevealGroups {

    public static final int MAX_SEEDS = 360, CELLS_PER_GROUP = 500, MAX_GROUPS = 32, CELLS_PER_SEED = 25;

    private StructureRevealGroups() {}

    // At least the count the height asks for, and more for structures with many blocks, so the server finishes each
    // piece in about the same time.
    public static int groupCount(int height, int cells) {
        int byHeight = height <= 6 ? Math.max(3, height) : height <= 30 ? 5 : height <= 64 ? 6 : 8;
        return Math.max(byHeight, Math.min(MAX_GROUPS, (cells + CELLS_PER_GROUP - 1) / CELLS_PER_GROUP));
    }

    public static int hash(int a, int b, int c) {
        int h = (a + 101) * 374761393 ^ (b + 57) * 668265263 ^ (c + 13) * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    // Integer-only, and the cells must come sorted by y, z, x relative to the structure's lowest corner, so the server
    // and every client split the structure into the same pieces. Every cell joins its nearest seed; the seeds are then
    // dealt out from the bottom up so that all pieces hold about the same number of cells.
    public static byte[] assign(int[] cx, int[] cy, int[] cz, int sizeY) {
        int n = cx.length, groups = groupCount(sizeY, n);
        byte[] groupOf = new byte[n];
        if (n == 0) return groupOf;
        int seeds = Math.max(groups * 3, Math.min(MAX_SEEDS, n / CELLS_PER_SEED));
        int[] sx = new int[seeds], sy = new int[seeds], sz = new int[seeds], held = new int[seeds];
        long[] rank = new long[seeds];
        for (int s = 0; s < seeds; s++) {
            int i = Integer.remainderUnsigned(hash(s, 7, 31), n);
            int jitter = Integer.remainderUnsigned(hash(s, 3, 11), 9) - 4;
            sx[s] = cx[i];
            sy[s] = cy[i];
            sz[s] = cz[i];
            rank[s] = (long) (cy[i] * 10 + jitter + 4) << 32 | s;
        }
        int[] seedOf = new int[n];
        for (int i = 0; i < n; i++) {
            long best = Long.MAX_VALUE;
            int nearest = 0;
            for (int s = 0; s < seeds; s++) {
                long dx = cx[i] - sx[s], dy = cy[i] - sy[s], dz = cz[i] - sz[s];
                long d = 25 * dx * dx + 49 * dy * dy + 25 * dz * dz;
                if (d < best) {
                    best = d;
                    nearest = s;
                }
            }
            seedOf[i] = nearest;
            held[nearest]++;
        }
        Arrays.sort(rank);
        byte[] seedGroup = new byte[seeds];
        long before = 0;
        for (long entry : rank) {
            int s = (int) entry;
            seedGroup[s] = (byte) Math.min(groups - 1, (before + held[s] / 2) * groups / n);
            before += held[s];
        }
        for (int i = 0; i < n; i++) groupOf[i] = seedGroup[seedOf[i]];
        return groupOf;
    }

    // Placement order that finishes one piece before starting the next, so pieces can be revealed one by one.
    public static int[] order(byte[] groupOf, int groups) {
        int[] next = new int[groups + 1];
        for (byte group : groupOf) next[group + 1]++;
        for (int g = 0; g < groups; g++) next[g + 1] += next[g];
        int[] order = new int[groupOf.length];
        for (int i = 0; i < groupOf.length; i++) order[next[groupOf[i]]++] = i;
        return order;
    }
}
