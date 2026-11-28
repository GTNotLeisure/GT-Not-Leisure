package com.science.gtnl.client.structureanimation;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSound;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.gtnllib.client.fx.world.FxBlockMesh;
import com.gtnllib.client.fx.world.FxCamera;
import com.gtnllib.client.fx.world.FxCameraShake;
import com.gtnllib.client.fx.world.FxJetParticle;
import com.gtnllib.utils.FlightPath;
import com.gtnllib.utils.Vec3d;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.structureanimation.StructureFlightPlan;
import com.science.gtnl.common.structureanimation.StructureFlightPlan.Partition;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

@SideOnly(Side.CLIENT)
public class StructureFlightAnimation extends StructureMeshTask {

    public static final int APPEAR = StructureFlightPlan.APPEAR, HOVER = StructureFlightPlan.HOVER,
        SNAP = StructureFlightPlan.SNAP, LATCH = StructureFlightPlan.LATCH, MIN_LEFT = 12, PARTICLES_PER_TICK = 80,
        BURSTS_PER_TICK = 30, MAX_LOOP_BANDS = 4, PARTICLE_RANGE = 64, FLYBY_LEAD = 26, HISS_DELAY = 10;
    public static final double LOCK_WINDOW = SNAP + LATCH - 3, CLOSE_SPAN = 9.5, FLASH_TICKS = 3, BAND_FLASH = 3.5,
        MAX_YAW = 25, ACCEL_PITCH = 8, BRAKE_PITCH = 12, MAX_ROLL = 15, JITTER = 0.6, MAX_TILT = 10, MAX_SHIFT = 4,
        MAX_RISE = 0.5, LIFT = 0.5, RECOIL = 0.04, RECOIL_EDGE = 0.15, SHAKE_BASE = 0.8, SHAKE_PER_BLOCK = 0.1,
        SHAKE_MAX = 3, SHAKE_RUMBLE = 0.5;
    public static final String SOUND = "sciencenotleisure:armory.";
    private static final int APPEARED = 1, LAUNCHED = 2, FLYBY = 4, BRAKED = 8, HOVERED = 16, SNAPPED = 32,
        LATCHED = 64, LOCKED = 128;
    private static final FxCameraShake.Source SHAKE = new FxCameraShake.Source();
    private static final Random RANDOM = new Random();

    public class Band {

        public final int index, flight, launch, arrival;
        public final int[][] blockCells = new int[StructureFlightPlan.BLOCKS_PER_BAND][];
        public final FxBlockMesh[] meshes = new FxBlockMesh[StructureFlightPlan.BLOCKS_PER_BAND];
        public final int[] rank = { -1, -1, -1, -1 };
        public final double[][] blockDir = new double[StructureFlightPlan.BLOCKS_PER_BAND][];
        public final double[][] blockPivot = new double[StructureFlightPlan.BLOCKS_PER_BAND][];
        public final double[] blockShift = new double[StructureFlightPlan.BLOCKS_PER_BAND],
            blockTilt = new double[StructureFlightPlan.BLOCKS_PER_BAND];
        public final Vec3d anchor, start, c1, c2, hold;
        public final double height, halfX, halfZ, maxEdge, shrink, heading;
        public int blocks, fired, lockedBlocks;
        public double launchAt = -1;
        public boolean compiled, skipped;
        public Loop jet, turbine;

