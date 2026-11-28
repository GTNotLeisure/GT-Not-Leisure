package com.science.gtnl.client.structureanimation;

import java.nio.DoubleBuffer;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.gtnllib.client.fx.world.FxBlockMesh;
import com.gtnllib.client.fx.world.FxCameraShake;
import com.gtnllib.client.fx.world.FxMoteRenderer;
import com.gtnllib.client.fx.world.FxMoteRenderer.Kind;
import com.gtnllib.client.fx.world.FxMoteRenderer.Mote;
import com.gtnllib.utils.Vec3d;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;
import com.science.gtnl.config.ClientConfig;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;

@SideOnly(Side.CLIENT)
public class StructureRiseAnimation extends StructureMeshTask {

    public static final int PRE_TICKS = StructureAnimationStyle.RISE_PRE, FLASH_TICKS = 6, SETTLE_TICKS = 14,
        STEAM_GLOW = 0xCBD5E1, STEAM_CORE = 0xF8FAFC;
    public static final double CLIP_MARGIN = 0.02, STAGE_MOVE_FRACTION = 0.78;
    public static final float RUMBLE_MAX = 0.9F, LOCK_SHAKE_MIN = 1.8F, LOCK_SHAKE_MAX = 4.5F;
    public static final String SOUND = "sciencenotleisure:armory.";
    private static final FxCameraShake.Source SHAKE = new FxCameraShake.Source();
    private static final Map<StructureRiseAnimation, Float> ACTIVE_SUSTAIN = new IdentityHashMap<>();
    private static final DoubleBuffer CLIP = BufferUtils.createDoubleBuffer(4);
    private static final Random RANDOM = new Random();

    public final int liftTicks, stages;
    public FxBlockMesh mesh;
    public long lockedTick = -1;
    public int pausedStageMask;

    public StructureRiseAnimation(Start start, ByteBuf extra) {
        super(start);
        liftTicks = Math.max(1, extra.readShort());
        stages = Math.max(1, Math.min(4, extra.readByte()));
        int[] cells = new int[code.length];
        for (int i = 0; i < cells.length; i++) cells[i] = i;
        mesh = mesh(cells);
        if (start.elapsed() > PRE_TICKS + liftTicks) lockedTick = startTick + PRE_TICKS + liftTicks;
        for (int stage = 0; stage < stages - 1; stage++)
            if (PRE_TICKS + (stage + STAGE_MOVE_FRACTION) / stages * liftTicks < start.elapsed())
                pausedStageMask |= 1 << stage;
    }

    public double linearProgress(double age) {
        double p = Math.max(0, Math.min(1, (age - PRE_TICKS) / liftTicks));
        if (placingTick >= 0) p = Math.max(p, Math.min(1, (age - (placingTick - startTick)) / 2));
        return p;
    }

    public double liftHeight(double age) {
        double p = linearProgress(age);
        if (p <= 0) return 0;
        if (p >= 1) return 1;
        double scaled = p * stages;
        int stage = Math.min(stages - 1, (int) scaled);
        double local = scaled - stage;
        double step = local >= STAGE_MOVE_FRACTION ? 1 : smooth(local / STAGE_MOVE_FRACTION);
        return (stage + step) / stages;
    }

    public double yOffset(double age) {
        return (liftHeight(age) - 1) * (start.sizeY() + CLIP_MARGIN);
    }

    @Override
    protected void placedInCopy(int placed) {}

    @Override
    protected boolean settled(long section) {
        return lockedTick >= 0;
    }

    @Override
    protected boolean busy() {
        return lockedTick < 0 || now() - lockedTick < SETTLE_TICKS;
    }

    @Override
    protected void releaseOwn() {
        clearRumble();
        if (mesh != null) mesh.release();
        mesh = null;
    }

    @Override
    protected void rewind() {
        if (lockedTick > now()) lockedTick = -1;
    }

