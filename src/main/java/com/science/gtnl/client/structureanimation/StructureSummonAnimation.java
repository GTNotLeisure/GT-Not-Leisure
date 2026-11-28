package com.science.gtnl.client.structureanimation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityCloudFX;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.particle.EntitySmokeFX;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.gtnllib.client.fx.world.FxBlockMesh;
import com.gtnllib.client.fx.world.FxCameraShake;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

@SideOnly(Side.CLIENT)
public class StructureSummonAnimation extends StructureMeshTask {

    public static final int OPEN_TICKS = StructureAnimationStyle.SUMMON_DELAY + StructureAnimationStyle.SUMMON_OPEN,
        CLOSE_TICKS = 12, MAX_DUST_PER_WAVE = 400;
    public static final float DUST_SCALE = 3f;
    public static final double CLIP_MARGIN = 0.02;
    public static final int[] DUST_WAVES = { 0, 6, 14 };
    public static final float SHAKE_MIN = 2.5F, SHAKE_MAX = 7F, SHAKE_PER_BLOCK = 0.25F, SHAKE_RUMBLE = 0.5F;
    public static final float SPIN_PER_TICK = 5.5f;
    private static final FxCameraShake.Source SHAKE = new FxCameraShake.Source();
    private static final DoubleBuffer CLIP = BufferUtils.createDoubleBuffer(4);
    private static final Random RANDOM = new Random();
    private static ResourceLocation circleTexture;

    public static class Spark {

        public double x, y, z, vx, vy, vz;
        public int age, life;
    }

    public final List<Spark> sparks = new ArrayList<>();
    public final int openingY, descentTicks;
    public FxBlockMesh mesh;
    public long impactTick = -1;
    public boolean opened;

    public StructureSummonAnimation(Start start, ByteBuf extra) {
        super(start);
        openingY = extra.readShort();
        descentTicks = Math.max(1, extra.readShort());
        int[] cells = new int[code.length];
        for (int i = 0; i < cells.length; i++) cells[i] = i;
        mesh = mesh(cells);
        // A viewer who arrives after the landing gets the machine in place without the impact played again.
        if (start.elapsed() >= OPEN_TICKS + descentTicks) {
            opened = true;
            impactTick = startTick + OPEN_TICKS + descentTicks;
        }
    }

    // Descent progress 0..1; the server's "placing" packet forces the landing within two ticks.
    public double descent(double age) {
        double progress = Math.max(0, Math.min(1, (age - OPEN_TICKS) / descentTicks));
        if (placingTick >= 0) progress = Math.max(progress, Math.min(1, (age - (placingTick - startTick)) / 2));
        return progress;
    }

    public double fall(double age) {
        return (openingY - start.originY() + CLIP_MARGIN) * (1 - Math.pow(descent(age), 4));
    }

    public float circleScale(double age) {
        double opening = Math.max(0, Math.min(1, age / OPEN_TICKS));
        double closing = impactTick < 0 ? 0 : Math.max(0, Math.min(1, (age - (impactTick - startTick)) / CLOSE_TICKS));
        return (float) ((1 - Math.pow(1 - opening, 3)) * (1 - closing * closing));
    }

    @Override
    protected void placedInCopy(int placed) {}

    @Override
    protected boolean settled(long section) {
        return impactTick >= 0;
    }

    @Override
    protected boolean busy() {
        return !sparks.isEmpty() || impactTick < 0 || now() - impactTick <= DUST_WAVES[DUST_WAVES.length - 1];
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
        if (!opened && age >= StructureAnimationStyle.SUMMON_DELAY) {
            opened = true;
            sound(centerX, openingY, centerZ, "portal.trigger", 0.6f, 0.6f);
        }
        if (impactTick < 0 && descent(age) >= 1) {
            impactTick = now();
            sound(centerX, start.originY(), centerZ, "random.anvil_land", 1.2f, 0.55f);
            sound(centerX, start.originY(), centerZ, "random.explode", 1.6f, 0.65f);
            shake(SHAKE, (float) Math.min(SHAKE_MAX, SHAKE_MIN + radius * SHAKE_PER_BLOCK), SHAKE_RUMBLE);
        }
        if (impactTick < 0 && age >= OPEN_TICKS) sparks(age);
        if (impactTick >= 0) for (int wave = 0; wave < DUST_WAVES.length; wave++)
            if (now() - impactTick == DUST_WAVES[wave]) dust(minecraft.theWorld, wave);
        stepSparks();
    }