        public Band(int index, IntArrayList[] cells, int[] bounds) {
            this.index = index;
            flight = flights[index];
            launch = launches[index];
            skipped = partition.empty(index);
            arrival = StructureFlightPlan.arrival(flight);
            double[] a = partition.anchor(index);
            anchor = new Vec3d(a[0], a[1], a[2]);
            height = bounds[4] - bounds[1] + 1;
            halfX = (bounds[3] - bounds[0] + 1) / 2.0;
            halfZ = (bounds[5] - bounds[2] + 1) / 2.0;
            maxEdge = partition.maxEdge(index);
            shrink = 1 / (1 + maxEdge / 8);
            double[] s = partition.launch(index, launchX, launchY, launchZ, centerX, centerZ);
            this.start = new Vec3d(s[0], s[1], s[2]);
            hold = anchor.add(0, partition.hover(index), 0);
            double d = Math.sqrt(this.start.distanceToSqr(hold));
            double fx = hold.x() - this.start.x(), fz = hold.z() - this.start.z(), fl = Math.hypot(fx, fz);
            if (fl < 1.0E-6) {
                fx = 0;
                fz = 1;
                fl = 1;
            }
            double side = (index % 2 == 0 ? 1 : -1) * (1.5 + 0.08 * Math.min(d, 40));
            c1 = this.start.add(-fz / fl * side, 3 + 0.12 * Math.min(d, 60), fx / fl * side);
            c2 = hold.add(0, 2 + d / 8, 0);
            heading = Math.toDegrees(Math.atan2(fx, fz));
            for (int block = 0; block < blockCells.length; block++) {
                if (cells[block] == null || cells[block].isEmpty()) continue;
                blockCells[block] = cells[block].toIntArray();
                rank[block] = blocks++;
                shapeBlock(block);
            }
        }

        private void shapeBlock(int block) {
            int[] cells = blockCells[block];
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE,
                maxZ = Integer.MIN_VALUE;
            for (int cell : cells) {
                minX = Math.min(minX, wx[cell]);
                minY = Math.min(minY, wy[cell]);
                minZ = Math.min(minZ, wz[cell]);
                maxX = Math.max(maxX, wx[cell]);
                maxZ = Math.max(maxZ, wz[cell]);
            }
            double dx = 0, dz = 0;
            if (useA) {
                double sign = (block & 1) != 0 ? 1 : -1;
                if (splitA == 0) dx = sign;
                else dz = sign;
            }
            if (useB) dz = (block & 2) != 0 ? 1 : -1;
            double length = Math.hypot(dx, dz);
            if (length < 1.0E-6) {
                blockDir[block] = new double[] { 0, 0 };
                blockPivot[block] = new double[] { 0, 0, 0 };
                return;
            }
            dx /= length;
            dz /= length;
            double sizeX = maxX - minX + 1, sizeZ = maxZ - minZ + 1;
            double width = Math.abs(dx) * sizeX + Math.abs(dz) * sizeZ;
            blockDir[block] = new double[] { dx, dz };
            blockShift[block] = Math.min(MAX_SHIFT, 0.8 + 0.35 * width / 2);
            double rise = Math.toDegrees(Math.asin(Math.min(1, MAX_RISE / Math.max(1, width))));
            blockTilt[block] = Math.min(MAX_TILT * shrink, rise);
            double cx = (minX + maxX + 1) / 2.0, cz = (minZ + maxZ + 1) / 2.0;
            blockPivot[block] = new double[] { cx + dx * sizeX / 2, minY, cz + dz * sizeZ / 2 };
        }

        public double hoverStart() {
            return APPEAR + flight;
        }

        public double snapStart() {
            return hoverStart() + HOVER;
        }

        public double snapEnd() {
            return snapStart() + SNAP;
        }

        public double local(double age) {
            if (skipped) return arrival;
            if (launchAt < 0) return -1;
            double t = age - launchAt;
            double rate = arrival / Math.max(1, launch + arrival - launchAt);
            double clock = t * rate;
            if (placingTick >= 0) {
                double placed = placingTick - startTick - launchAt;
                if (t > placed) {
                    double reached = Math.min(arrival, Math.max(0, placed * rate));
                    clock = reached + (t - placed) * (arrival - reached) / 2;
                }
            }
            return Math.min(arrival, clock);
        }

        public Vec3d position(double l) {
            if (l < APPEAR) {
                double k = Math.max(0, l) / APPEAR;
                return start.add(0, -0.55 * (1 - k) * (1 - k), 0);
            }
            if (l < hoverStart())
                return FlightPath.cubic(start, c1, c2, hold, FlightPath.profile((l - APPEAR) / flight));
            if (l < snapStart()) {
                double h = l - hoverStart();
                return hold.add(0, Math.sin(h * 0.75) * 0.055 * Math.min(1, h / 3), 0);
            }
            if (l < snapEnd()) {
                double u = Math.pow((l - snapStart()) / SNAP, 2.6);
                return hold.add(
                    anchor.subtract(hold)
                        .scale(u));
            }
            return anchor;
        }

