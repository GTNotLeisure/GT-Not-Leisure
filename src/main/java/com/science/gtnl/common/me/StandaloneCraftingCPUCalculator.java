package com.science.gtnl.common.me;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.science.gtnl.common.block.blocks.tile.TileEntityMentalMathUnit;

import appeng.api.util.WorldCoord;
import appeng.core.AELog;
import appeng.me.cluster.IAECluster;
import appeng.me.cluster.implementations.CraftingCPUCalculator;

public class StandaloneCraftingCPUCalculator extends CraftingCPUCalculator {

    private final TileEntityMentalMathUnit tile;

    public StandaloneCraftingCPUCalculator(TileEntityMentalMathUnit tile) {
        super(tile);
        this.tile = tile;
    }

    @Override
    public void calculateMultiblock(World world, WorldCoord location) {
        if (world == null || world.isRemote) {
            return;
        }
        try {
            // Only the target tile belongs to this CPU, so adjacent regions need no scans.
            if (!verifyInternalStructure(world, location, location)) {
                disconnect();
                return;
            }

            IAECluster cluster = tile.getCluster();
            boolean updateGrid = cluster == null || !matchesExistingCluster(cluster, location, location);
            if (updateGrid) {
                if (cluster != null) {
                    cluster.destroy();
                }
                WorldCoord min = location.copy();
                WorldCoord max = location.copy();
                cluster = createCluster(world, min, max);
                updateTiles(cluster, world, min, max);
            }
            cluster.updateStatus(updateGrid);
            return;
        } catch (Throwable error) {
            AELog.debug(error);
        }
        disconnect();
    }

    @Override
    public boolean isValidTile(TileEntity candidate) {
        return candidate == tile;
    }

    @Override
    public boolean checkMultiblockScale(WorldCoord min, WorldCoord max) {
        return min.x == max.x && min.y == max.y && min.z == max.z;
    }

    @Override
    public boolean verifyInternalStructure(World world, WorldCoord min, WorldCoord max) {
        return checkMultiblockScale(min, max) && !tile.isInvalid()
            && world.getTileEntity(min.x, min.y, min.z) == tile
            && tile.isValid()
            && tile.getStorageBytes() > 0;
    }

    @Override
    public IAECluster createCluster(World world, WorldCoord min, WorldCoord max) {
        return new StandaloneCraftingCPUCluster(
            min,
            max,
            tile.getDefinition()
                .parallelism());
    }
}
