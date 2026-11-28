package com.science.gtnl.client.structureanimation;

import java.nio.FloatBuffer;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import com.gtnllib.client.fx.world.FxBlockMesh;
import com.gtnllib.client.fx.world.FxCameraShake;
import com.gtnllib.client.fx.world.FxCrystalMesh;
import com.gtnllib.client.fx.world.FxFractalLightning;
import com.gtnllib.client.fx.world.FxMistWave;
import com.gtnllib.client.fx.world.FxMoteRenderer;
import com.gtnllib.client.fx.world.FxMoteRenderer.Kind;
import com.gtnllib.client.fx.world.FxMoteRenderer.Mote;
import com.gtnllib.client.fx.world.FxSpatialLens;
import com.gtnllib.client.fx.world.FxVisibility;
import com.gtnllib.client.fx.world.FxVoxelOutline;
import com.gtnllib.utils.Vec3d;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@SideOnly(Side.CLIENT)
public class StructureWarpInAnimation extends StructureMeshTask {

    public static final int TEAR_TICKS = StructureAnimationStyle.WARP_IN_TEAR,
        WORMHOLE_TICKS = StructureAnimationStyle.WARP_IN_WORMHOLE, NEEDLE_TICKS = 10, SNAP_TICKS = 20, MAX_BOLTS = 56,
        OUTLINE_CELL_LIMIT = 20_000, PSIONIC_BLUE = 0x58B4FF, PSIONIC_CORE = 0xFFFFFF, TEAR_CUE_TICK = 1,
        DIM_RGB = 0x050810, FIRST_ARC_DELAY = 4, AFTERGLOW_TICKS = 30, BURST_TICKS = 8;
    public static final double COLLAPSE_AT = 0.78, FLASH_CORE = 0.35, REVEAL_START = 0.16, REVEAL_END = 0.88,
        WHITEOUT_CAP = 0.35, CORE_HOLD = 0.55, HORIZONTAL_DIM = 0.3, DIM = 0.28, DIM_REACH = 24, DIM_FADE = 40,
        BURST_ALPHA = 0.7, AFTERGLOW = 0.3, FLASH_HOLD = 0.35, OTHERS_TINT = 0.5;
    public static final float SHAKE_MIN = 2.4F, SHAKE_MAX = 5.8F, MIST_DENSITY = 1.7F;
    public static final String SOUND = "sciencenotleisure:warp_in.";

    private static final int LIGHTNING_PRIMARY = FxFractalLightning.DEFAULT_PRIMARY_RGB,
        LIGHTNING_SECONDARY = FxFractalLightning.DEFAULT_SECONDARY_RGB,
        LIGHTNING_CORE = FxFractalLightning.DEFAULT_CORE_RGB, LIGHTNING_GLOW = FxFractalLightning.DEFAULT_GLOW_RGB;
    private static final int LENS_VOID = FxSpatialLens.DEFAULT_VOID_RGB,
        LENS_PRIMARY = FxSpatialLens.DEFAULT_PRIMARY_RGB, LENS_HIGHLIGHT = FxSpatialLens.DEFAULT_HIGHLIGHT_RGB,
        LENS_MIST = FxSpatialLens.DEFAULT_MIST_RGB, RIPPLE_PRIMARY = FxSpatialLens.DEFAULT_RIPPLE_PRIMARY_RGB,
        RIPPLE_HIGHLIGHT = FxSpatialLens.DEFAULT_RIPPLE_HIGHLIGHT_RGB;
    private static final int MIST_DARK = FxMistWave.DEFAULT_DARK_RGB, MIST_MID = FxMistWave.DEFAULT_MID_RGB,
        MIST_CREST = FxMistWave.DEFAULT_CREST_RGB;
    private static final int CRYSTAL_SHADOW = FxCrystalMesh.DEFAULT_SHADOW_RGB,
        CRYSTAL_PRIMARY = FxCrystalMesh.DEFAULT_PRIMARY_RGB, CRYSTAL_HIGHLIGHT = FxCrystalMesh.DEFAULT_HIGHLIGHT_RGB,
        WIRE_PRIMARY = FxCrystalMesh.DEFAULT_WIRE_PRIMARY_RGB;

    private static final FxCameraShake.Source SHAKE = new FxCameraShake.Source();
    private static final FloatBuffer MODELVIEW = BufferUtils.createFloatBuffer(16);
    private static final Random RANDOM = new Random();
    public static final double MOTE_SCALE_MIN = 0.45, MOTE_FULL_RADIUS = 10;