        public double open(int block, double l) {
            if (blockShift[block] <= 0 || l < hoverStart()) return 0;
            if (l < snapStart()) return FlightPath.ease((l - hoverStart()) / (HOVER * 0.7));
            return 1 - FlightPath.close(l - snapStart(), rank[block], blocks, LOCK_WINDOW, CLOSE_SPAN);
        }

        public double lockTime(int block) {
            return snapStart() + FlightPath.lockTick(rank[block], blocks, LOCK_WINDOW);
        }

        public boolean flying(double l) {
            return l >= APPEAR && l < hoverStart();
        }
    }

    public class Loop extends MovingSound {

        private final Band band;
        private final boolean jetSound;
        private final float gain;
        private Vec3d last;

        public Loop(Band band, String name, boolean jetSound, float gain) {
            super(new ResourceLocation(SOUND + name));
            this.band = band;
            this.jetSound = jetSound;
            this.gain = gain;
            repeat = true;
            field_147665_h = 0;
            volume = 0.01F;
            last = band.position(band.local(age(0)));
            move(last);
        }

        private void move(Vec3d at) {
            xPosF = (float) at.x();
            yPosF = (float) (at.y() + band.height / 2);
            zPosF = (float) at.z();
        }

        @Override
        public void update() {
            double l = band.local(age(0));
            if (released || band.skipped || l >= band.snapEnd()) {
                donePlaying = true;
                return;
            }
            Vec3d at = band.position(l);
            float speed = (float) Math.sqrt(at.distanceToSqr(last));
            last = at;
            move(at);
            double t = (l - APPEAR) / band.flight;
            float level;
            if (l < APPEAR + 3) level = 1.15F;
            else if (t < FlightPath.BRAKE_AT) level = 0.85F;
            else if (t < 1) level = jetSound ? 1.1F : 0.7F;
            else level = jetSound ? 0.45F : 0.35F;
            volume = gain * level + 0.01F;
            float s = Math.min(speed, 1.6F);
            field_147663_c = jetSound ? 0.85F + s * 0.15F : 0.75F + s * 0.45F;
        }
    }

    public final int launchX, launchY, launchZ;
    public final short[] flights, launches;
    public final Partition partition;
    public final Band[] bands;
    public final int[] bandEnds;
    public final Long2IntOpenHashMap sectionBands = new Long2IntOpenHashMap();
    public final int splitA, splitB;
    public final boolean useA, useB;
    public int lockedMask, latchedMask, budget, bursts, lockSoundRank = -1, preparedBands;
    public long lastLatch = -1;
    public boolean released, hissed;

