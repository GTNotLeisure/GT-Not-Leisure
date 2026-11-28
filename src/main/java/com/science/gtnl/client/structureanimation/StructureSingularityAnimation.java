package com.science.gtnl.client.structureanimation;

import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;

import com.gtnllib.client.fx.world.FxArcRenderer;
import com.gtnllib.client.fx.world.FxBlockMesh;
import com.gtnllib.client.fx.world.FxCameraShake;
import com.gtnllib.client.fx.world.FxMoteRenderer;
import com.gtnllib.client.fx.world.FxMoteRenderer.Kind;
import com.gtnllib.client.fx.world.FxMoteRenderer.Mote;
import com.gtnllib.client.fx.world.FxShockwaveRenderer;
import com.gtnllib.utils.Vec3d;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

@SideOnly(Side.CLIENT)
public class StructureSingularityAnimation extends StructureMeshTask {

    public static final int FLASH_TICKS = 10, RING_SEGMENTS = 48, VIOLET = 0xC084FC, CYAN = 0x67E8F9;
    public static final float MAX_SPIN_DEGREES = 65F, SHAKE_MIN = 2.0F, SHAKE_MAX = 5.5F;
    public static final String SOUND = "sciencenotleisure:armory.";
    private static final FxCameraShake.Source SHAKE = new FxCameraShake.Source();
    private static final Random RANDOM = new Random();

    public final int chargeTicks, unfoldTicks;
    public final double centerY;
    public FxBlockMesh mesh;
    public long burstTick = -1, settledTick = -1;
    public boolean charged;
    private double lastAge;

    public StructureSingularityAnimation(Start start, ByteBuf extra) {
        super(start);
        chargeTicks = Math.max(1, extra.readShort());
        unfoldTicks = Math.max(1, extra.readShort());
        centerY = start.originY() + start.sizeY() / 2.0;
        int[] cells = new int[code.length];
        for (int i = 0; i < cells.length; i++) cells[i] = i;
        mesh = mesh(cells);
        charged = start.elapsed() > 0;
        if (start.elapsed() > chargeTicks) burstTick = startTick + chargeTicks;
        if (start.elapsed() > chargeTicks + unfoldTicks) settledTick = startTick + chargeTicks + unfoldTicks;
    }

    public double unfold(double age) {
        double p = Math.max(0, Math.min(1, (age - chargeTicks) / unfoldTicks));
        if (placingTick >= 0) p = Math.max(p, Math.min(1, (age - (placingTick - startTick)) / 2));
        return p;
    }

    @Override
    protected void placedInCopy(int placed) {}

    @Override
    protected boolean settled(long section) {
        return settledTick >= 0;
    }

    @Override
    protected boolean busy() {
        return settledTick < 0 || now() - settledTick < FLASH_TICKS;
    }

    @Override
    protected void releaseOwn() {
        if (mesh != null) mesh.release();
        mesh = null;
    }

    @Override
    protected void rewind() {
        if (burstTick > now()) burstTick = -1;
        if (settledTick > now()) settledTick = -1;
    }

    @Override
    protected void animate(Minecraft minecraft, double age) {
        if (!charged && age >= 1) {
            charged = true;
            sound(centerX, centerY, centerZ, "portal.trigger", 0.55F, 1.35F);
            sound(centerX, centerY, centerZ, SOUND + "pulse", 0.85F, 0.9F);
        }
        if (burstTick < 0 && (age >= chargeTicks || placingTick >= 0)) {
            burstTick = now();
            sound(centerX, centerY, centerZ, SOUND + "launch", 1.15F, 0.8F);
            sound(centerX, centerY, centerZ, "random.explode", 1.2F, 0.55F);
            shake(SHAKE, (float) Math.min(SHAKE_MAX, SHAKE_MIN + radius * 0.2), 0.45F);
            spawnBurstEffects();
        }
        if (settledTick < 0 && unfold(age) >= 1) {
            settledTick = now();
            sound(centerX, centerY, centerZ, SOUND + "lock", 0.9F, 1.0F);
        }
        if (burstTick < 0 && settledTick < 0) spawnInwardMotes();
    }