    @Override
    protected void animate(Minecraft minecraft, double age) {
        if (lockedTick >= 0) return;
        double p = linearProgress(age);
        if (p >= 1) {
            lockedTick = now();
            clearRumble();
            sound(centerX, start.originY(), centerZ, SOUND + "lock", 1.1F, 0.72F);
            sound(centerX, start.originY(), centerZ, "random.anvil_land", 1.0F, 0.6F);
            sound(centerX, start.originY(), centerZ, SOUND + "hiss", 1.0F, 0.85F);
            shake(SHAKE, (float) Math.min(LOCK_SHAKE_MAX, LOCK_SHAKE_MIN + radius * 0.16), 0.5F);
            ventSteam(minecraft.theWorld, true);
            return;
        }
        double scaled = p * stages;
        int stage = Math.min(stages - 1, (int) scaled);
        double local = scaled - stage;
        boolean pausing = age >= PRE_TICKS && local >= STAGE_MOVE_FRACTION && stage < stages - 1;
        if (pausing && (pausedStageMask & (1 << stage)) == 0) {
            pausedStageMask |= 1 << stage;
            sound(centerX, start.originY(), centerZ, SOUND + "hiss", 0.85F, 0.95F + stage * 0.1F);
            ventSteam(minecraft.theWorld, true);
        }
        applyRumble(minecraft, pausing ? 0.25F : 1.0F);
        ventSteam(minecraft.theWorld, false);
        if ((int) age % 2 == 0) spawnDebris(minecraft.theWorld);
    }

    private void applyRumble(Minecraft minecraft, float factor) {
        if (minecraft.thePlayer == null || !ClientConfig.structureLandingShake || muted()) {
            clearRumble();
            return;
        }
        double dx = gap(minecraft.thePlayer.posX, start.originX(), start.originX() + start.sizeX());
        double dy = gap(minecraft.thePlayer.posY, start.originY(), start.originY() + start.sizeY());
        double dz = gap(minecraft.thePlayer.posZ, start.originZ(), start.originZ() + start.sizeZ());
        double falloff = 1 - Math.sqrt(dx * dx + dy * dy + dz * dz) / (radius + SHAKE_REACH);
        if (falloff > 0) {
            ACTIVE_SUSTAIN.put(this, RUMBLE_MAX * factor * (float) falloff);
            syncSustain();
        } else {
            clearRumble();
        }
    }

    private void clearRumble() {
        if (ACTIVE_SUSTAIN.remove(this) != null) syncSustain();
    }

    private static void syncSustain() {
        float max = 0;
        for (float value : ACTIVE_SUSTAIN.values()) if (value > max) max = value;
        SHAKE.sustain(
            Minecraft.getMinecraft()
                .isGamePaused() ? 0 : max);
    }

    private void ventSteam(World world, boolean burst) {
        if (muted()) return;
        if (world == null) return;
        int count = particles(burst ? Math.max(16, Math.min(48, (start.sizeX() + start.sizeZ()) * 2)) : 6);
        for (int i = 0; i < count; i++) {
            double[] edge = perimeterPoint();
            double speed = burst ? 0.12 + RANDOM.nextDouble() * 0.16 : 0.04 + RANDOM.nextDouble() * 0.06;
            Vec3d origin = new Vec3d(edge[0], start.originY() + 0.15 + RANDOM.nextDouble() * 0.2, edge[1]);
            Vec3d velocity = new Vec3d(edge[2] * speed, 0.03 + RANDOM.nextDouble() * 0.05, edge[3] * speed);
            double size = Math.min(3.5, (burst ? 0.85 : 0.55) + RANDOM.nextDouble() * 0.45);
            FxMoteRenderer.spawn(
                new Mote(
                    Kind.PUFF,
                    origin,
                    size,
                    burst ? 26 + RANDOM.nextInt(14) : 16 + RANDOM.nextInt(10),
                    STEAM_GLOW,
                    STEAM_CORE).velocity(velocity)
                        .fall(0.92, -0.004),
                0);
        }
    }