    public StructureFlightAnimation(Start start, ByteBuf extra) {
        super(start);
        launchX = extra.readInt();
        launchY = extra.readInt();
        launchZ = extra.readInt();
        int count = extra.readByte();
        if (count < 0 || count > StructureFlightPlan.MAX_BANDS)
            throw new IllegalArgumentException("Invalid projector flights");
        flights = new short[count];
        launches = new short[count];
        for (int band = 0; band < count; band++) {
            flights[band] = extra.readShort();
            launches[band] = extra.readShort();
        }
        partition = StructureFlightPlan.partition(
            wx,
            wy,
            wz,
            start.originX(),
            start.originY(),
            start.originZ(),
            start.sizeX(),
            start.sizeY(),
            start.sizeZ(),
            launchX,
            launchZ);
        if (partition.bands() != count) throw new IllegalArgumentException("Projector flights do not match the bands");
        for (int band = 0; band < count; band++) if (partition.empty(band) != (flights[band] == 0))
            throw new IllegalArgumentException("Projector flights do not match the bands");
        int axis = partition.axis();
        int[] size = { start.sizeX(), start.sizeY(), start.sizeZ() };
        splitA = axis == 0 ? 2 : 0;
        splitB = axis == 1 ? 2 : -1;
        useA = size[splitA] >= StructureFlightPlan.MIN_SPLIT;
        useB = splitB >= 0 && size[splitB] >= StructureFlightPlan.MIN_SPLIT;
        IntArrayList[][] cells = new IntArrayList[count][StructureFlightPlan.BLOCKS_PER_BAND];
        int[] perBand = new int[count];
        sectionBands.defaultReturnValue(0);
        for (int cell = 0; cell < code.length; cell++) {
            int band = partition.band(cell), block = partition.block(cell);
            if (cells[band][block] == null) cells[band][block] = new IntArrayList();
            cells[band][block].add(cell);
            perBand[band]++;
            long section = sectionKey(wx[cell] - offsetX, wy[cell] - offsetY, wz[cell] - offsetZ);
            sectionBands.put(section, sectionBands.get(section) | 1 << band);
        }
        bandEnds = new int[count];
        int[] cursor = new int[count];
        for (int band = 0, sum = 0; band < count; band++) {
            cursor[band] = sum;
            sum += perBand[band];
            bandEnds[band] = sum;
        }
        placeOrder = new int[code.length];
        for (int cell = 0; cell < code.length; cell++) placeOrder[cursor[partition.band(cell)]++] = cell;
        bands = new Band[count];
        for (int band = 0; band < count; band++) bands[band] = new Band(band, cells[band], partition.bounds()[band]);
        for (Band band : bands) if (band.skipped) {
            lockedMask |= 1 << band.index;
            latchedMask |= 1 << band.index;
        }
    }

    @Override
    protected void placedInCopy(int placed) {
        while (preparedBands < bands.length && placed >= bandEnds[preparedBands]) {
            Band band = bands[preparedBands++];
            for (int block = 0; block < band.blockCells.length; block++)
                if (band.blockCells[block] != null) band.meshes[block] = mesh(band.blockCells[block]);
        }
    }

    @Override
    protected boolean settled(long section) {
        return (sectionBands.get(section) & ~lockedMask) == 0;
    }

    @Override
    protected boolean busy() {
        return false;
    }

    @Override
    protected void releaseOwn() {
        released = true;
        for (Band band : bands) for (int block = 0; block < band.meshes.length; block++) {
            if (band.meshes[block] != null) band.meshes[block].release();
            band.meshes[block] = null;
        }
    }

    @Override
    protected void animate(Minecraft minecraft, double age) {
        budget = particles(PARTICLES_PER_TICK);
        bursts = particles(BURSTS_PER_TICK);
        lockSoundRank = -1;
        int flying = 0;
        for (Band band : bands) {
            launch(band, age);
            if (band.flying(band.local(age))) flying++;
        }
        for (Band band : bands) {
            if (band.skipped) continue;
            if (band.launchAt < 0) gather(minecraft, band);
            break;
        }
        for (Band band : bands) events(minecraft, band, age, flying);
        if (lockSoundRank >= 0) play(lockAt(), "lock", 0.85F, 0.72F + 0.14F * lockSoundRank);
        if (!hissed && lockedMask == (1 << bands.length) - 1 && lastLatch >= 0 && now() - lastLatch >= HISS_DELAY) {
            hissed = true;
            play(new Vec3d(centerX, start.originY() + start.sizeY(), centerZ), "hiss", 0.7F, 1);
        }
    }

    private Vec3d lockAt() {
        return new Vec3d(centerX, start.originY(), centerZ);
    }

    private void launch(Band band, double age) {
        if (band.launchAt >= 0 || band.skipped || age < band.launch) return;
        // A backwards run fast-forwards before any mesh is compiled; every band still has its flight then.
        if (reversed || band.compiled && placingTick < 0 && band.launch + band.arrival - age >= MIN_LEFT) {
            band.launchAt = age;
            return;
        }
        if (placingTick >= 0 || band.launch + band.arrival - age < MIN_LEFT) {
            band.skipped = true;
            lockedMask |= 1 << band.index;
            latchedMask |= 1 << band.index;
        }
    }