    private void spawnInwardMotes() {
        if (muted()) return;
        int count = Math.max(3, Math.min(14, particles((int) Math.max(4, radius * 1.5))));
        double span = Math.max(2.5, Math.min(18, radius * 1.5));
        for (int i = 0; i < count; i++) {
            double theta = RANDOM.nextDouble() * Math.PI * 2;
            double phi = (RANDOM.nextDouble() - 0.5) * Math.PI;
            double dist = span * (0.7 + RANDOM.nextDouble() * 0.5);
            double ox = Math.cos(theta) * Math.cos(phi) * dist;
            double oy = Math.sin(phi) * dist * 0.7;
            double oz = Math.sin(theta) * Math.cos(phi) * dist;
            int life = 10 + RANDOM.nextInt(8);
            Vec3d origin = new Vec3d(centerX + ox, centerY + oy, centerZ + oz);
            Vec3d velocity = new Vec3d(-ox / life, -oy / life, -oz / life);
            int color = i % 2 == 0 ? VIOLET : CYAN;
            FxMoteRenderer.spawn(
                new Mote(Kind.DOT, origin, 0.18 + RANDOM.nextDouble() * 0.14, life, color, 0xFFFFFF).velocity(velocity)
                    .twinkle(RANDOM.nextFloat() * 6.28F),
                0);
        }
    }

    private void spawnBurstEffects() {
        if (muted()) return;
        Vec3d center = new Vec3d(centerX, centerY, centerZ);
        double outer = Math.max(2.5, Math.min(32, radius * 2.1 + 3));
        double inner = Math.max(1.8, Math.min(32, radius * 1.4 + 2));
        FxShockwaveRenderer.spawn(new Vec3d(centerX, start.originY() + 0.15, centerZ), outer, 20, VIOLET);
        FxShockwaveRenderer.spawn(center, inner, 16, CYAN);
        double minX = start.originX(), maxX = start.originX() + start.sizeX();
        double minY = start.originY(), maxY = start.originY() + start.sizeY();
        double minZ = start.originZ(), maxZ = start.originZ() + start.sizeZ();
        for (int i = 0; i < 8; i++) {
            Vec3d corner = new Vec3d(
                (i & 1) == 0 ? minX : maxX,
                (i & 2) == 0 ? minY : maxY,
                (i & 4) == 0 ? minZ : maxZ);
            FxArcRenderer.spawnColored(
                center,
                corner,
                10,
                Math.min(1.6, 0.35 + radius * 0.08),
                14,
                i % 2 == 0 ? VIOLET : CYAN,
                1.35);
        }
    }

    private float coreCharge(double age) {
        if (burstTick >= 0) return Math.max(0, 1 - (float) (age - (burstTick - startTick)) / 8F);
        if (age < chargeTicks) return (float) Math.max(0, age / chargeTicks);
        return Math.max(0, 1 - (float) (age - chargeTicks) / 8F);
    }

    @Override
    public float glow(double age) {
        float charge = coreCharge(age);
        if (charge <= 0.01F) return 0;
        return burstTick >= 0 ? charge * 0.9F : (float) Math.pow(charge, 1.2) * 0.95F;
    }

    @Override
    protected void drawGlow(double cameraX, double cameraY, double cameraZ) {
        float strength = coreCharge(lastAge);
        if (strength <= 0.01F) return;
        GL11.glPushMatrix();
        GL11.glTranslated(centerX - cameraX, centerY - cameraY, centerZ - cameraZ);
        drawCoreGeometry(lastAge, strength);
        GL11.glPopMatrix();
    }