    private void spawnDebris(World world) {
        if (muted()) return;
        if (world == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        int count = particles(Math.max(4, Math.min(18, (start.sizeX() + start.sizeZ()) / 2)));
        for (int i = 0; i < count; i++) {
            double[] edge = perimeterPoint();
            int bx = (int) Math.floor(edge[0]), bz = (int) Math.floor(edge[1]);
            Block ground = world.getBlock(bx, start.originY() - 1, bz);
            if (ground == null || ground == Blocks.air) continue;
            int meta = world.getBlockMetadata(bx, start.originY() - 1, bz);
            double speed = 0.06 + RANDOM.nextDouble() * 0.1;
            minecraft.effectRenderer.addEffect(
                new EntityDiggingFX(
                    world,
                    edge[0],
                    start.originY() + 0.15,
                    edge[1],
                    edge[2] * speed,
                    0.12 + RANDOM.nextDouble() * 0.08,
                    edge[3] * speed,
                    ground,
                    meta));
        }
    }

    private double[] perimeterPoint() {
        int side = RANDOM.nextInt(4);
        double x, z, ox, oz;
        if (side == 0) {
            x = start.originX() + RANDOM.nextDouble() * start.sizeX();
            z = start.originZ() - 0.1;
            ox = (RANDOM.nextDouble() - 0.5) * 0.3;
            oz = -1;
        } else if (side == 1) {
            x = start.originX() + RANDOM.nextDouble() * start.sizeX();
            z = start.originZ() + start.sizeZ() + 0.1;
            ox = (RANDOM.nextDouble() - 0.5) * 0.3;
            oz = 1;
        } else if (side == 2) {
            x = start.originX() - 0.1;
            z = start.originZ() + RANDOM.nextDouble() * start.sizeZ();
            ox = -1;
            oz = (RANDOM.nextDouble() - 0.5) * 0.3;
        } else {
            x = start.originX() + start.sizeX() + 0.1;
            z = start.originZ() + RANDOM.nextDouble() * start.sizeZ();
            ox = 1;
            oz = (RANDOM.nextDouble() - 0.5) * 0.3;
        }
        return new double[] { x, z, ox, oz };
    }

    @Override
    protected void render(Minecraft minecraft, double age, float partial, double cameraX, double cameraY,
        double cameraZ) {
        // Tasks stop ticking while paused but still render, so the tremor is muted and restored from here.
        if (ACTIVE_SUSTAIN.containsKey(this)) syncSustain();
        if (mesh != null) mesh.compile(StructureAnimations.compileBudget(), this::placed);
        drawMachine(age, cameraX, cameraY, cameraZ);
        if (lockedTick < 0 || now() - lockedTick < FLASH_TICKS) drawSeam(age, cameraX, cameraY, cameraZ);
    }

    private void drawMachine(double age, double cameraX, double cameraY, double cameraZ) {
        if (mesh == null || liftHeight(age) <= 0.001) return;
        GL11.glPushMatrix();
        GL11.glTranslated(offsetX - cameraX, offsetY - cameraY, offsetZ - cameraZ);
        boolean locked = linearProgress(age) >= 1;
        if (!locked) {
            CLIP.clear();
            CLIP.put(0)
                .put(1)
                .put(0)
                .put(-(start.originY() - offsetY - CLIP_MARGIN))
                .flip();
            GL11.glClipPlane(GL11.GL_CLIP_PLANE0, CLIP);
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
        } else {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -1);
        }
        GL11.glTranslated(0, yOffset(age), 0);
        FxBlockMesh.solidState();
        drawMesh(mesh);
        if (lockedTick >= 0) {
            float flash = 1 - (float) (age - (lockedTick - startTick)) / FLASH_TICKS;
            if (flash > 0) mesh.drawFlash(flash * 0.3F, this::visible);
        }
        GL11.glDisable(GL11.GL_CLIP_PLANE0);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPopMatrix();
    }

    private void drawSeam(double age, double cameraX, double cameraY, double cameraZ) {
        float fade = lockedTick < 0 ? 1F : Math.max(0, 1 - (float) (age - (lockedTick - startTick)) / FLASH_TICKS);
        if (fade <= 0) return;
        double minX = start.originX() - 0.06, maxX = start.originX() + start.sizeX() + 0.06;
        double minZ = start.originZ() - 0.06, maxZ = start.originZ() + start.sizeZ() + 0.06;
        double y = start.originY() + 0.03;
        GL11.glPushMatrix();
        GL11.glTranslated(-cameraX, -cameraY, -cameraZ);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2.6F);
        GL11.glColor4f(1F, 0.72F, 0.22F, 0.65F * fade);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINE_LOOP);
        tessellator.addVertex(minX, y, minZ);
        tessellator.addVertex(maxX, y, minZ);
        tessellator.addVertex(maxX, y, maxZ);
        tessellator.addVertex(minX, y, maxZ);
        tessellator.draw();
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