    private void gather(Minecraft minecraft, Band band) {
        if (!near(minecraft, band.start, 0)) return;
        float size = size(band);
        for (int i = 0; i < 3 && budget > 0; i++, budget--) {
            double ox = RANDOM.nextGaussian() * 1.6, oy = RANDOM.nextGaussian() * 1.6, oz = RANDOM.nextGaussian() * 1.6;
            spawn(
                minecraft,
                FxJetParticle.Kind.CORE,
                band.start.add(ox, oy, oz),
                -ox * 0.18,
                -oy * 0.18,
                -oz * 0.18,
                size);
        }
    }

    private void events(Minecraft minecraft, Band band, double age, int flying) {
        if (band.skipped || band.launchAt < 0) return;
        double l = band.local(age);
        Vec3d at = band.position(l);
        Vec3d middle = at.add(0, band.height / 2, 0);
        if (once(band, APPEARED, l >= 0)) {
            play(middle, "hatch", 1, 1);
            ring(minecraft, band, at);
        }
        if (once(band, LAUNCHED, l >= APPEAR)) {
            play(middle, "launch", 1, 0.95F + RANDOM.nextFloat() * 0.1F);
            startLoops(minecraft, band);
        }
        if (once(band, FLYBY, band.flight > FLYBY_LEAD + 4 && l >= band.hoverStart() - FLYBY_LEAD))
            play(middle, "flyby", 0.8F, 1);
        double t = (l - APPEAR) / band.flight;
        if (once(band, BRAKED, band.flying(l) && t >= FlightPath.BRAKE_AT)) {
            play(middle, "brake", 1, 1);
            retro(minecraft, band, l, 10);
        }
        if (once(band, HOVERED, l >= band.hoverStart())) play(middle, "unlock", 0.8F, 0.7F);
        if (once(band, SNAPPED, l >= band.snapStart())) play(middle, "pulse", 0.8F, 0.75F);
        for (int block = 0; block < band.rank.length; block++) {
            if (band.rank[block] < 0 || (band.lockedBlocks & 1 << block) != 0 || l < band.lockTime(block)) continue;
            band.lockedBlocks |= 1 << block;
            lockSoundRank = Math.max(lockSoundRank, band.rank[block]);
            sparks(minecraft, band, block);
        }
        if (once(band, LATCHED, l >= band.snapEnd())) {
            play(middle, "latch", 1, 0.95F + RANDOM.nextFloat() * 0.1F);
            latchedMask |= 1 << band.index;
            boolean last = latchedMask == (1 << bands.length) - 1;
            shake(
                SHAKE,
                (float) Math.min(SHAKE_MAX, SHAKE_BASE + SHAKE_PER_BLOCK * band.maxEdge),
                last ? (float) SHAKE_RUMBLE : 0);
            lastLatch = now();
        }
        if (once(band, LOCKED, l >= band.arrival)) lockedMask |= 1 << band.index;
        if (band.flying(l)) exhaust(minecraft, band, l, t < 0.12 ? 2 : 1, flying);
        else if (l >= band.hoverStart() && l < band.snapStart()) lift(minecraft, band, at);
        if (band.flying(l) && t >= FlightPath.BRAKE_AT) retro(minecraft, band, l, 2);
    }

    private static boolean once(Band band, int flag, boolean condition) {
        if (!condition || (band.fired & flag) != 0) return false;
        band.fired |= flag;
        return true;
    }

    private void startLoops(Minecraft minecraft, Band band) {
        if (muted()) return;
        int playing = 0;
        for (Band other : bands) if (other.jet != null && !other.jet.isDonePlaying()) playing++;
        if (playing >= MAX_LOOP_BANDS) return;
        float gain = reach(band.position(band.local(age(0))));
        band.jet = new Loop(band, "jet_loop", true, 0.7F * gain);
        band.turbine = new Loop(band, "turbine_loop", false, 0.45F * gain);
        minecraft.getSoundHandler()
            .playSound(band.jet);
        minecraft.getSoundHandler()
            .playSound(band.turbine);
    }