    public final int materializeTicks, wormholeEnd, collapseTick;
    public final double centerY, sphereRadius, holeWorldRadius, moteScale;
    private final float localCx, localCy, localCz;
    private float[] wireLines;
    private final FxFractalLightning lightning = new FxFractalLightning(MAX_BOLTS);
    public FxBlockMesh mesh;
    public long snappedTick = -1;
    public boolean tearCue, wormholeCue, collapseCue, wireframeCue;
    private int verticalList, horizontalList, nextArcSoundTick;
    private double lastAge;
    private double camRx = 1, camRy, camRz, camUx, camUy = 1, camUz;

    public StructureWarpInAnimation(Start start, ByteBuf extra) {
        super(start);
        materializeTicks = Math.max(1, extra.readShort());
        wormholeEnd = TEAR_TICKS + WORMHOLE_TICKS;
        collapseTick = TEAR_TICKS + (int) Math.round(WORMHOLE_TICKS * COLLAPSE_AT);
        centerY = start.originY() + start.sizeY() * 0.52;
        localCx = (float) (centerX - offsetX);
        localCy = (float) (centerY - offsetY);
        localCz = (float) (centerZ - offsetZ);
        double diag = Math
            .sqrt(start.sizeX() * start.sizeX() + start.sizeY() * start.sizeY() + start.sizeZ() * start.sizeZ());
        sphereRadius = Math.max(2.2, diag * 0.56 + 0.8);
        holeWorldRadius = Math.max(1.25, Math.min(15.0, diag * 0.21 + 0.40));
        moteScale = Math.max(MOTE_SCALE_MIN, Math.min(1, radius / MOTE_FULL_RADIUS));

        int[] cells = new int[code.length];
        for (int i = 0; i < cells.length; i++) cells[i] = i;
        if (code.length <= OUTLINE_CELL_LIMIT) {
            LongOpenHashSet blockSet = new LongOpenHashSet(code.length);
            for (int i = 0; i < cells.length; i++)
                blockSet.add(FxVoxelOutline.pack(wx[i] - offsetX, wy[i] - offsetY, wz[i] - offsetZ));
            wireLines = FxVoxelOutline.mergedLines(blockSet);
        }
        mesh = mesh(cells);

        // Late viewers join with the clock advanced; cues already past must stay silent.
        int elapsed = start.elapsed();
        tearCue = elapsed > TEAR_CUE_TICK;
        wormholeCue = elapsed > TEAR_TICKS;
        collapseCue = elapsed > collapseTick;
        wireframeCue = elapsed > wormholeEnd;
        nextArcSoundTick = elapsed + FIRST_ARC_DELAY;
        int total = wormholeEnd + materializeTicks;
        if (elapsed > total) snappedTick = startTick + total;
    }

    public double materialize(double age) {
        double p = Math.max(0, Math.min(1, (age - wormholeEnd) / materializeTicks));
        if (placingTick >= 0) p = Math.max(p, Math.min(1, (age - (placingTick - startTick)) / 2));
        return p;
    }

    @Override
    protected void placedInCopy(int placed) {}

    @Override
    protected boolean settled(long section) {
        return snappedTick >= 0 && now() - snappedTick >= SNAP_TICKS;
    }

    @Override
    protected boolean busy() {
        return snappedTick < 0 || now() - snappedTick < Math.max(SNAP_TICKS, AFTERGLOW_TICKS);
    }

    @Override
    protected void releaseOwn() {
        if (mesh != null) mesh.release();
        mesh = null;
        if (verticalList != 0) {
            GL11.glDeleteLists(verticalList, 2);
            verticalList = horizontalList = 0;
        }
        wireLines = null;
        lightning.clear();
    }

    @Override
    protected void rewind() {
        if (snappedTick > now()) snappedTick = -1;
    }

