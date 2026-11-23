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
    public final int[] worldX, worldY, worldZ;

    public ProjectorSite(ProjectorStructure structure, ExtendedFacing facing, int x, int y, int z) {
        this.structure = structure;
        this.facing = facing;
        originX = x;
        originY = y;
        originZ = z;
        int total = structure.size + structure.emptyCells.length;
        worldX = new int[total];
        worldY = new int[total];
        worldZ = new int[total];
        int[] offset = new int[3];
        for (int cell = 0; cell < total; cell++) {
            if (cell < structure.size) {
                facing.getWorldOffset(new int[] { structure.a[cell], structure.b[cell], structure.c[cell] }, offset);
            } else {
                long packed = structure.emptyCells[cell - structure.size];
                facing.getWorldOffset(
                    new int[] { ProjectorPlan.unpackA(packed), ProjectorPlan.unpackB(packed),
                        ProjectorPlan.unpackC(packed) },
                    offset);
            }
            worldX[cell] = x + offset[0];
            worldY[cell] = y + offset[1];
            worldZ[cell] = z + offset[2];
        }
    }

    public int total() {
        return worldX.length;
    }

    // Chunks the server has not loaded count as unloaded instead of being loaded from disk for the check.
    public byte compute(World world, int cell) {
        int x = worldX[cell], y = worldY[cell], z = worldZ[cell];
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
    public byte frontOf(int cell) {
        int simulated = structure.frontOf(cell);
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