    private static float size(Band band) {
        return (float) Math.min(5, 1 + band.maxEdge / 6);
    }

    private boolean near(Minecraft minecraft, Vec3d at, double extra) {
        EntityPlayer player = minecraft.thePlayer;
        if (player == null) return false;
        double reach = PARTICLE_RANGE + extra;
        return at.distanceToSqr(new Vec3d(player.posX, player.posY, player.posZ)) < reach * reach;
    }

    private void spawn(Minecraft minecraft, FxJetParticle.Kind kind, Vec3d at, double vx, double vy, double vz,
        float size) {
        if (muted()) return;
        minecraft.effectRenderer
            .addEffect(new FxJetParticle(minecraft.theWorld, kind, at.x(), at.y(), at.z(), vx, vy, vz, size));
    }

    private Vec3d[] nozzles(Band band, Vec3d at) {
        double hx = band.halfX * 0.8, hz = band.halfZ * 0.8;
        return new Vec3d[] { at.add(-hx, 0, -hz), at.add(hx, 0, -hz), at.add(-hx, 0, hz), at.add(hx, 0, hz) };
    }

    private Vec3d velocity(Band band, double l) {
        return band.position(l + 0.5)
            .subtract(band.position(l - 0.5));
    }

    private void exhaust(Minecraft minecraft, Band band, double l, double power, int flying) {
        Vec3d at = band.position(l);
        if (!near(minecraft, at, band.maxEdge)) return;
        int share = Math.max(4, budget / Math.max(1, flying));
        Vec3d velocity = velocity(band, l);
        double speed = Math.sqrt(velocity.distanceToSqr(Vec3d.ZERO));
        Vec3d back = speed > 1.0E-4 ? velocity.scale(-1 / speed) : new Vec3d(0, -1, 0);
        float size = size(band);
        double scale = Math.sqrt(size);
        int perNozzle = Math.max(1, (int) Math.round(Math.min(share, 4 + 8 * power) / 4.0));
        for (Vec3d nozzle : nozzles(band, at)) {
            for (int i = 0; i < perNozzle && budget > 0; i++, budget--) {
                FxJetParticle.Kind kind = i == 0 ? FxJetParticle.Kind.CORE
                    : i == perNozzle - 1 && perNozzle > 2 && RANDOM.nextBoolean() ? FxJetParticle.Kind.TRAIL
                        : FxJetParticle.Kind.THRUST;
                double v = (0.18 + RANDOM.nextDouble() * 0.12 + 0.15 * speed) * scale;
                spawn(
                    minecraft,
                    kind,
                    nozzle,
                    (back.x() * 0.6) * v + RANDOM.nextGaussian() * 0.03,
                    (back.y() * 0.6 - 0.5) * v,
                    (back.z() * 0.6) * v + RANDOM.nextGaussian() * 0.03,
                    size);
            }
        }
    }

    private void lift(Minecraft minecraft, Band band, Vec3d at) {
        if (!near(minecraft, at, band.maxEdge)) return;
        float size = size(band);
        for (Vec3d nozzle : nozzles(band, at)) {
            if (budget-- <= 0) return;
            spawn(
                minecraft,
                FxJetParticle.Kind.THRUST,
                nozzle,
                RANDOM.nextGaussian() * 0.02,
                -0.3 * Math.sqrt(size),
                RANDOM.nextGaussian() * 0.02,
                size);
        }
    }

    private void retro(Minecraft minecraft, Band band, double l, int count) {
        Vec3d at = band.position(l);
        if (!near(minecraft, at, band.maxEdge)) return;
        Vec3d velocity = velocity(band, l);
        double speed = Math.sqrt(velocity.distanceToSqr(Vec3d.ZERO));
        if (speed < 1.0E-4) return;
        Vec3d ahead = velocity.scale(1 / speed);
        float size = size(band);
        Vec3d front = at.add(ahead.scale(Math.max(band.halfX, band.halfZ)))
            .add(0, band.height / 2, 0);
        for (int i = 0; i < count && bursts > 0; i++, bursts--) spawn(
            minecraft,
            FxJetParticle.Kind.THRUST,
            front,
            ahead.x() * 0.35 + RANDOM.nextGaussian() * 0.05,
            ahead.y() * 0.35 + RANDOM.nextGaussian() * 0.05,
            ahead.z() * 0.35 + RANDOM.nextGaussian() * 0.05,
            size);
    }