    @Override
    protected void animate(Minecraft minecraft, double age) {
        if (snappedTick >= 0) return;
        lightning.prune(age);
        double mat = materialize(age);
        if (mat >= 1) {
            snappedTick = now();
            lightning.clear();
            sound(centerX, centerY, centerZ, SOUND + "snap", 1.35F, 1.0F);
            shake(SHAKE, (float) Math.min(SHAKE_MAX, SHAKE_MIN + radius * 0.18), 0.48F);
            spawnShockwaveSparks();
            return;
        }
        int tick = (int) age;
        if (!tearCue && age >= TEAR_CUE_TICK) {
            tearCue = true;
            sound(centerX, centerY, centerZ, SOUND + "tear", 0.95F, 1.0F);
        }
        if (!wormholeCue && age >= TEAR_TICKS) {
            wormholeCue = true;
            lightning.clear();
            nextArcSoundTick = Math.max(nextArcSoundTick, tick + 5);
            sound(centerX, centerY, centerZ, SOUND + "rift", 1.15F, 1.0F);
            shake(SHAKE, (float) Math.min(SHAKE_MAX * 0.65, 1.4 + radius * 0.10), 0.35F);
        }
        if (!collapseCue && age >= collapseTick && age < wormholeEnd) {
            collapseCue = true;
            sound(centerX, centerY, centerZ, SOUND + "collapse", 1.10F, 1.0F);
        }
        if (!wireframeCue && age >= wormholeEnd) {
            wireframeCue = true;
            nextArcSoundTick = Math.max(nextArcSoundTick, tick + 6);
            float matPitch = Math.max(0.82F, Math.min(1.15F, 36.0F / materializeTicks));
            sound(centerX, centerY, centerZ, SOUND + "materialize", 1.10F, matPitch);
            shake(SHAKE, (float) Math.min(SHAKE_MAX * 0.45, 1.0 + radius * 0.08), 0.25F);
        }

        if (age < TEAR_TICKS) {
            if (tick % 2 == 0) spawnTearLightning(age);
            spawnInwardSparks(0.70);
        } else if (age < wormholeEnd) {
            spawnWormholeLightning(age);
        } else {
            spawnMaterializeLightning(age, mat);
            spawnEnergySparks(mat);
        }
        if (tick >= nextArcSoundTick && !lightning.isEmpty()
            && (age < TEAR_TICKS || age < TEAR_TICKS + WORMHOLE_TICKS * 0.72 || age >= wormholeEnd)) {
            nextArcSoundTick = tick + 6 + RANDOM.nextInt(5);
            sound(centerX, centerY, centerZ, SOUND + "arc", 0.42F, 0.90F + RANDOM.nextFloat() * 0.25F);
        }
    }

    private void spawnTearLightning(double age) {
        if (muted()) return;
        double reach = Math.max(2.4, holeWorldRadius * 1.48);
        double floorY = start.originY() + 0.04;
        for (int i = 0; i < 3; i++) {
            double a1 = RANDOM.nextDouble() * Math.PI * 2;
            double e1 = (RANDOM.nextDouble() - 0.35) * 1.15;
            double x1 = centerX + Math.cos(a1) * Math.cos(e1) * reach;
            double y1 = Math.max(start.originY() + 0.15, centerY + Math.sin(e1) * reach);
            double z1 = centerZ + Math.sin(a1) * Math.cos(e1) * reach;
            lightning.spawn(
                centerX + (RANDOM.nextDouble() - 0.5) * 0.4,
                centerY + (RANDOM.nextDouble() - 0.5) * 0.5,
                centerZ + (RANDOM.nextDouble() - 0.5) * 0.4,
                x1,
                y1,
                z1,
                4,
                0.42,
                (float) Math.max(0.28, Math.min(0.58, holeWorldRadius * 0.115)),
                age,
                4.5 + RANDOM.nextDouble() * 2.0,
                1,
                floorY);
        }
    }

    private void spawnWormholeLightning(double age) {
        if (muted()) return;
        double u = (age - TEAR_TICKS) / Math.max(1, WORMHOLE_TICKS);
        if (u > 0.72) return;
        double rimR = holeWorldRadius * (1.03 + 0.03 * Math.sin(age * 0.45));
        double floorY = start.originY() + 0.04;
        int tick = (int) age;

        if (tick % 2 == 0) {
            int bursts = RANDOM.nextInt(3) == 0 ? 1 : 2;
            for (int i = 0; i < bursts; i++) {
                double ang = RANDOM.nextDouble() * Math.PI * 2;
                double ca = Math.cos(ang), sa = Math.sin(ang);
                double dx = camRx * ca + camUx * sa;
                double dy = camRy * ca + camUy * sa;
                double dz = camRz * ca + camUz * sa;
                double sx = centerX + dx * rimR;
                double sy = Math.max(start.originY() + 0.25, centerY + dy * rimR);
                double sz = centerZ + dz * rimR;
                double len = rimR * (0.72 + RANDOM.nextDouble() * 0.75);
                double jitter = 0.48;
                lightning.spawn(
                    sx,
                    sy,
                    sz,
                    sx + (dx + (RANDOM.nextDouble() - 0.5) * jitter) * len,
                    Math.max(start.originY() + 0.08, sy + (dy + (RANDOM.nextDouble() - 0.5) * jitter) * len),
                    sz + (dz + (RANDOM.nextDouble() - 0.5) * jitter) * len,
                    4,
                    0.44,
                    (float) Math.max(0.24, Math.min(0.46, holeWorldRadius * 0.095)),
                    age,
                    3.5 + RANDOM.nextDouble() * 2.5,
                    1,
                    floorY);
            }
        }

        if (tick % 3 == 0) {
            double side = (RANDOM.nextDouble() - 0.5) * 0.42;
            double sx = centerX + camRx * side * rimR - camUx * rimR * 0.98;
            double sy = Math.max(start.originY() + 0.8, centerY - rimR * 0.98);
            double sz = centerZ + camRz * side * rimR - camUz * rimR * 0.98;
            double gSpread = rimR * 0.90;
            lightning.spawn(
                sx,
                sy,
                sz,
                centerX + (RANDOM.nextDouble() - 0.5) * gSpread * 2.0,
                start.originY() + 0.05,
                centerZ + (RANDOM.nextDouble() - 0.5) * gSpread * 2.0,
                4,
                0.46,
                (float) Math.max(0.28, Math.min(0.54, holeWorldRadius * 0.11)),
                age,
                4.5 + RANDOM.nextDouble() * 2.0,
                1,
                floorY);
        }
    }

