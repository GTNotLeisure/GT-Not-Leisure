package com.science.gtnl.client.structureanimation;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.lwjgl.opengl.GL11;

import com.gtnllib.client.fx.world.FxBlockMesh;
import com.gtnllib.client.fx.world.FxCameraShake;
import com.gtnllib.client.fx.world.FxJetParticle;
import com.gtnllib.client.fx.world.FxMoteRenderer;
import com.gtnllib.client.fx.world.FxMoteRenderer.Kind;
import com.gtnllib.client.fx.world.FxMoteRenderer.Mote;
import com.gtnllib.client.fx.world.FxShockwaveRenderer;
import com.gtnllib.utils.Vec3d;
import com.science.gtnl.client.structureanimation.StructureSummonAnimation.Dust;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

@SideOnly(Side.CLIENT)
public class StructureOrbitalAnimation extends StructureMeshTask {

    public static final int FLASH_TICKS = 8, RING_SEGMENTS = 48, MAX_DUST_PER_WAVE = 360, FIRE_COLOR = 0xFB923C,
        GOLD_COLOR = 0xFEF3C7;
    public static final int[] DUST_WAVES = { 0, 5, 12 };
    public static final float DUST_SCALE = 3.2F, SHAKE_MIN = 3.2F, SHAKE_MAX = 7.5F, SHAKE_PER_BLOCK = 0.25F;
    public static final double BRAKE_START = 0.65, BEACON_HEIGHT = 72;
    public static final String SOUND = "sciencenotleisure:armory.";
    private static final FxCameraShake.Source SHAKE = new FxCameraShake.Source();
    private static final Random RANDOM = new Random();

    public final int dropHeight, beaconTicks, dropTicks;
    public FxBlockMesh mesh;
    public long impactTick = -1;
    public boolean braked, launched;
    public int pulses;
    private double lastAge;

    public StructureOrbitalAnimation(Start start, ByteBuf extra) {
        super(start);
        dropHeight = Math.max(0, extra.readShort());
        beaconTicks = Math.max(1, extra.readShort());
        dropTicks = Math.max(0, extra.readShort());
        int[] cells = new int[code.length];
        for (int i = 0; i < cells.length; i++) cells[i] = i;
        mesh = mesh(cells);
        int elapsed = start.elapsed();
        pulses = elapsed <= 1 ? 0 : Math.min(3, (elapsed - 1) / 10 + 1);
        launched = elapsed > beaconTicks;
        braked = elapsed > beaconTicks + BRAKE_START * dropTicks;
        if (elapsed > beaconTicks + dropTicks) impactTick = startTick + beaconTicks + dropTicks;
    }

    public double dropProgress(double age) {
        double p = dropTicks <= 0 || dropHeight <= 0 ? (age >= beaconTicks ? 1 : 0)
            : Math.max(0, Math.min(1, (age - beaconTicks) / dropTicks));
        if (placingTick >= 0) p = Math.max(p, Math.min(1, (age - (placingTick - startTick)) / 2));
        return p;
    }

    public double currentDropY(double age) {
        double p = dropProgress(age);
        if (p >= 1) return 0;
        double ease = p < BRAKE_START ? (p / BRAKE_START) * 0.84
            : 0.84 + 0.16 * (1 - Math.pow(1 - (p - BRAKE_START) / (1 - BRAKE_START), 2.2));
        return (1 - ease) * dropHeight;
    }

    @Override
    protected void placedInCopy(int placed) {}

    @Override
    protected boolean settled(long section) {
        return impactTick >= 0;
    }

    @Override
    protected boolean busy() {
        return impactTick < 0 || now() - impactTick <= DUST_WAVES[DUST_WAVES.length - 1];
    }

    @Override
    protected void releaseOwn() {
        if (mesh != null) mesh.release();
        mesh = null;
    }

    @Override
    protected void rewind() {
        if (impactTick > now()) impactTick = -1;
    }