    // Distance from the centre to the bounding box edge along a horizontal direction.
    private double edge(double dx, double dz) {
        return Math.min(
            start.sizeX() / 2.0 / Math.max(Math.abs(dx), 0.001),
            start.sizeZ() / 2.0 / Math.max(Math.abs(dz), 0.001));
    }

    // While the machine comes down: sparks swirl along the circle's rim and sink, and pale motes slide down its sides.
    private void sparks(double age) {
        if (muted()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        int count = particles((int) Math.max(8, Math.min(24, Math.round(radius * 3))));
        double drop = fall(age);
        double bottom = start.originY() + drop;
        double top = Math.min(openingY, start.originY() + start.sizeY() + drop);
        for (int i = 0; i < count; i++) {
            double angle = age * 0.11 + Math.PI * 2 * (i + RANDOM.nextDouble()) / count;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double ring = radius * (0.8 + RANDOM.nextDouble() * 0.2);
            Spark spark = new Spark();
            spark.x = centerX + dx * ring;
            spark.y = openingY - 0.08;
            spark.z = centerZ + dz * ring;
            spark.vx = -dz * 0.13 - dx * 0.025;
            spark.vy = -0.07;
            spark.vz = dx * 0.13 - dz * 0.025;
            spark.life = 12 + RANDOM.nextInt(15);
            sparks.add(spark);
            if (i % 2 == 0 && top > bottom) {
                double edge = edge(dx, dz) + 0.15;
                minecraft.effectRenderer.addEffect(
                    new Mote(
                        minecraft.theWorld,
                        centerX + dx * edge,
                        bottom + RANDOM.nextDouble() * (top - bottom),
                        centerZ + dz * edge,
                        -dz * 0.045,
                        -0.06,
                        dx * 0.045,
                        12 + RANDOM.nextInt(12)));
            }
        }
    }

    private void stepSparks() {
        Iterator<Spark> iterator = sparks.iterator();
        while (iterator.hasNext()) {
            Spark spark = iterator.next();
            if (++spark.age >= spark.life) {
                iterator.remove();
                continue;
            }
            spark.x += spark.vx;
            spark.y += spark.vy;
            spark.z += spark.vz;
        }
    }

    // A ring of slow, wide smoke rolling outward from the base; each wave starts a little further out and slower.
    // Built directly so the 16-block particle range of spawnParticle does not hide it from players watching from afar.
    private void dust(World world, int wave) {
        if (muted()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        double grow = Math.sqrt(Math.max(1, radius / 8));
        int count = particles(Math.max(24, Math.min(MAX_DUST_PER_WAVE, (start.sizeX() + start.sizeZ()) * 6)));
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * (i + RANDOM.nextDouble()) / count;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double edge = edge(dx, dz) + 0.2 + wave * 0.45;
            double x = centerX + dx * edge, z = centerZ + dz * edge;
            double speed = (0.12 + RANDOM.nextDouble() * 0.16) * (1 - wave * 0.18) * grow;
            minecraft.effectRenderer.addEffect(
                new Dust(
                    world,
                    x,
                    start.originY() + 0.1 + RANDOM.nextDouble() * 0.25,
                    z,
                    dx * speed,
                    0.012 + RANDOM.nextDouble() * 0.025,
                    dz * speed,
                    (float) ((0.65 + RANDOM.nextDouble() * 0.6) * DUST_SCALE * grow),
                    20 + RANDOM.nextInt(41)));
            if (wave != 0 || i % 2 != 0) continue;
            Block ground = world.getBlock((int) Math.floor(x), start.originY() - 1, (int) Math.floor(z));
            if (ground == null || ground == Blocks.air) continue;
            int meta = world.getBlockMetadata((int) Math.floor(x), start.originY() - 1, (int) Math.floor(z));
            minecraft.effectRenderer.addEffect(
                new EntityDiggingFX(world, x, start.originY() + 0.15, z, dx * speed, 0.12, dz * speed, ground, meta));
        }
    }

    @Override
    protected void render(Minecraft minecraft, double age, float partial, double cameraX, double cameraY,
        double cameraZ) {
        drawMachine(minecraft, age, cameraX, cameraY, cameraZ);
        drawCircle(minecraft, age, cameraX, cameraY, cameraZ);
        drawSparks(partial, cameraX, cameraY, cameraZ);
    }

    private void drawMachine(Minecraft minecraft, double age, double cameraX, double cameraY, double cameraZ) {
        if (mesh == null) return;
        GL11.glPushMatrix();
        GL11.glTranslated(offsetX - cameraX, offsetY - cameraY, offsetZ - cameraZ);
        boolean landed = descent(age) >= 1;
        if (!landed) {
            // Set before the drop so the plane stays at the circle while the machine moves through it.
            CLIP.clear();
            CLIP.put(0)
                .put(-1)
                .put(0)
                .put(openingY - offsetY)
                .flip();
            GL11.glClipPlane(GL11.GL_CLIP_PLANE0, CLIP);
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
        } else {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -1);
        }
        GL11.glTranslated(0, fall(age), 0);
        FxBlockMesh.solidState();
        drawMesh(mesh);
        GL11.glDisable(GL11.GL_CLIP_PLANE0);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPopMatrix();
    }

