package com.science.gtnl.common.structureanimation;

import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import com.science.gtnl.common.structureanimation.StructureFlightPlan.Partition;
import com.science.gtnl.common.structureanimation.StructureFlightPlan.Timing;

import io.netty.buffer.ByteBuf;

public enum StructureAnimationStyle {

    REVEAL {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            // Small structures pass through the white outline quickly; each doubling of the cells adds two ticks.
            int cells = Math.max(1, site.xs().length);
            int ticks = Math.min(REVEAL_MAX_TICKS, REVEAL_MIN_TICKS + 2 * (31 - Integer.numberOfLeadingZeros(cells)));
            extra.writeFloat(ticks / 40f);
            extra.writeFloat(ticks / 40f);
            return ticks;
        }

        @Override
        public int[] order(Site site) {
            int count = site.xs().length;
            int[] cx = new int[count], cy = new int[count], cz = new int[count];
            for (int i = 0; i < count; i++) {
                cx[i] = site.xs()[i] - site.minX();
                cy[i] = site.ys()[i] - site.minY();
                cz[i] = site.zs()[i] - site.minZ();
            }
            return StructureRevealGroups.order(
                StructureRevealGroups.assign(cx, cy, cz, site.sizeY()),
                StructureRevealGroups.groupCount(site.sizeY(), count));
        }
    },
    SUMMON {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            int descent = SUMMON_DESCENT + Math.min(SUMMON_EXTRA_MAX, site.xs().length / SUMMON_EXTRA_CELLS);
            extra.writeShort(openingY(site));
            extra.writeShort(descent);
            return SUMMON_DELAY + SUMMON_OPEN + descent;
        }
    },
    FLIGHT {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            int[] launch = StructureFlightPlan.launchPoint(
                site.minX(),
                site.minZ(),
                site.sizeX(),
                site.sizeZ(),
                site.maxY(),
                site.builderX(),
                site.builderZ(),
                site.lookX(),
                site.lookZ());
            Partition partition = StructureFlightPlan.partition(
                site.xs(),
                site.ys(),
                site.zs(),
                site.minX(),
                site.minY(),
                site.minZ(),
                site.sizeX(),
                site.sizeY(),
                site.sizeZ(),
                launch[0],
                launch[2]);
            Timing timing = StructureFlightPlan.timing(
                partition,
                site.xs().length,
                launch[0],
                launch[1],
                launch[2],
                site.minX() + site.sizeX() / 2.0,
                site.minZ() + site.sizeZ() / 2.0);
            for (int value : launch) extra.writeInt(value);
            extra.writeByte(timing.flights().length);
            for (int band = 0; band < timing.flights().length; band++) {
                extra.writeShort(timing.flights()[band]);
                extra.writeShort(timing.launches()[band]);
            }
            return timing.total();
        }
    },
    NONE {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            return 0;
        }
    };

    public static final int SUMMON_DELAY = 2, SUMMON_OPEN = 16, SUMMON_DESCENT = 92, SUMMON_CLEARANCE = 15,
        SUMMON_EXTRA_CELLS = 1500, SUMMON_EXTRA_MAX = 100;
    public static final int REVEAL_MIN_TICKS = 6, REVEAL_MAX_TICKS = 24;

    // Writes this style's part of the start packet and returns the tick at which the server starts placing.
    public abstract int prepare(Site site, ByteBuf extra);

    // The order in which the server must place the site's cells, as indices into them; null keeps them as given.
    public int[] order(Site site) {
        return null;
    }

    // The circle opens 15 blocks above the top, lowered to the first solid block over the machine's own
    // footprint; columns in unloaded chunks are skipped rather than treated as blocked.
    public static int openingY(Site site) {
        int maxY = site.maxY(), opening = Math.min(maxY + 1 + SUMMON_CLEARANCE, 255);
        World world = DimensionManager.getWorld(site.dimension());
        if (world == null) return maxY + 1;
        for (int x = site.minX(); x < site.minX() + site.sizeX(); x++)
            for (int z = site.minZ(); z < site.minZ() + site.sizeZ(); z++) {
                if (!world.blockExists(x, maxY, z)) continue;
                for (int y = maxY + 1; y < opening; y++) {
                    if (!world.isAirBlock(x, y, z)) {
                        opening = y;
                        break;
                    }
                }
            }
        return Math.max(maxY + 1, opening);
    }

    public record Site(int dimension, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, int[] xs, int[] ys,
        int[] zs, int controllerX, int controllerY, int controllerZ, int facing, double builderX, double builderZ,
        double lookX, double lookZ) {

        public int maxY() {
            return minY + sizeY - 1;
        }
    }
}
