package com.science.gtnl.client.projector;

import static com.science.gtnl.common.projector.ProjectorSite.CORRECT;
import static com.science.gtnl.common.projector.ProjectorSite.MISSING;
import static com.science.gtnl.common.projector.ProjectorSite.UNLOADED;
import static com.science.gtnl.common.projector.ProjectorSite.WRONG;

import java.util.Arrays;

import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;

import com.gtnewhorizon.gtnhlib.util.CoordinatePacker;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.science.gtnl.common.projector.ProjectorSite;
import com.science.gtnl.common.projector.ProjectorStructure;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@SideOnly(Side.CLIENT)
public class ProjectorCheck {

    private static final int SCAN_PER_TICK = 16384, RECHECK_TICKS = 20, FULL_SCAN_TICKS = 600;

    public ProjectorStructure structure;
    public ProjectorSite site;
    public ExtendedFacing facing;
    public int originX, originY, originZ;
    public byte[] status = new byte[0];
    public int[] worldX = new int[0], worldY = new int[0], worldZ = new int[0];
    public int correct, missing, wrong, unloaded;
    public boolean scanned;
    public int revision;
    private World world;
    private final Long2IntOpenHashMap byPosition = new Long2IntOpenHashMap();
    private final Long2ObjectOpenHashMap<IntArrayList> byChunk = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private final IntArrayList machineCells = new IntArrayList();
    private int cursor, ticks;

    public ProjectorCheck() {
        byPosition.defaultReturnValue(-1);
    }

    public boolean isActive() {
        return structure != null;
    }

    public void clear() {
        structure = null;
        site = null;
        world = null;
        status = new byte[0];
        byPosition.clear();
        byChunk.clear();
        dirty.clear();
        machineCells.clear();
        correct = missing = wrong = unloaded = 0;
        scanned = false;
    }

    public void update(World current, ProjectorStructure target, ExtendedFacing targetFacing, int x, int y, int z,
        int[] offsets) {
        if (target != structure || targetFacing != facing
            || current != world
            || x != originX
            || y != originY
            || z != originZ
            || !Arrays.equals(offsets, site.offsets)) rebuild(current, target, targetFacing, x, y, z, offsets);
        if (!dirty.isEmpty()) {
            LongIterator iterator = dirty.iterator();
            while (iterator.hasNext()) {
                int cell = byPosition.get(iterator.nextLong());
                if (cell >= 0) evaluate(cell);
            }
            dirty.clear();
        }
        if (!scanned) {
            int end = Math.min(status.length, cursor + SCAN_PER_TICK);
            for (; cursor < end; cursor++) evaluate(cursor);
            if (cursor >= status.length) scanned = true;
        } else if (++ticks % FULL_SCAN_TICKS == 0) {
            cursor = 0;
            scanned = false;
        } else if (ticks % RECHECK_TICKS == 0) {
            // Block changes arrive through the notifiers, but a GT machine's id comes with its tile entity data later.
            for (int i = 0; i < machineCells.size(); i++) {
                int cell = machineCells.getInt(i);
                if (status[cell] != CORRECT) evaluate(cell);
            }
        }
    }

    public boolean isComplete() {
        return scanned && status.length > 0 && missing == 0 && wrong == 0 && unloaded == 0;
    }

    // Indices count every copy; the structure cell of one is site.local(index).
    public int indexAt(int x, int y, int z) {
        int index = byPosition.get(CoordinatePacker.pack(x, y, z));
        return structure != null && index >= 0 && site.isBlock(index) ? index : -1;
    }

    public int copies() {
        return site == null ? 1 : site.copies();
    }

    // The block the ray starts in is skipped, as the player may stand inside the projection.
    public int firstCellAlong(Vec3 start, Vec3 direction, double length) {
        if (structure == null) return -1;
        int x = MathHelper.floor_double(start.xCoord), y = MathHelper.floor_double(start.yCoord),
            z = MathHelper.floor_double(start.zCoord);
        int stepX = direction.xCoord > 0 ? 1 : -1, stepY = direction.yCoord > 0 ? 1 : -1,
            stepZ = direction.zCoord > 0 ? 1 : -1;
        double deltaX = Math.abs(1 / direction.xCoord), deltaY = Math.abs(1 / direction.yCoord),
            deltaZ = Math.abs(1 / direction.zCoord);
        double nextX = boundary(start.xCoord, x, direction.xCoord, deltaX),
            nextY = boundary(start.yCoord, y, direction.yCoord, deltaY),
            nextZ = boundary(start.zCoord, z, direction.zCoord, deltaZ);
        while (true) {
            double distance;
            if (nextX <= nextY && nextX <= nextZ) {
                distance = nextX;
                x += stepX;
                nextX += deltaX;
            } else if (nextY <= nextZ) {
                distance = nextY;
                y += stepY;
                nextY += deltaY;
            } else {
                distance = nextZ;
                z += stepZ;
                nextZ += deltaZ;
            }
            if (distance > length) return -1;
            int index = indexAt(x, y, z);
            if (index >= 0) return index;
        }
    }

    private static double boundary(double start, int block, double direction, double delta) {
        if (direction == 0) return Double.POSITIVE_INFINITY;
        return (direction > 0 ? block + 1 - start : start - block) * delta;
    }

    public void blockChanged(World changed, int x, int y, int z) {
        if (changed == world && structure != null) dirty.add(CoordinatePacker.pack(x, y, z));
    }

    public void chunkReceived(int chunkX, int chunkZ) {
        IntArrayList cells = byChunk.get(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ));
        if (cells == null) return;
        for (int i = 0; i < cells.size(); i++) {
            int cell = cells.getInt(i);
            dirty.add(CoordinatePacker.pack(worldX[cell], worldY[cell], worldZ[cell]));
        }
    }

    private void rebuild(World current, ProjectorStructure target, ExtendedFacing targetFacing, int x, int y, int z,
        int[] offsets) {
        clear();
        world = current;
        structure = target;
        facing = targetFacing;
        originX = x;
        originY = y;
        originZ = z;
        site = new ProjectorSite(target, targetFacing, x, y, z, offsets);
        int total = site.total();
        status = new byte[total];
        worldX = site.worldX;
        worldY = site.worldY;
        worldZ = site.worldZ;
        for (int cell = 0; cell < total; cell++) {
            if (site.isShadowed(cell)) continue;
            if (site.isBlock(cell) && target.machines[site.local(cell)] > 0) machineCells.add(cell);
            byPosition.put(CoordinatePacker.pack(worldX[cell], worldY[cell], worldZ[cell]), cell);
            byChunk
                .computeIfAbsent(
                    ChunkCoordIntPair.chunkXZ2Int(worldX[cell] >> 4, worldZ[cell] >> 4),
                    k -> new IntArrayList())
                .add(cell);
        }
        cursor = 0;
        ticks = 0;
        revision++;
    }

    private void evaluate(int cell) {
        byte next = site.compute(world, cell);
        byte previous = status[cell];
        if (next == previous) return;
        count(previous, -1);
        count(next, 1);
        status[cell] = next;
        revision++;
    }

    private void count(byte state, int delta) {
        switch (state) {
            case CORRECT -> correct += delta;
            case MISSING -> missing += delta;
            case WRONG -> wrong += delta;
            case UNLOADED -> unloaded += delta;
            default -> {}
        }
    }

}