    @Override
    protected void animate(Minecraft minecraft, double age) {
        double p = dropProgress(age);
        if (impactTick < 0 && age < beaconTicks && placingTick < 0) {
            int pulseStep = (int) (age / 10);
            if (pulseStep >= pulses && pulses < 3) {
                pulses = pulseStep + 1;
                sound(centerX, start.originY(), centerZ, SOUND + "pulse", 0.8F, 0.9F + pulseStep * 0.2F);
            }
            if ((int) age % 3 == 0) spawnBeaconSparks();
            return;
        }
        if (!launched && (age >= beaconTicks || placingTick >= 0)) {
            launched = true;
            if (dropHeight > 0) sound(centerX, start.originY() + dropHeight, centerZ, SOUND + "flyby", 1.1F, 0.85F);
        }
        if (!braked && p >= BRAKE_START && p < 1) {
            braked = true;
            sound(centerX, start.originY() + currentDropY(age), centerZ, SOUND + "brake", 1.1F, 0.9F);
        }
        if (impactTick < 0 && p >= 1) {
            impactTick = now();
            sound(centerX, start.originY(), centerZ, "random.explode", 1.8F, 0.55F);
            sound(centerX, start.originY(), centerZ, "random.anvil_land", 1.3F, 0.5F);
            shake(SHAKE, (float) Math.min(SHAKE_MAX, SHAKE_MIN + radius * SHAKE_PER_BLOCK), 0.55F);
            if (!muted()) {
                double waveR = Math.max(3, Math.min(32, radius * 2.3 + 4));
                FxShockwaveRenderer.spawn(new Vec3d(centerX, start.originY() + 0.12, centerZ), waveR, 22, FIRE_COLOR);
                FxShockwaveRenderer.spawn(
                    new Vec3d(centerX, start.originY() + 0.2, centerZ),
                    Math.min(32, waveR * 0.72),
                    16,
                    GOLD_COLOR);
            }
        }
        if (impactTick < 0 && p > 0) spawnEntryParticles(minecraft, currentDropY(age), p >= BRAKE_START);
        if (impactTick >= 0) for (int wave = 0; wave < DUST_WAVES.length; wave++)
            if (now() - impactTick == DUST_WAVES[wave]) spawnDust(minecraft.theWorld, wave);
    }

    private void spawnBeaconSparks() {
        if (muted()) return;
        Vec3d center = new Vec3d(centerX, start.originY() + 0.15, centerZ);
        for (int i = 0; i < 3; i++) {
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            Vec3d vel = new Vec3d(Math.cos(ang) * 0.08, 0.08 + RANDOM.nextDouble() * 0.1, Math.sin(ang) * 0.08);
            FxMoteRenderer.spawn(
                new Mote(Kind.SPARK, center, 0.18, 12, FIRE_COLOR, GOLD_COLOR).velocity(vel)
                    .fall(0.92, 0.012),
                0);
        }
    }

    private void spawnEntryParticles(Minecraft minecraft, double dropY, boolean braking) {
        if (muted()) return;
        if (minecraft.theWorld == null) return;
        float size = (float) Math.max(0.8, Math.min(2.8, radius * 0.25));
        int flames = particles(Math.max(6, Math.min(20, (int) (radius * 2))));
        for (int i = 0; i < flames; i++) {
            double ang = RANDOM.nextDouble() * Math.PI * 2;
            double r = RANDOM.nextDouble() * radius;
            double px = centerX + Math.cos(ang) * r;
            double pz = centerZ + Math.sin(ang) * r;
            double py = start.originY() + dropY + RANDOM.nextDouble() * Math.min(4, start.sizeY());
            minecraft.effectRenderer.addEffect(
                new FxJetParticle(
                    minecraft.theWorld,
                    i % 3 == 0 ? FxJetParticle.Kind.TRAIL : FxJetParticle.Kind.THRUST,
                    px,
                    py,
                    pz,
                    Math.cos(ang) * 0.06,
                    0.35 + RANDOM.nextDouble() * 0.25,
                    Math.sin(ang) * 0.06,
                    size));
        }
        if (!braking) return;
        double minX = start.originX() + 0.4, maxX = start.originX() + start.sizeX() - 0.4;
        double minZ = start.originZ() + 0.4, maxZ = start.originZ() + start.sizeZ() - 0.4;
        double[][] jets = { { minX, minZ }, { maxX, minZ }, { minX, maxZ }, { maxX, maxZ } };
        for (double[] jet : jets) {
            minecraft.effectRenderer.addEffect(
                new FxJetParticle(
                    minecraft.theWorld,
                    FxJetParticle.Kind.CORE,
                    jet[0],
                    start.originY() + dropY + 0.1,
                    jet[1],
                    0,
                    -0.45,
                    0,
                    size * 1.1F));
        }
    }

    private void spawnDust(World world, int wave) {
        if (muted()) return;
        if (world == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        double grow = Math.sqrt(Math.max(1, radius / 7));
        int count = particles(Math.max(24, Math.min(MAX_DUST_PER_WAVE, (start.sizeX() + start.sizeZ()) * 6)));
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * (i + RANDOM.nextDouble()) / count;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double edge = Math.min(
                start.sizeX() / 2.0 / Math.max(Math.abs(dx), 0.001),
                start.sizeZ() / 2.0 / Math.max(Math.abs(dz), 0.001)) + 0.2 + wave * 0.55;
            double x = centerX + dx * edge, z = centerZ + dz * edge;
            double speed = (0.16 + RANDOM.nextDouble() * 0.2) * (1 - wave * 0.18) * grow;
            minecraft.effectRenderer.addEffect(
                new Dust(
                    world,
                    x,
                    start.originY() + 0.15 + RANDOM.nextDouble() * 0.25,
                    z,
                    dx * speed,
                    0.02 + RANDOM.nextDouble() * 0.03,
                    dz * speed,
                    (float) ((0.7 + RANDOM.nextDouble() * 0.65) * DUST_SCALE * grow),
                    24 + RANDOM.nextInt(40)));
            if (wave != 0 || i % 2 != 0) continue;
            Block ground = world.getBlock((int) Math.floor(x), start.originY() - 1, (int) Math.floor(z));
            if (ground == null || ground == Blocks.air) continue;
            int meta = world.getBlockMetadata((int) Math.floor(x), start.originY() - 1, (int) Math.floor(z));
            minecraft.effectRenderer.addEffect(
                new EntityDiggingFX(world, x, start.originY() + 0.2, z, dx * speed, 0.16, dz * speed, ground, meta));
        }
    }

