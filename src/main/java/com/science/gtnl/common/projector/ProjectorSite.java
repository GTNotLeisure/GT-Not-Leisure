package com.science.gtnl.common.projector;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.science.gtnl.common.projector.ProjectorStructure.Choice;
import com.science.gtnl.common.projector.ProjectorStructure.HatchSlot;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

public class ProjectorSite {

    public static final byte UNKNOWN = 0, CORRECT = 1, MISSING = 2, WRONG = 3, UNLOADED = 4;

    public final ProjectorStructure structure;
    public final ExtendedFacing facing;
    public final int originX, originY, originZ;
    public final int[] offsets;
    public final int perCopy;
    public final int[] worldX, worldY, worldZ;

    // Cells of copy n follow those of copy n - 1, each copy laid out like a single structure.
    public ProjectorSite(ProjectorStructure structure, ExtendedFacing facing, int x, int y, int z, int[] offsets) {
        this.structure = structure;
        this.facing = facing;
        this.offsets = offsets;
        originX = x;
        originY = y;
        originZ = z;
        perCopy = structure.size + structure.emptyCells.length;
        int total = perCopy * copies();
        worldX = new int[total];
        worldY = new int[total];
        worldZ = new int[total];
        int[] offset = new int[3];
        for (int cell = 0; cell < perCopy; cell++) {
            if (cell < structure.size) {
                facing.getWorldOffset(new int[] { structure.a[cell], structure.b[cell], structure.c[cell] }, offset);
            } else {
                long packed = structure.emptyCells[cell - structure.size];
                facing.getWorldOffset(
                    new int[] { ProjectorPlan.unpackA(packed), ProjectorPlan.unpackB(packed),
                        ProjectorPlan.unpackC(packed) },
                    offset);
            }
            for (int copy = 0; copy < copies(); copy++) {
                int index = copy * perCopy + cell;
                worldX[index] = x + offsets[copy * 3] + offset[0];
                worldY[index] = y + offsets[copy * 3 + 1] + offset[1];
                worldZ[index] = z + offsets[copy * 3 + 2] + offset[2];
            }
        }
    }

    public int total() {
        return worldX.length;
    }

    public int copies() {
        return offsets.length / 3;
    }

    public int local(int cell) {
        return cell % perCopy;
    }

    public boolean isBlock(int cell) {
        return local(cell) < structure.size;
    }

    public boolean isController(int x, int y, int z) {
        for (int copy = 0; copy < copies(); copy++) {
            if (x == originX + offsets[copy * 3] && y == originY + offsets[copy * 3 + 1]
                && z == originZ + offsets[copy * 3 + 2]) return true;
        }
        return false;
    }

    // Chunks the server has not loaded count as unloaded instead of being loaded from disk for the check.
    public byte compute(World world, int index) {
        int x = worldX[index], y = worldY[index], z = worldZ[index], cell = local(index);
        if (y < 0 || y > 255
            || !world.getChunkProvider()
                .chunkExists(x >> 4, z >> 4))
            return UNLOADED;
        Chunk chunk = world.getChunkFromChunkCoords(x >> 4, z >> 4);
        if (chunk instanceof EmptyChunk || !chunk.isChunkLoaded) return UNLOADED;
        Block block = chunk.getBlock(x & 15, y, z & 15);
        if (cell >= structure.size) return block.isAir(world, x, y, z) ? CORRECT : WRONG;
        boolean free = block.isAir(world, x, y, z) || block.isReplaceable(world, x, y, z);
        int meta = chunk.getBlockMetadata(x & 15, y, z & 15);
        if (structure.blocks[cell] == Blocks.air) {
            Choice choice = structure.choiceAt(cell);
            if (choice == null || free) return CORRECT;
            return matches(world, cell, block, meta, x, y, z) ? CORRECT : WRONG;
        }
        if (matches(world, cell, block, meta, x, y, z)) return CORRECT;
        return free ? MISSING : WRONG;
    }

    private boolean matches(World world, int cell, Block block, int meta, int x, int y, int z) {
        int expected = structure.machines[cell];
        if (expected <= 0 && block == structure.blocks[cell]
            && (meta == structure.metas[cell] || structure.metas[cell] == ProjectorStructure.ANY_META)) return true;
        Choice choice = structure.choiceAt(cell);
        if (expected <= 0 && choice != null
            && choice.channel() == null
            && choice.accepts(Item.getItemFromBlock(block), ProjectorStructure.damageOf(world, block, meta, x, y, z)))
            return true;
        if (!(world.getTileEntity(x, y, z) instanceof IGregTechTileEntity tile)) return false;
        int actual = tile.getMetaTileID();
        if (actual == expected) return true;
        HatchSlot slot = structure.slotAt(cell);
        if (slot == null) return false;
        for (int candidate : slot.accepted()) {
            if (candidate == actual) return true;
        }
        return false;
    }

    // The simulation built the structure facing the canonical way; turn each machine's front the same way the
    // projection is turned.
    public byte frontOf(int index) {
        int simulated = structure.frontOf(local(index));
        if (simulated < 0) return -1;
        ForgeDirection front = ForgeDirection.getOrientation(simulated);
        int[] relative = new int[3], world = new int[3];
        structure.canonical.getOffsetABC(new int[] { front.offsetX, front.offsetY, front.offsetZ }, relative);
        facing.getWorldOffset(relative, world);
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (direction.offsetX == world[0] && direction.offsetY == world[1] && direction.offsetZ == world[2])
                return (byte) direction.ordinal();
        }
        return -1;
    }
}