    private void spawnMaterializeLightning(double age, double mat) {
        if (muted()) return;
        int tick = (int) age;
        if (tick % 2 != 0) return;
        double floorY = start.originY() + 0.04;
        int bursts = mat > 0.65 ? 3 : 2;
        for (int i = 0; i < bursts; i++) {
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            double r0 = radius * (0.35 + RANDOM.nextDouble() * 0.52);
            double x0 = centerX + Math.cos(ang) * r0;
            double z0 = centerZ + Math.sin(ang) * r0;
            if (RANDOM.nextFloat() < 0.75F) {
                double y0 = start.originY() + start.sizeY() * (0.20 + RANDOM.nextDouble() * 0.45);
                double r1 = radius * (0.78 + RANDOM.nextDouble() * 0.42);
                double ang2 = ang + (RANDOM.nextDouble() - 0.5) * 0.65;
                lightning.spawn(
                    x0,
                    y0,
                    z0,
                    centerX + Math.cos(ang2) * r1,
                    start.originY() + 0.05,
                    centerZ + Math.sin(ang2) * r1,
                    4,
                    0.42,
                    (float) Math.max(0.18, Math.min(0.38, radius * 0.060)),
                    age,
                    4.0 + RANDOM.nextDouble() * 2.5,
                    1,
                    floorY);
            } else {
                double y0 = start.originY() + start.sizeY() * (0.18 + RANDOM.nextDouble() * 0.62);
                double ang2 = ang + (RANDOM.nextBoolean() ? 1 : -1) * (0.55 + RANDOM.nextDouble() * 0.75);
                double r1 = radius * (0.45 + RANDOM.nextDouble() * 0.45);
                double y1 = Math.max(
                    start.originY() + 0.2,
                    Math.min(
                        start.originY() + start.sizeY() * 0.92,
                        y0 + (RANDOM.nextDouble() - 0.5) * start.sizeY() * 0.45));
                lightning.spawn(
                    x0,
                    y0,
                    z0,
                    centerX + Math.cos(ang2) * r1,
                    y1,
                    centerZ + Math.sin(ang2) * r1,
                    3,
                    0.38,
                    (float) Math.max(0.15, Math.min(0.32, radius * 0.050)),
                    age,
                    3.5 + RANDOM.nextDouble() * 2.0,
                    1,
                    floorY);
            }
        }
    }

    private void spawnInwardSparks(double intensity) {
        if (muted()) return;
        int count = Math.max(2, Math.min(10, particles((int) (4 + holeWorldRadius * 1.4))));
        for (int i = 0; i < count; i++) {
            double theta = RANDOM.nextDouble() * Math.PI * 2;
            double phi = (RANDOM.nextDouble() - 0.35) * 1.4;
            double dist = holeWorldRadius * (1.8 + RANDOM.nextDouble() * 1.6);
            double ox = centerX + Math.cos(theta) * Math.cos(phi) * dist;
            double oy = Math.max(start.originY() + 0.12, centerY + Math.sin(phi) * dist * 0.75);
            double oz = centerZ + Math.sin(theta) * Math.cos(phi) * dist;
            double pull = 0.14 * intensity;
            Vec3d vel = new Vec3d(
                (centerX - ox) / dist * pull - Math.sin(theta) * 0.04,
                (centerY - oy) / dist * pull,
                (centerZ - oz) / dist * pull + Math.cos(theta) * 0.04);
            FxMoteRenderer.spawn(
                new Mote(
                    Kind.DOT,
                    new Vec3d(ox, oy, oz),
                    (0.12 + RANDOM.nextDouble() * 0.10) * moteScale,
                    10 + RANDOM.nextInt(8),
                    PSIONIC_BLUE,
                    PSIONIC_CORE).velocity(vel)
                        .smooth()
                        .fall(0.95, 0)
                        .twinkle(RANDOM.nextFloat() * 6.28F),
                0);
        }
    }