    private void ring(Minecraft minecraft, Band band, Vec3d at) {
        if (!near(minecraft, at, band.maxEdge)) return;
        float size = size(band);
        for (int i = 0; i < 12 && bursts > 0; i++, bursts--) {
            double angle = Math.PI * 2 * i / 12;
            spawn(
                minecraft,
                FxJetParticle.Kind.TRAIL,
                at,
                Math.cos(angle) * 0.18 * size,
                0.02,
                Math.sin(angle) * 0.18 * size,
                size);
        }
    }

    private void sparks(Minecraft minecraft, Band band, int block) {
        double[] pivot = band.blockPivot[block];
        Vec3d at = blockDirLength(band, block) > 0 ? new Vec3d(pivot[0], pivot[1] + band.height / 2, pivot[2])
            : band.anchor.add(0, band.height / 2, 0);
        if (!near(minecraft, at, band.maxEdge)) return;
        float size = size(band);
        for (int i = 0; i < 8 && bursts > 0; i++, bursts--) spawn(
            minecraft,
            i < 2 ? FxJetParticle.Kind.CORE : FxJetParticle.Kind.THRUST,
            at,
            RANDOM.nextGaussian() * 0.18,
            RANDOM.nextGaussian() * 0.18,
            RANDOM.nextGaussian() * 0.18,
            size * 0.6F);
    }

    private static double blockDirLength(Band band, int block) {
        double[] dir = band.blockDir[block];
        return dir == null ? 0 : Math.hypot(dir[0], dir[1]);
    }

    private float reach(Vec3d at) {
        Minecraft minecraft = Minecraft.getMinecraft();
        double distance = minecraft.thePlayer == null ? 0
            : Math.sqrt(
                at.distanceToSqr(
                    new Vec3d(minecraft.thePlayer.posX, minecraft.thePlayer.posY, minecraft.thePlayer.posZ)));
        return (float) Math.max(1, Math.max(radius, distance) / 14);
    }

    private void play(Vec3d at, String name, float volume, float pitch) {
        if (muted()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.theWorld == null) return;
        minecraft.theWorld.playSound(at.x(), at.y(), at.z(), SOUND + name, volume * reach(at), pitch, false);
    }

    @Override
    protected void render(Minecraft minecraft, double age, float partial, double cameraX, double cameraY,
        double cameraZ) {
        FxCamera.capture();
        for (Band band : bands) {
            if (band.compiled || band.skipped || band.index >= preparedBands) continue;
            boolean complete = true;
            for (FxBlockMesh mesh : band.meshes)
                if (mesh != null && !mesh.compile(StructureAnimations.compileBudget())) complete = false;
            band.compiled = complete;
            if (!complete) break;
        }
        FxBlockMesh.solidState();
        for (Band band : bands) {
            if (band.skipped || band.launchAt < 0) continue;
            double l = band.local(age);
            Vec3d at = band.position(l);
            double reach = band.maxEdge + MAX_SHIFT + 1;
            if (!FxCamera.inView(at.add(0, band.height / 2, 0), reach)) continue;
            drawBand(band, l, at, cameraX, cameraY, cameraZ);
        }
    }

