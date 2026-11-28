package com.science.gtnl.client.structureanimation;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.science.gtnl.common.structureanimation.StructureRevealGroups;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.floats.FloatArrayList;

@SideOnly(Side.CLIENT)
public class StructureRevealShape {

    private static final int[][] DIRS = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 }, { 0, 0, 1 },
        { 0, 0, -1 } };
    private static final int[][][] CORNERS = { { { 1, 0, 0 }, { 1, 1, 0 }, { 1, 1, 1 }, { 1, 0, 1 } },
        { { 0, 0, 1 }, { 0, 1, 1 }, { 0, 1, 0 }, { 0, 0, 0 } }, { { 0, 1, 0 }, { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 } },
        { { 0, 0, 1 }, { 0, 0, 0 }, { 1, 0, 0 }, { 1, 0, 1 } }, { { 1, 0, 1 }, { 1, 1, 1 }, { 0, 1, 1 }, { 0, 0, 1 } },
        { { 0, 0, 0 }, { 0, 1, 0 }, { 1, 1, 0 }, { 1, 0, 0 } } };

    public final int groups, cells;
    public final int[] last;
    public final float fadeTotal;
    public final FloatArrayList[] shells;
    public final FloatArrayList outline = new FloatArrayList();
    private final int sizeX, sizeY, sizeZ;
    private final short[] kinds;
    private final byte[] groupOf;

    public StructureRevealShape(int sizeX, int sizeY, int sizeZ, byte[] deflated) throws DataFormatException {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        kinds = inflate(deflated, sizeX * sizeY * sizeZ);
        int count = 0;
        for (short kind : kinds) if (kind != 0) count++;
        cells = count;
        int[] cx = new int[count], cy = new int[count], cz = new int[count];
        int n = 0;
        for (int y = 0; y < sizeY; y++) for (int z = 0; z < sizeZ; z++) for (int x = 0; x < sizeX; x++) {
            if (kinds[index(x, y, z)] == 0) continue;
            cx[n] = x;
            cy[n] = y;
            cz[n++] = z;
        }
        groups = StructureRevealGroups.groupCount(sizeY, count);
        fadeTotal = fadeTotal(count);
        byte[] cellGroups = StructureRevealGroups.assign(cx, cy, cz, sizeY);
        // The server places piece after piece, so a piece is complete at the running total of the cells up to it.
        last = new int[groups];
        groupOf = new byte[kinds.length];
        for (int i = 0; i < count; i++) {
            groupOf[index(cx[i], cy[i], cz[i])] = cellGroups[i];
            last[cellGroups[i]]++;
        }
        for (int g = 0, total = 0; g < groups; g++) {
            total += last[g];
            last[g] = total - 1;
        }
        shells = new FloatArrayList[groups];
        for (int g = 0; g < groups; g++) shells[g] = new FloatArrayList();
        for (int i = 0; i < count; i++) addFaces(cx[i], cy[i], cz[i], groupOf);
        outline();
    }

    public static float fadeTotal(int cells) {
        if (cells <= 100) return 1.5f;
        if (cells <= 1000) return 2.7f;
        if (cells <= 10000) return 3.9f;
        if (cells <= 100000) return 5.1f;
        return 6f;
    }

    private int index(int x, int y, int z) {
        return (y * sizeZ + z) * sizeX + x;
    }

    // The piece a cell of the box belongs to, or -1 where nothing is built.
    public int groupAt(int x, int y, int z) {
        return kindAt(x, y, z) == 0 ? -1 : groupOf[index(x, y, z)];
    }

    private int kindAt(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return 0;
        return kinds[index(x, y, z)];
    }

    public static short[] inflate(byte[] deflated, int volume) throws DataFormatException {
        byte[] raw = new byte[volume * 2];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(deflated);
            int read = 0;
            while (read < raw.length && !inflater.finished()) {
                int got = inflater.inflate(raw, read, raw.length - read);
                if (got == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                read += got;
            }
            if (read != raw.length) throw new DataFormatException("Projector build shape is truncated");
        } finally {
            inflater.end();
        }
        short[] kinds = new short[volume];
        for (int i = 0; i < volume; i++) kinds[i] = (short) (((raw[i * 2] & 0xFF) << 8) | (raw[i * 2 + 1] & 0xFF));
        return kinds;
    }

    private void addFaces(int x, int y, int z, byte[] groupOf) {
        int g = groupOf[index(x, y, z)];
        for (int d = 0; d < 6; d++) {
            int nx = x + DIRS[d][0], ny = y + DIRS[d][1], nz = z + DIRS[d][2];
            if (kindAt(nx, ny, nz) != 0 && groupOf[index(nx, ny, nz)] == g) continue;
            FloatArrayList shell = shells[g];
            for (int[] corner : CORNERS[d]) {
                shell.add(x + corner[0]);
                shell.add(y + corner[1]);
                shell.add(z + corner[2]);
            }
        }
    }

    private boolean crease(int a, int b, int c, int d) {
        int n = (a != 0 ? 1 : 0) + (b != 0 ? 1 : 0) + (c != 0 ? 1 : 0) + (d != 0 ? 1 : 0);
        if (n == 1 || n == 3) return true;
        if (n != 2) return false;
        if (a != 0 && d != 0 || b != 0 && c != 0) return true;
        int first = a != 0 ? a : b != 0 ? b : c;
        int second = d != 0 ? d : c != 0 && (a != 0 || b != 0) ? c : b;
        return first != second;
    }

    private void line(int x0, int y0, int z0, int x1, int y1, int z1) {
        outline.add(x0);
        outline.add(y0);
        outline.add(z0);
        outline.add(x1);
        outline.add(y1);
        outline.add(z1);
    }

    private void outline() {
        for (int y = 0; y <= sizeY; y++) for (int z = 0; z <= sizeZ; z++) {
            int start = -1;
            for (int x = 0; x <= sizeX; x++) {
                boolean on = x < sizeX
                    && crease(kindAt(x, y - 1, z - 1), kindAt(x, y, z - 1), kindAt(x, y - 1, z), kindAt(x, y, z));
                if (on && start < 0) start = x;
                if (!on && start >= 0) {
                    line(start, y, z, x, y, z);
                    start = -1;
                }
            }
        }
        for (int x = 0; x <= sizeX; x++) for (int z = 0; z <= sizeZ; z++) {
            int start = -1;
            for (int y = 0; y <= sizeY; y++) {
                boolean on = y < sizeY
                    && crease(kindAt(x - 1, y, z - 1), kindAt(x, y, z - 1), kindAt(x - 1, y, z), kindAt(x, y, z));
                if (on && start < 0) start = y;
                if (!on && start >= 0) {
                    line(x, start, z, x, y, z);
                    start = -1;
                }
            }
        }
        for (int x = 0; x <= sizeX; x++) for (int y = 0; y <= sizeY; y++) {
            int start = -1;
            for (int z = 0; z <= sizeZ; z++) {
                boolean on = z < sizeZ
                    && crease(kindAt(x - 1, y - 1, z), kindAt(x, y - 1, z), kindAt(x - 1, y, z), kindAt(x, y, z));
                if (on && start < 0) start = z;
                if (!on && start >= 0) {
                    line(x, y, start, x, y, z);
                    start = -1;
                }
            }
        }
    }
}