    private void spawnEnergySparks(double mat) {
        if (muted()) return;
        int count = Math.max(2, Math.min(12, particles((int) (4 + radius * 1.5))));
        for (int i = 0; i < count; i++) {
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            double r = radius * (0.45 + RANDOM.nextDouble() * 0.65);
            Vec3d origin = new Vec3d(
                centerX + Math.cos(ang) * r,
                start.originY() + RANDOM.nextDouble() * start.sizeY(),
                centerZ + Math.sin(ang) * r);
            Vec3d vel = new Vec3d(
                Math.cos(ang) * (0.02 + 0.04 * mat),
                0.05 + RANDOM.nextDouble() * 0.08,
                Math.sin(ang) * (0.02 + 0.04 * mat));
            FxMoteRenderer.spawn(
                new Mote(
                    Kind.DOT,
                    origin,
                    (0.14 + RANDOM.nextDouble() * 0.12) * moteScale,
                    10 + RANDOM.nextInt(8),
                    i % 2 == 0 ? PSIONIC_CORE : PSIONIC_BLUE,
                    PSIONIC_CORE).velocity(vel)
                        .smooth()
                        .fall(0.94, -0.001)
                        .twinkle(RANDOM.nextFloat() * 6.28F),
                0);
        }
    }

    private void spawnShockwaveSparks() {
        if (muted()) return;
        int count = Math.max(12, Math.min(42, particles((int) (14 + radius * 4.5))));
        for (int i = 0; i < count; i++) {
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            double speed = 0.28 + RANDOM.nextDouble() * 0.36;
            Vec3d origin = new Vec3d(
                centerX + Math.cos(ang) * radius * 0.55,
                start.originY() + 0.18 + RANDOM.nextDouble() * 0.8,
                centerZ + Math.sin(ang) * radius * 0.55);
            Vec3d vel = new Vec3d(Math.cos(ang) * speed, 0.03 + RANDOM.nextDouble() * 0.10, Math.sin(ang) * speed);
            FxMoteRenderer.spawn(
                new Mote(
                    Kind.DOT,
                    origin,
                    (0.16 + RANDOM.nextDouble() * 0.14) * moteScale,
                    12 + RANDOM.nextInt(10),
                    PSIONIC_BLUE,
                    PSIONIC_CORE).velocity(vel)
                        .smooth()
                        .fall(0.90, 0.003)
                        .twinkle(RANDOM.nextFloat() * 6.28F),
                0);
        }
    }

    @Override
    public float glow(double age) {
        if (snappedTick >= 0) return 0;
        if (age < TEAR_TICKS) return (float) Math.min(1, age / 3.0) * 0.78F;
        if (age < wormholeEnd) return 0.58F;
        double mat = materialize(age);
        return (float) (0.48 + smoothstep(0.55, 0.99, mat) * 0.50);
    }

    @Override
    protected void drawGlow(double cameraX, double cameraY, double cameraZ) {
        if (snappedTick >= 0) return;
        GL11.glPushMatrix();
        GL11.glTranslated(-cameraX, -cameraY, -cameraZ);

        lightning.drawGlowCores(lastAge, cameraX, cameraY, cameraZ, 0.36F, LIGHTNING_GLOW);

        if (lastAge >= wormholeEnd && mesh != null) {
            double mat = materialize(lastAge);
            float meshBloom = (float) (smoothstep(0.38, 0.75, mat) * 0.25 + smoothstep(0.74, 0.99, mat) * 0.30);
            FxCrystalMesh.wire(() -> mesh.draw(this::visible))
                .at(offsetX, offsetY, offsetZ)
                .bounds(localCx, localCy, localCz, start.sizeX(), start.sizeY(), start.sizeZ())
                .time((float) (lastAge / 20.0))
                .strength(meshBloom)
                .whiteCore(0.45F + (float) smoothstep(0.72, 0.99, mat) * 0.35F)
                .colors(WIRE_PRIMARY, CRYSTAL_HIGHLIGHT)
                .drawGlow();
        }
        GL11.glPopMatrix();
    }

    @Override
    protected void render(Minecraft minecraft, double age, float partial, double cameraX, double cameraY,
        double cameraZ) {
        lastAge = age;
        MODELVIEW.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, MODELVIEW);
        camRx = MODELVIEW.get(0);
        camRy = MODELVIEW.get(4);
        camRz = MODELVIEW.get(8);
        camUx = MODELVIEW.get(1);
        camUy = MODELVIEW.get(5);
        camUz = MODELVIEW.get(9);
        if (mesh != null) mesh.compile(StructureAnimations.compileBudget(), this::placed);