    @Override
    protected void render(Minecraft minecraft, double age, float partial, double cameraX, double cameraY,
        double cameraZ) {
        lastAge = age;
        if (mesh != null) mesh.compile(StructureAnimations.compileBudget(), this::placed);
        double u = unfold(age);
        if (u > 0) drawUnfoldingMachine(age, u, cameraX, cameraY, cameraZ);
        if (coreCharge(age) > 0.01F) drawCore(age, cameraX, cameraY, cameraZ);
    }

    private void drawUnfoldingMachine(double age, double u, double cameraX, double cameraY, double cameraZ) {
        if (mesh == null) return;
        double scale = u >= 1 ? 1 : Math.max(0.02, easeOutBack(u));
        double spin = u >= 1 ? 0 : (1 - easeOutCubic(u)) * MAX_SPIN_DEGREES;
        GL11.glPushMatrix();
        GL11.glTranslated(centerX - cameraX, centerY - cameraY, centerZ - cameraZ);
        if (u < 1) {
            GL11.glRotated(spin, 0, 1, 0);
            GL11.glScaled(scale, scale, scale);
        } else {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -1);
        }
        GL11.glTranslated(offsetX - centerX, offsetY - centerY, offsetZ - centerZ);
        FxBlockMesh.solidState();
        drawMesh(mesh);
        float flash = u < 1 ? (float) (1 - u) * 0.7F
            : settledTick >= 0 ? Math.max(0, 1 - (float) (age - (settledTick - startTick)) / FLASH_TICKS) * 0.5F : 0;
        if (flash > 0.01F) mesh.drawFlash(flash, this::visible);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPopMatrix();
    }

    private void drawCore(double age, double cameraX, double cameraY, double cameraZ) {
        float charge = coreCharge(age);
        if (charge <= 0.01F) return;
        GL11.glPushMatrix();
        GL11.glTranslated(centerX - cameraX, centerY - cameraY, centerZ - cameraZ);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        GL11.glColor4f(0.82F, 0.62F, 1F, 0.9F * charge);
        drawCoreGeometry(age, charge);
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private void drawCoreGeometry(double age, float charge) {
        double coreR = Math.max(0.45, Math.min(2.2, radius * 0.22)) * (0.35 + 0.85 * charge);
        double ringR = Math.max(1.2, Math.min(6.0, radius * 0.65)) * (1.25 - 0.55 * charge);
        drawOctahedron(coreR, age * 9);
        GL11.glLineWidth(2.4F);
        GL11.glPushMatrix();
        GL11.glRotated(age * 11, 0.3, 1, 0.2);
        drawRing(ringR);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        GL11.glRotated(-age * 8.5, 0.7, 0.2, 1);
        drawRing(ringR * 1.35);
        GL11.glPopMatrix();
    }

    private static void drawOctahedron(double r, double spin) {
        GL11.glPushMatrix();
        GL11.glRotated(spin, 0.4, 1, 0.3);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_TRIANGLES);
        double[][] pts = { { 0, r, 0 }, { 0, -r, 0 }, { r, 0, 0 }, { -r, 0, 0 }, { 0, 0, r }, { 0, 0, -r } };
        int[][] tris = { { 0, 2, 4 }, { 0, 4, 3 }, { 0, 3, 5 }, { 0, 5, 2 }, { 1, 4, 2 }, { 1, 3, 4 }, { 1, 5, 3 },
            { 1, 2, 5 } };
        for (int[] tri : tris) for (int idx : tri) t.addVertex(pts[idx][0], pts[idx][1], pts[idx][2]);
        t.draw();
        GL11.glPopMatrix();
    }

    private static void drawRing(double r) {
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINE_LOOP);
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double ang = i * Math.PI * 2 / RING_SEGMENTS;
            t.addVertex(Math.cos(ang) * r, 0, Math.sin(ang) * r);
        }
        t.draw();
    }

    private static double easeOutCubic(double t) {
        return 1 - Math.pow(1 - t, 3);
    }

    private static double easeOutBack(double t) {
        double c1 = 1.85, c3 = c1 + 1;
        return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
    }
}