    private void drawBand(Band band, double l, Vec3d at, double cameraX, double cameraY, double cameraZ) {
        GL11.glPushMatrix();
        GL11.glTranslated(at.x() - cameraX, at.y() + band.height / 2 - cameraY, at.z() - cameraZ);
        pose(band, l);
        GL11.glTranslated(-band.anchor.x(), -band.anchor.y() - band.height / 2, -band.anchor.z());
        boolean landed = l >= band.arrival;
        if (landed) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -1);
        }
        double latch = l - band.snapEnd();
        double bandFlash = latch >= 0 && latch < BAND_FLASH ? 1 - latch / BAND_FLASH : 0;
        for (int block = 0; block < band.meshes.length; block++) {
            FxBlockMesh mesh = band.meshes[block];
            if (mesh == null) continue;
            GL11.glPushMatrix();
            unfold(band, block, band.open(block, l));
            GL11.glTranslated(offsetX, offsetY, offsetZ);
            drawMesh(mesh);
            double since = l - band.lockTime(block);
            double flash = Math.max(bandFlash, since >= 0 && since < FLASH_TICKS ? 1 - since / FLASH_TICKS : 0);
            if (flash > 0) mesh.drawFlash((float) (flash * 0.8), this::visible);
            GL11.glPopMatrix();
        }
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPopMatrix();
    }

    private void pose(Band band, double l) {
        double recoil = l >= band.snapEnd()
            ? FlightPath.recoil(l - band.snapEnd(), Math.min(RECOIL, RECOIL_EDGE / Math.max(1, band.maxEdge / 2)))
            : 0;
        if (recoil != 0) GL11.glScaled(1 + recoil, 1 + recoil, 1 + recoil);
        if (l < APPEAR) {
            double k = Math.max(0, l) / APPEAR;
            double grow = 0.2 + 0.8 * (1 - Math.pow(1 - k, 3));
            GL11.glScaled(grow, grow, grow);
            return;
        }
        if (l >= band.snapEnd()) return;
        double s = band.shrink, pitch = 0, roll = 0, yaw = 0;
        double travel = travel(band, l),
            turn = MathHelper.wrapAngleTo180_double(travel(band, l + 0.5) - travel(band, l - 0.5));
        if (band.flying(l)) {
            double t = (l - APPEAR) / band.flight;
            double settle = FlightPath.ease((t - FlightPath.BRAKE_AT) / (1 - FlightPath.BRAKE_AT));
            yaw = clamp(MathHelper.wrapAngleTo180_double(travel - band.heading), MAX_YAW) * (1 - settle) * s;
            double accel = t < 0.12 ? 1 - t / 0.12 : 0;
            double brake = t > FlightPath.BRAKE_AT
                ? Math.pow((t - FlightPath.BRAKE_AT) / (1 - FlightPath.BRAKE_AT), 1.5)
                : 0;
            pitch = (-ACCEL_PITCH * accel + BRAKE_PITCH * brake) * s;
            roll = clamp(turn * 4.5, MAX_ROLL) * (1 - settle) * s;
        } else if (l < band.snapStart()) {
            pitch = BRAKE_PITCH * Math.exp(-(l - band.hoverStart()) / 2) * s;
            travel = band.heading;
        } else travel = band.heading;
        pitch += JITTER * s * (Math.sin(l * 2.3) + 0.5 * Math.sin(l * 5.1 + 1.3));
        GL11.glRotated(travel, 0, 1, 0);
        GL11.glRotated(pitch, 1, 0, 0);
        GL11.glRotated(roll, 0, 0, 1);
        GL11.glRotated(-travel + yaw, 0, 1, 0);
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private double travel(Band band, double l) {
        Vec3d velocity = velocity(band, l);
        if (Math.abs(velocity.x()) + Math.abs(velocity.z()) < 1.0E-5) return band.heading;
        return Math.toDegrees(Math.atan2(velocity.x(), velocity.z()));
    }

    private static void unfold(Band band, int block, double open) {
        if (open <= 0 || blockDirLength(band, block) <= 0) return;
        double[] dir = band.blockDir[block], pivot = band.blockPivot[block];
        double shift = band.blockShift[block] * open;
        GL11.glTranslated(dir[0] * shift, LIFT * open, dir[1] * shift);
        GL11.glTranslated(pivot[0], pivot[1], pivot[2]);
        GL11.glRotated(band.blockTilt[block] * open, dir[1], 0, -dir[0]);
        GL11.glTranslated(-pivot[0], -pivot[1], -pivot[2]);
    }

}
