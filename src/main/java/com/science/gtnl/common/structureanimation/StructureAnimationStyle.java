package com.science.gtnl.common.structureanimation;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.DimensionManager;

import com.science.gtnl.common.structureanimation.StructureFlightPlan.Partition;
import com.science.gtnl.common.structureanimation.StructureFlightPlan.Timing;

import io.netty.buffer.ByteBuf;

public enum StructureAnimationStyle {

    REVEAL {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            int cells = Math.max(1, site.xs().length);
            int ticks = Math.min(
                REVEAL_MAX_TICKS,
                REVEAL_MIN_TICKS + REVEAL_TICKS_PER_DOUBLING * (31 - Integer.numberOfLeadingZeros(cells)));
            float halfSeconds = ticks / 2f / TICKS_PER_SECOND;
            extra.writeFloat(halfSeconds);
            extra.writeFloat(halfSeconds);
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
    PRINT {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            int scan = Math.max(
                PRINT_MIN_SCAN,
                Math.min(
                    PRINT_MAX_SCAN,
                    PRINT_BASE_SCAN + site.sizeY() * PRINT_SCAN_PER_LAYER + site.xs().length / PRINT_CELLS_PER_TICK));
            extra.writeShort(scan);
            return PRINT_INTRO + scan;
        }
    },
    RISE {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            int lift = Math.max(
                RISE_MIN_LIFT,
                Math.min(
                    RISE_MAX_LIFT,
                    RISE_BASE_LIFT + site.sizeY() * RISE_LIFT_PER_LAYER + site.xs().length / RISE_CELLS_PER_TICK));
            int stages = site.sizeY() <= RISE_SHORT_HEIGHT ? RISE_SHORT_STAGES : RISE_STAGES;
            extra.writeShort(lift);
            extra.writeByte(stages);
            return RISE_PRE + lift;
        }
    },
    SINGULARITY {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            int charge = SINGULARITY_CHARGE
                + Math.min(SINGULARITY_EXTRA_MAX, site.xs().length / SINGULARITY_EXTRA_CELLS);
            extra.writeShort(charge);
            extra.writeShort(SINGULARITY_UNFOLD);
            return charge + SINGULARITY_UNFOLD;
        }
    },
    WARP_IN {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            int materialize = Math.max(
                WARP_IN_MIN_MATERIALIZE,
                Math.min(
                    WARP_IN_MAX_MATERIALIZE,
                    WARP_IN_BASE_MATERIALIZE + site.sizeY() / WARP_IN_LAYERS_PER_TICK
                        + site.xs().length / WARP_IN_CELLS_PER_TICK));
            extra.writeShort(materialize);
            return WARP_IN_TEAR + WARP_IN_WORMHOLE + materialize;
        }
    },
    ORBITAL {

        @Override
        public int prepare(Site site, ByteBuf extra) {
            int dropHeight = orbitalDropHeight(site);
            int dropTicks = orbitalDropTicks(dropHeight);
            extra.writeShort(dropHeight);
            extra.writeShort(ORBITAL_BEACON);
            extra.writeShort(dropTicks);
            return ORBITAL_BEACON + dropTicks;
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
    public static final int REVEAL_MIN_TICKS = 6, REVEAL_MAX_TICKS = 24, REVEAL_TICKS_PER_DOUBLING = 2;
    public static final int PRINT_INTRO = 10, PRINT_MIN_SCAN = 36, PRINT_MAX_SCAN = 120, PRINT_BASE_SCAN = 28,
        PRINT_SCAN_PER_LAYER = 4, PRINT_CELLS_PER_TICK = 2000;
    public static final int RISE_PRE = 12, RISE_MIN_LIFT = 56, RISE_MAX_LIFT = 130, RISE_BASE_LIFT = 48,
        RISE_LIFT_PER_LAYER = 4, RISE_CELLS_PER_TICK = 1800, RISE_SHORT_HEIGHT = 4, RISE_SHORT_STAGES = 2,
        RISE_STAGES = 3;
    public static final int SINGULARITY_CHARGE = 26, SINGULARITY_UNFOLD = 22, SINGULARITY_EXTRA_MAX = 16,
        SINGULARITY_EXTRA_CELLS = 5000;
    public static final int WARP_IN_TEAR = 12, WARP_IN_WORMHOLE = 28, WARP_IN_MIN_MATERIALIZE = 32,
        WARP_IN_MAX_MATERIALIZE = 44, WARP_IN_BASE_MATERIALIZE = 30, WARP_IN_LAYERS_PER_TICK = 2,
        WARP_IN_CELLS_PER_TICK = 3200;
    public static final int ORBITAL_BEACON = 26, ORBITAL_DROP = 20, ORBITAL_MIN_DROP = 3, ORBITAL_CLEARANCE = 48;
    public static final int WORLD_HEIGHT = 256, TICKS_PER_SECOND = 20;

    /** Writes this style's part of the start packet and returns the tick at which the server starts placing. */
    public abstract int prepare(Site site, ByteBuf extra);

    public int[] order(Site site) {
        return null;
    }

    public static int openingY(Site site) {
        int maxY = site.maxY();
        World world = DimensionManager.getWorld(site.dimension());
        if (world == null) return maxY + 1;
        return Math.max(maxY + 1, ceilingY(world, site, Math.min(maxY + 1 + SUMMON_CLEARANCE, WORLD_HEIGHT - 1)));
    }

    public static int orbitalDropHeight(Site site) {
        int maxY = site.maxY(), limit = Math.min(maxY + 1 + ORBITAL_CLEARANCE, WORLD_HEIGHT);
        World world = DimensionManager.getWorld(site.dimension());
        int top = world == null ? limit : ceilingY(world, site, limit);
        return Math.max(0, top - maxY - 1);
    }

    public static int orbitalDropTicks(int dropHeight) {
        if (dropHeight <= 0) return 0;
        double fall = Math.sqrt(Math.min(1.0, dropHeight / (double) ORBITAL_CLEARANCE));
        return Math.max(ORBITAL_MIN_DROP, (int) Math.round(ORBITAL_DROP * fall));
    }

    // Empty sections hold only Blocks.air, so skipping them matches checking every block with isAirBlock.
    public static int ceilingY(World world, Site site, int limit) {
        int maxY = site.maxY(), top = limit;
        int x0 = site.minX(), x1 = site.minX() + site.sizeX() - 1;
        int z0 = site.minZ(), z1 = site.minZ() + site.sizeZ() - 1;
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
            int ax = Math.max(x0, cx << 4), bx = Math.min(x1, (cx << 4) + 15);
            int az = Math.max(z0, cz << 4), bz = Math.min(z1, (cz << 4) + 15);
            // blockExists only checks the chunk is loaded; getChunkFromChunkCoords would load or generate it.
            if (!world.blockExists(ax, maxY, az)) continue;
            Chunk chunk = world.getChunkFromChunkCoords(cx, cz);
            ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
            chunkScan: for (int y = maxY + 1; y < top;) {
                int section = y >> 4;
                ExtendedBlockStorage storage = section < sections.length ? sections[section] : null;
                if (storage == null || storage.isEmpty()) {
                    y = (section + 1) << 4;
                    continue;
                }
                for (int x = ax; x <= bx; x++) for (int z = az; z <= bz; z++) {
                    if (!chunk.getBlock(x & 15, y, z & 15)
                        .isAir(world, x, y, z)) {
                        top = y;
                        break chunkScan;
                    }
                }
                y++;
            }
        }
        return top;
    }

    public record Site(int dimension, int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ, int[] xs, int[] ys,
        int[] zs, int controllerX, int controllerY, int controllerZ, int facing, double builderX, double builderZ,
        double lookX, double lookZ) {

        public int maxY() {
            return minY + sizeY - 1;
        }
    }
}