    private float beaconFade(double age) {
        if (impactTick >= 0) return Math.max(0, 1 - (float) (age - (impactTick - startTick)) / 4F);
        if (age < beaconTicks) return (float) Math.min(1, age / 6);
        if (age < beaconTicks + dropTicks) return 1F;
        return Math.max(0, 1 - (float) (age - beaconTicks - dropTicks) / 4F);
    }

    @Override
    public float glow(double age) {
        float fade = beaconFade(age);
        return fade <= 0.01F ? 0 : fade * (impactTick >= 0 ? 0.8F : 0.75F);
    }

    @Override
    protected void drawGlow(double cameraX, double cameraY, double cameraZ) {
        float fade = beaconFade(lastAge);
        if (fade <= 0.01F) return;
        GL11.glPushMatrix();
        GL11.glTranslated(centerX - cameraX, start.originY() - cameraY, centerZ - cameraZ);
        drawBeaconBeam(0.28 * fade, BEACON_HEIGHT);
        GL11.glPopMatrix();
    }

    @Override
    protected void render(Minecraft minecraft, double age, float partial, double cameraX, double cameraY,
        double cameraZ) {
        lastAge = age;
        if (mesh != null) mesh.compile(StructureAnimations.compileBudget(), this::placed);
        if (beaconFade(age) > 0.01F) drawBeacon(age, cameraX, cameraY, cameraZ);
        if (age >= beaconTicks || placingTick >= 0 || impactTick >= 0) drawMachine(age, cameraX, cameraY, cameraZ);
    }

    private void drawBeacon(double age, double cameraX, double cameraY, double cameraZ) {
        float fade = beaconFade(age);
        if (fade <= 0.01F) return;
        GL11.glPushMatrix();
        GL11.glTranslated(centerX - cameraX, start.originY() + 0.04 - cameraY, centerZ - cameraZ);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        GL11.glColor4f(1F, 0.32F, 0.18F, 0.55F * fade);
        drawBeaconBeam(0.32, BEACON_HEIGHT);
        GL11.glColor4f(1F, 0.88F, 0.65F, 0.92F * fade);
        drawBeaconBeam(0.11, BEACON_HEIGHT);
        GL11.glLineWidth(2.4F);
        for (int ring = 0; ring < 2; ring++) {
            double cycle = (age * 0.055 + ring * 0.5) % 1;
            double r = Math.max(0.5, (1 - cycle) * (radius + 2.5));
            float a = (float) Math.sin(cycle * Math.PI) * fade * 0.85F;
            GL11.glColor4f(1F, 0.36F, 0.22F, a);
            Tessellator t = Tessellator.instance;
            t.startDrawing(GL11.GL_LINE_LOOP);
            for (int i = 0; i < RING_SEGMENTS; i++) {
                double ang = i * Math.PI * 2 / RING_SEGMENTS;
                t.addVertex(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            }
            t.draw();
        }
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private static void drawBeaconBeam(double half, double height) {
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.addVertex(-half, 0, 0);
        t.addVertex(half, 0, 0);
        t.addVertex(half, height, 0);
        t.addVertex(-half, height, 0);
        t.addVertex(0, 0, -half);
        t.addVertex(0, 0, half);
        t.addVertex(0, height, half);
        t.addVertex(0, height, -half);
        t.draw();
    }

    private void drawMachine(double age, double cameraX, double cameraY, double cameraZ) {
        if (mesh == null) return;
        double dropY = currentDropY(age);
        GL11.glPushMatrix();
        GL11.glTranslated(offsetX - cameraX, offsetY + dropY - cameraY, offsetZ - cameraZ);
        if (dropY <= 0.001) {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -1);
        }
        FxBlockMesh.solidState();
        drawMesh(mesh);
        if (impactTick >= 0) {
            float flash = 1 - (float) (age - (impactTick - startTick)) / FLASH_TICKS;
            if (flash > 0) mesh.drawFlash(flash * 0.5F, this::visible);
        }
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPopMatrix();
    }
}
