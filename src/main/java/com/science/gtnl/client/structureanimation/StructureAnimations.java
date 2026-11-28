package com.science.gtnl.client.structureanimation;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.world.WorldEvent;

import org.lwjgl.opengl.GL11;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.gtnhlib.eventbus.Phase;
import com.gtnllib.client.fx.screen.FxGlow;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(side = Side.CLIENT, phase = Phase.PRE)
public class StructureAnimations {

    public interface Factory {

        /** Builds the client half of a build style from the common start header and the style's own bytes. */
        StructureAnimationTask create(Start start, ByteBuf extra);
    }

    public static final int MAX_TASKS = 4;
    public static final long PLACE_BUDGET_NANOS = 4_000_000L, COMPILE_BUDGET_NANOS = 4_000_000L,
        COMPILE_BUDGET_MAX_NANOS = 10_000_000L;
    public static final float GLOW_SPREAD = 1.5f;
    private static final Map<StructureAnimationStyle, Factory> FACTORIES = new EnumMap<>(StructureAnimationStyle.class);
    private static final Int2ObjectLinkedOpenHashMap<StructureAnimationTask> TASKS = new Int2ObjectLinkedOpenHashMap<>();
    private static final FxGlow GLOW_FX = new FxGlow();
    private static long clientTicks, compileDeadline, lastFrame;

    private StructureAnimations() {}

    public static void registerStyles() {
        FACTORIES.put(StructureAnimationStyle.REVEAL, StructureRevealAnimation::new);
        FACTORIES.put(StructureAnimationStyle.SUMMON, StructureSummonAnimation::new);
        FACTORIES.put(StructureAnimationStyle.FLIGHT, StructureFlightAnimation::new);
        FACTORIES.put(StructureAnimationStyle.PRINT, StructurePrintAnimation::new);
        FACTORIES.put(StructureAnimationStyle.RISE, StructureRiseAnimation::new);
        FACTORIES.put(StructureAnimationStyle.SINGULARITY, StructureSingularityAnimation::new);
        FACTORIES.put(StructureAnimationStyle.WARP_IN, StructureWarpInAnimation::new);
        FACTORIES.put(StructureAnimationStyle.ORBITAL, StructureOrbitalAnimation::new);
    }

    public static long clientTicks() {
        return clientTicks;
    }

    public static long compileBudget() {
        return Math.max(0, compileDeadline - System.nanoTime());
    }

    // Only the local player's own builds; someone else's build nearby must not block or hide this player's projector.
    public static boolean ownActive() {
        return ownTask() != null;
    }

    public static StructureAnimationTask ownTask() {
        for (StructureAnimationTask task : TASKS.values()) if (task.start.own()) return task;
        return null;
    }

    public static void start(Start start, byte[] extra) {
        Factory factory = FACTORIES.get(start.style());
        if (factory == null) return;
        StructureAnimationTask task;
        try {
            task = factory.create(start, Unpooled.wrappedBuffer(extra));
        } catch (RuntimeException failure) {
            ScienceNotLeisure.LOG.error("Projector build animation could not start", failure);
            return;
        }
        StructureAnimationTask replaced = TASKS.remove(start.job());
        if (replaced != null) replaced.release();
        while (TASKS.size() >= MAX_TASKS) TASKS.removeFirst()
            .release();
        TASKS.put(start.job(), task);
    }

    public static void state(int id, int state, int processed, int[] skipped) {
        StructureAnimationTask task = TASKS.get(id);
        if (task == null) return;
        if (skipped.length > 0) task.drop(skipped);
        task.state(state, processed);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || TASKS.isEmpty() || minecraft.isGamePaused()) return;
        clientTicks++;
        Iterator<StructureAnimationTask> iterator = TASKS.values()
            .iterator();
        while (iterator.hasNext()) {
            StructureAnimationTask task = iterator.next();
            if (!task.expired(minecraft)) task.tick(minecraft);
            if (task.expired(minecraft) || task.finished()) {
                task.release();
                iterator.remove();
            }
        }
    }

    @SubscribeEvent
    public static void unload(WorldEvent.Unload event) {
        if (!event.world.isRemote) return;
        for (StructureAnimationTask task : TASKS.values()) task.release();
        TASKS.clear();
    }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent event) {
        if (TASKS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity camera = minecraft.renderViewEntity;
        if (camera == null || minecraft.theWorld == null) return;
        long frame = System.nanoTime();
        compileDeadline = frame
            + Math.max(COMPILE_BUDGET_NANOS, Math.min(COMPILE_BUDGET_MAX_NANOS, (frame - lastFrame) / 4));
        lastFrame = frame;
        double cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        int dimension = minecraft.theWorld.provider.dimensionId;
        float glow = 0;
        for (StructureAnimationTask task : TASKS.values()) {
            if (task.start.dimension() != dimension) continue;
            double age = task.age(event.partialTicks);
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                task.render(minecraft, age, event.partialTicks, cameraX, cameraY, cameraZ);
            } finally {
                GL11.glDisable(GL11.GL_CLIP_PLANE0);
                GL11.glPopAttrib();
                GL11.glPopMatrix();
            }
            glow = Math.max(glow, task.glow(age));
        }
        if (glow > 0) drawGlow(dimension, cameraX, cameraY, cameraZ, glow);
    }

    private static void drawGlow(int dimension, double cameraX, double cameraY, double cameraZ, float strength) {
        if (!GLOW_FX.begin()) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            for (StructureAnimationTask task : TASKS.values()) {
                if (task.start.dimension() != dimension) continue;
                GL11.glColor4f(1, 1, 1, 1);
                task.drawGlow(cameraX, cameraY, cameraZ);
            }
        } finally {
            GL11.glPopAttrib();
        }
        GLOW_FX.end(strength, GLOW_SPREAD, 2);
    }
}