    private void drawCircle(Minecraft minecraft, double age, double cameraX, double cameraY, double cameraZ) {
        float scale = circleScale(age);
        if (scale <= 0.001f) return;
        if (circleTexture == null) circleTexture = minecraft.getTextureManager()
            .getDynamicTextureLocation("gtnl_projector_circle", new DynamicTexture(circleImage()));
        minecraft.getTextureManager()
            .bindTexture(circleTexture);
        GL11.glPushMatrix();
        GL11.glTranslated(centerX - cameraX, openingY + 0.01 - cameraY, centerZ - cameraZ);
        GL11.glRotated(age * SPIN_PER_TICK, 0, 1, 0);
        double r = radius * scale;
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        GL11.glColor4f(0.62f, 0.9f, 1f, 1f);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(-r, 0, -r, 0, 0);
        tessellator.addVertexWithUV(-r, 0, r, 0, 1);
        tessellator.addVertexWithUV(r, 0, r, 1, 1);
        tessellator.addVertexWithUV(r, 0, -r, 1, 0);
        tessellator.draw();
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private void drawSparks(float partial, double cameraX, double cameraY, double cameraZ) {
        if (sparks.isEmpty()) return;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        float size = (float) Math.max(0.06, radius * 0.006);
        float rx = ActiveRenderInfo.rotationX * size, rz = ActiveRenderInfo.rotationZ * size,
            ryz = ActiveRenderInfo.rotationYZ * size, rxy = ActiveRenderInfo.rotationXY * size,
            rxz = ActiveRenderInfo.rotationXZ * size;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        for (Spark spark : sparks) {
            float alpha = 1 - (spark.age + partial) / spark.life;
            tessellator.setColorRGBA_F(0.55f, 0.8f, 1f, Math.max(0, alpha));
            double x = spark.x + spark.vx * partial - cameraX, y = spark.y + spark.vy * partial - cameraY,
                z = spark.z + spark.vz * partial - cameraZ;
            tessellator.addVertex(x - rx - ryz, y - rxz, z - rz - rxy);
            tessellator.addVertex(x - rx + ryz, y + rxz, z - rz + rxy);
            tessellator.addVertex(x + rx + ryz, y + rxz, z + rz + rxy);
            tessellator.addVertex(x + rx - ryz, y - rxz, z + rz - rxy);
        }
        tessellator.draw();
        GL11.glDepthMask(true);
    }

    public static class Dust extends EntitySmokeFX {

        public Dust(World world, double x, double y, double z, double vx, double vy, double vz, float scale, int life) {
            super(world, x, y, z, vx, vy, vz, scale);
            particleMaxAge = life;
            setRBGColorF(0.64f, 0.6f, 0.54f);
        }
    }

    public static class Mote extends EntityCloudFX {

        public Mote(World world, double x, double y, double z, double vx, double vy, double vz, int life) {
            super(world, x, y, z, vx, vy, vz);
            particleMaxAge = life;
            setRBGColorF(0.65f, 0.72f, 0.78f);
            multipleParticleScaleBy(0.45f);
        }
    }

    // The circle is drawn here rather than shipped as a texture: outer ring of ticks and glyphs, inner hexagram.
    private static BufferedImage circleImage() {
        int size = 512;
        double radius = size / 2.0;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.translate(radius, radius);
        ring(g, radius * 0.97, 5);
        ring(g, radius * 0.93, 2);
        ring(g, radius * 0.78, 4);
        ring(g, radius * 0.74, 2);
        for (int i = 0; i < 72; i++) {
            double angle = i / 72.0 * Math.PI * 2;
            boolean major = i % 6 == 0;
            g.setStroke(new BasicStroke(major ? 4 : 2));
            double inner = radius * (major ? 0.78 : 0.88);
            g.draw(
                new Line2D.Double(
                    Math.cos(angle) * radius * 0.93,
                    Math.sin(angle) * radius * 0.93,
                    Math.cos(angle) * inner,
                    Math.sin(angle) * inner));
        }
        g.setStroke(new BasicStroke(3));
        for (int i = 0; i < 24; i++) {
            double angle = (i + 0.5) / 24 * Math.PI * 2, s = radius * 0.035;
            Graphics2D glyph = (Graphics2D) g.create();
            glyph.rotate(angle);
            glyph.translate(radius * 0.855, 0);
            glyph.rotate(Math.PI / 2);
            int seed = i * 7919 % 13;
            glyph.draw(new Line2D.Double(-s, -s, -s, s));
            glyph.draw(seed % 2 == 1 ? new Line2D.Double(-s, 0, s, -s) : new Line2D.Double(-s, -s, s, s));
            if (seed % 3 == 0) glyph.draw(new Line2D.Double(s * 0.2, -s, s * 0.2, s));
            if (seed % 4 == 1) glyph.draw(new Line2D.Double(-s, s, s, s));
            glyph.dispose();
        }
        ring(g, radius * 0.70, 4);
        ring(g, radius * 0.42, 3);
        ring(g, radius * 0.12, 3);
        g.setStroke(new BasicStroke(3));
        for (int triangle = 0; triangle < 2; triangle++) {
            Path2D path = new Path2D.Double();
            for (int k = 0; k <= 3; k++) {
                double angle = -Math.PI / 2 + triangle * Math.PI + k * Math.PI * 2 / 3;
                double x = Math.cos(angle) * radius * 0.70, y = Math.sin(angle) * radius * 0.70;
                if (k == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
            }
            g.draw(path);
        }
        for (int k = 0; k < 6; k++) {
            double angle = -Math.PI / 2 + k * Math.PI / 3;
            double x = Math.cos(angle) * radius * 0.70, y = Math.sin(angle) * radius * 0.70, r = radius * 0.06;
            g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            g.draw(
                new Line2D.Double(
                    0,
                    0,
                    Math.cos(angle + Math.PI / 6) * radius * 0.42,
                    Math.sin(angle + Math.PI / 6) * radius * 0.42));
        }
        g.dispose();
        return image;
    }

    private static void ring(Graphics2D g, double radius, float width) {
        g.setStroke(new BasicStroke(width));
        g.draw(new Ellipse2D.Double(-radius, -radius, radius * 2, radius * 2));
    }
}