        float timeSec = (float) (age / 20.0);
        double mat = materialize(age);
        double flashCore = holeWorldRadius * FLASH_CORE;
        double dx = cameraX - centerX, dy = cameraY - centerY, dz = cameraZ - centerZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double sinceSnap = snappedTick < 0 ? -1 : age - (snappedTick - startTick);
        double dim = DIM * (1.0 - smoothstep(radius + DIM_REACH, radius + DIM_REACH + DIM_FADE, distance))
            * smoothstep(0, TEAR_TICKS, age)
            * (sinceSnap < 0 ? 1.0 : 1.0 - smoothstep(0, AFTERGLOW_TICKS, sinceSnap));
        if (dim > 0.005) dim *= tintScale(minecraft, cameraX, cameraY, cameraZ);
        if (dim > 0.005) screenTint(DIM_RGB, dim);

        if (snappedTick < 0 && age >= wormholeEnd) {
            double reveal = smoothstep(REVEAL_START, REVEAL_END, mat);
            double skinFill = smoothstep(REVEAL_START, 0.45, mat);
            double whitePeak = smoothstep(0.80, 0.99, mat) * WHITEOUT_CAP;
            double wireStr = smoothstep(0.0, 0.06, mat) * (1.0 - smoothstep(0.80, 0.97, mat));
            if (mesh != null && skinFill > 0.01) {
                FxCrystalMesh.surface(() -> mesh.draw(this::visible))
                    .at(offsetX - cameraX, offsetY - cameraY, offsetZ - cameraZ)
                    .bounds(localCx, localCy, localCz, start.sizeX(), start.sizeY(), start.sizeZ())
                    .time(timeSec)
                    .fill((float) skinFill)
                    .reveal((float) reveal)
                    .whiteout((float) whitePeak)
                    .colors(CRYSTAL_SHADOW, CRYSTAL_PRIMARY, CRYSTAL_HIGHLIGHT)
                    .draw();
            }
            if (wireStr > 0.01) {
                compileWireLists();
                drawWires(
                    horizontalList,
                    wireStr * HORIZONTAL_DIM,
                    2.0F,
                    whitePeak,
                    timeSec,
                    cameraX,
                    cameraY,
                    cameraZ);
                drawWires(verticalList, wireStr, 3.0F, whitePeak, timeSec, cameraX, cameraY, cameraZ);
            }
            lightning
                .draw(age, cameraX, cameraY, cameraZ, 1.05F, LIGHTNING_PRIMARY, LIGHTNING_SECONDARY, LIGHTNING_CORE);

            double needleT = Math.min(1.0, (age - wormholeEnd) / NEEDLE_TICKS);
            double needle = Math.pow(1.0 - needleT, 2.2);
            double coreHold = CORE_HOLD * (1.0 - smoothstep(0.55, REVEAL_END, mat));
            double core = Math.max(needle, coreHold);
            if (core > 0.01) {
                FxSpatialLens
                    .pinpointFlash(centerX, centerY, centerZ, sphereRadius * (1.2 + 0.6 * reveal + 0.9 * needle))
                    .time(timeSec)
                    .strength((float) (core * 1.35))
                    .spike((float) core)
                    .colors(LENS_MIST, LENS_HIGHLIGHT)
                    .clearSight(clearSight(minecraft, flashCore, cameraX, cameraY, cameraZ))
                    .camera(cameraX, cameraY, cameraZ)
                    .draw();
            }
            return;
        }

        if (snappedTick < 0 && age < TEAR_TICKS) {
            double tearU = age / TEAR_TICKS;
            double sparkPulse = (0.45 + 0.55 * tearU) * (0.82 + 0.18 * Math.sin(age * 2.8));
            FxSpatialLens.pinpointFlash(centerX, centerY, centerZ, holeWorldRadius * (0.75 + 0.65 * tearU))
                .time(timeSec)
                .strength((float) sparkPulse)
                .spike((float) (0.35 + 0.55 * tearU))
                .colors(LENS_MIST, LENS_HIGHLIGHT)
                .clearSight(clearSight(minecraft, flashCore, cameraX, cameraY, cameraZ))
                .camera(cameraX, cameraY, cameraZ)
                .draw();
            lightning
                .draw(age, cameraX, cameraY, cameraZ, 1.05F, LIGHTNING_PRIMARY, LIGHTNING_SECONDARY, LIGHTNING_CORE);
            return;
        }

        if (snappedTick < 0) {
            double u = (age - TEAR_TICKS) / Math.max(1, WORMHOLE_TICKS);
            if (u < 0.85) {
                double open = smoothstep(0.0, 0.16, u);
                double pinch = u > 0.68 ? (u - 0.68) / 0.17 : 0.0;
                double breath = 1.0 + 0.03 * Math.sin(age * 0.42);
                double curHoleR = holeWorldRadius * open * breath * Math.max(0.0, 1.0 - pinch * pinch);
                double quadR = holeWorldRadius * 2.55;
                float strength = (float) ((0.50 + 0.85 * open) * (1.0 + 0.55 * pinch)
                    * (1.0 - smoothstep(0.79, 0.85, u)));
                float openTint = (float) (1.0 - smoothstep(0.02, 0.24, u));
                float openFlash = (float) (u < 0.22 ? Math.pow(1.0 - u / 0.22, 1.8) : 0.0);

                FxSpatialLens.wormhole(centerX, centerY, centerZ, curHoleR)
                    .lensRadius(quadR)
                    .time(timeSec)
                    .strength(strength)
                    .open(openTint, openFlash)
                    .colors(LENS_VOID, LENS_PRIMARY, LENS_HIGHLIGHT)
                    .clearSight(clearSight(minecraft, curHoleR, cameraX, cameraY, cameraZ))
                    .camera(cameraX, cameraY, cameraZ)
                    .draw();
                lightning.draw(
                    age,
                    cameraX,
                    cameraY,
                    cameraZ,
                    1.05F,
                    LIGHTNING_PRIMARY,
                    LIGHTNING_SECONDARY,
                    LIGHTNING_CORE);
            }

            if (u >= COLLAPSE_AT) {
                double nU = (u - COLLAPSE_AT) / (1.0 - COLLAPSE_AT);
                FxSpatialLens.pinpointFlash(centerX, centerY, centerZ, sphereRadius * (1.65 + 0.45 * nU))
                    .time(timeSec)
                    .strength((float) (smoothstep(0.0, 0.35, nU) * 1.35))
                    .spike(1.0F)
                    .colors(LENS_MIST, LENS_HIGHLIGHT)
                    .clearSight(clearSight(minecraft, flashCore, cameraX, cameraY, cameraZ))
                    .camera(cameraX, cameraY, cameraZ)
                    .draw();
            }
            return;
        }

        drawSolidFinal(age, cameraX, cameraY, cameraZ);
        double snapU = Math.max(0, Math.min(1, (age - (snappedTick - startTick)) / SNAP_TICKS));
        if (snapU < 1.0) {
            float mistProgress = (float) Math.pow(snapU, 0.45);
            FxMistWave.wave(centerX, start.originY(), centerZ, radius, mistRadius())
                .puffs(28)
                .time(timeSec)
                .progress(mistProgress)
                .strength(MIST_DENSITY)
                .colors(MIST_DARK, MIST_MID, MIST_CREST)
                .camera(cameraX, cameraY, cameraZ)
                .draw();
            FxSpatialLens
                .ripple(centerX, centerY, centerZ, sphereRadius * 3.6, (float) (0.06 + Math.pow(snapU, 0.58) * 0.88))
                .time(timeSec)
                .strength((float) Math.pow(1.0 - snapU, 1.15) * 1.48F)
                .colors(RIPPLE_PRIMARY, RIPPLE_HIGHLIGHT)
                .clearSight(clearSight(minecraft, radius, cameraX, cameraY, cameraZ))
                .camera(cameraX, cameraY, cameraZ)
                .draw();
        }
        if (sinceSnap < BURST_TICKS) {
            double envelope = sinceSnap < 1 ? sinceSnap : Math.pow(1.0 - (sinceSnap - 1) / (BURST_TICKS - 1), 1.5);
            double burst = BURST_ALPHA * envelope * (1.0 - smoothstep(mistRadius(), mistRadius() * 1.6, distance));
            if (burst > 0.005) burst *= tintScale(minecraft, cameraX, cameraY, cameraZ);
            if (burst > 0.005) screenTint(MIST_CREST, burst);
        }
    }

    private double mistRadius() {
        return Math.max(10.0, radius * 4.5 + 8.0);
    }

    private double tintScale(Minecraft minecraft, double cameraX, double cameraY, double cameraZ) {
        if (!clearSight(minecraft, radius, cameraX, cameraY, cameraZ)) return 0;
        return start.own() ? 1.0 : OTHERS_TINT;
    }

    private static void screenTint(int rgb, double alpha) {
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(
                ((rgb >> 16) & 0xFF) / 255F,
                ((rgb >> 8) & 0xFF) / 255F,
                (rgb & 0xFF) / 255F,
                (float) Math.min(1, alpha));
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glVertex3f(-1, -1, 0);
            GL11.glVertex3f(1, -1, 0);
            GL11.glVertex3f(1, 1, 0);
            GL11.glVertex3f(-1, 1, 0);
            GL11.glEnd();
        } finally {
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPopAttrib();
        }
    }

    private boolean clearSight(Minecraft minecraft, double clearance, double cameraX, double cameraY, double cameraZ) {
        return FxVisibility.hasLineOfSight(minecraft, centerX, centerY, centerZ, clearance, cameraX, cameraY, cameraZ);
    }

    private void drawSolidFinal(double age, double cameraX, double cameraY, double cameraZ) {
        if (mesh == null) return;
        GL11.glPushMatrix();
        GL11.glTranslated(offsetX - cameraX, offsetY - cameraY, offsetZ - cameraZ);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(-1, -1);
        FxBlockMesh.solidState();
        drawMesh(mesh);
        double sinceSnap = age - (snappedTick - startTick);
        float flash = (float) (1.0 - smoothstep(FLASH_HOLD, 1.0, sinceSnap / SNAP_TICKS)) * 0.85F;
        float afterglow = (float) (AFTERGLOW * Math.pow(Math.max(0, 1 - sinceSnap / AFTERGLOW_TICKS), 1.6));
        float glow = Math.max(flash, afterglow);
        if (glow > 0.005F) drawLockRimFlash(glow);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPopMatrix();
    }

    private void drawLockRimFlash(float strength) {
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_CONSTANT_COLOR, GL11.GL_ONE);
        GL14.glBlendColor(strength * 0.55F, strength * 0.88F, strength, 1F);
        GL11.glDepthFunc(GL11.GL_EQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_FOG);
        mesh.draw(this::visible);
        GL11.glEnable(GL11.GL_FOG);
        GL11.glDepthMask(true);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDisable(GL11.GL_BLEND);
        GL14.glBlendColor(0, 0, 0, 0);
    }

    private void drawWires(int list, double strength, float width, double whitePeak, float timeSec, double cameraX,
        double cameraY, double cameraZ) {
        FxCrystalMesh.wire(() -> GL11.glCallList(list))
            .at(offsetX - cameraX, offsetY - cameraY, offsetZ - cameraZ)
            .bounds(localCx, localCy, localCz, start.sizeX(), start.sizeY(), start.sizeZ())
            .time(timeSec)
            .strength((float) strength)
            .whiteCore((float) (0.55 + whitePeak))
            .lineWidth(width)
            .colors(WIRE_PRIMARY, CRYSTAL_HIGHLIGHT)
            .drawWireframe();
    }

    private void compileWireLists() {
        if (verticalList != 0) return;
        verticalList = GL11.glGenLists(2);
        horizontalList = verticalList + 1;
        compileWires(verticalList, true);
        compileWires(horizontalList, false);
        wireLines = null;
    }

    private void compileWires(int list, boolean vertical) {
        GL11.glNewList(list, GL11.GL_COMPILE);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);
        if (wireLines != null && wireLines.length > 0) {
            for (int i = 0; i + 5 < wireLines.length; i += 6) {
                boolean upright = wireLines[i] == wireLines[i + 3] && wireLines[i + 2] == wireLines[i + 5];
                if (upright != vertical) continue;
                t.addVertex(wireLines[i], wireLines[i + 1], wireLines[i + 2]);
                t.addVertex(wireLines[i + 3], wireLines[i + 4], wireLines[i + 5]);
            }
        } else {
            double minX = start.originX() - offsetX, minY = start.originY() - offsetY, minZ = start.originZ() - offsetZ;
            double maxX = minX + start.sizeX(), maxY = minY + start.sizeY(), maxZ = minZ + start.sizeZ();
            for (int corner = 0; corner < 4; corner++) {
                double a = (corner & 1) == 0 ? minX : maxX, b = (corner & 2) == 0 ? minZ : maxZ;
                if (vertical) {
                    t.addVertex(a, minY, b);
                    t.addVertex(a, maxY, b);
                    continue;
                }
                double y = (corner & 1) == 0 ? minY : maxY, side = (corner & 2) == 0 ? minZ : maxZ;
                t.addVertex(minX, y, side);
                t.addVertex(maxX, y, side);
                double edgeX = (corner & 2) == 0 ? minX : maxX;
                t.addVertex(edgeX, y, minZ);
                t.addVertex(edgeX, y, maxZ);
            }
        }
        t.draw();
        GL11.glEndList();
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double u = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return u * u * (3 - 2 * u);
    }
}
