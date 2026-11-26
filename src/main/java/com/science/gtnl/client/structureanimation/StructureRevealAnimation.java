package com.science.gtnl.client.structureanimation;

import static com.science.gtnl.common.structureanimation.StructureAnimationStyle.TICKS_PER_SECOND;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.packet.StructureAnimationStatePacket;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;

@SideOnly(Side.CLIENT)
public class StructureRevealAnimation extends StructureAnimationTask {

    public static final float HOLD = 0.1f, MESH_DELAY = 0.15f, MIN_FADE = 0.3f, ABORT_FADE = 0.3f, FILL = 0.07f,
        LINE = 0.9f, GLOW = 0.9f;

    public final float blinkFade, solidAt;
    public final CompletableFuture<StructureRevealShape> shape;
    public final FloatArrayList progressTimes = new FloatArrayList();
    public final IntArrayList progressValues = new IntArrayList();
    public float endAt = -1;
    public int compiled;
    public int[] shellLists = new int[0];
    public int outlineList;
    public boolean faded;
    private float[] groupProcessed;
    private int[] groupCursor;
    private int controllerGroup = -1;

    public StructureRevealAnimation(Start start, ByteBuf extra) {
        super(start);
        blinkFade = extra.readFloat();
        solidAt = blinkFade + extra.readFloat();
        int sizeX = start.sizeX(), sizeY = start.sizeY(), sizeZ = start.sizeZ();
        byte[] kinds = start.kinds();
        shape = CompletableFuture.supplyAsync(() -> {
            try {
                return new StructureRevealShape(sizeX, sizeY, sizeZ, kinds);
            } catch (Exception failure) {
                ScienceNotLeisure.LOG.error("Projector build shape could not be read", failure);
                return null;
            }
        });
    }

    public float seconds() {
        return (now() - startTick) / (float) TICKS_PER_SECOND;
    }

    public float solid() {
        return placingTick >= 0 ? Math.min(solidAt, (placingTick - startTick) / (float) TICKS_PER_SECOND) : solidAt;
    }

    public StructureRevealShape ready() {
        StructureRevealShape built = shape.getNow(null);
        return built != null && compiled > built.groups ? built : null;
    }

    @Override
    public void state(int state, int processed) {
        super.state(state, processed);
        float now = seconds();
        boolean done = state == StructureAnimationStatePacket.DONE;
        if (done || state == StructureAnimationStatePacket.PROGRESS) {
            progressTimes.add(now);
            progressValues.add(done ? Integer.MAX_VALUE : processed);
        }
        if (state != StructureAnimationStatePacket.PLACING && state != StructureAnimationStatePacket.PROGRESS)
            endAt = now;
    }

    // An aborted or stalled build fades out rather than vanishing.
    @Override
    public boolean expired(Minecraft minecraft) {
        return shape.isDone() && shape.getNow(null) == null || minecraft.theWorld == null
            || minecraft.theWorld.provider.dimensionId != start.dimension();
    }

    @Override
    public boolean finished() {
        return faded;
    }

    @Override
    protected void tick(Minecraft minecraft) {
        if (endAt < 0 && timedOut()) {
            aborted = true;
            endAt = seconds();
        }
    }

    // A piece starts fading once the progress packet covering its last cell has arrived, plus time for the client to
    // rebuild the chunk meshes under it.
    public float cover(StructureRevealShape built, int group, float t) {
        float fadeStart = solid() + HOLD;
        float processed = processedAt(built, group);
        float cover = 1;
        if (processed >= 0) {
            float begin = Math.max(fadeStart, processed + MESH_DELAY);
            float end = Math.max(fadeStart + built.fadeTotal * (group + 1) / built.groups, begin + MIN_FADE);
            cover = 1 - clamp((t - begin) / (end - begin));
        }
        if (aborted) cover = Math.min(cover, 1 - clamp((t - endAt) / ABORT_FADE));
        return cover;
    }

    // When the server reported this piece placed, or -1. The piece holding the controller waits for the end, because
    // the server places the controller last whatever the order. Each piece is looked up once.
    private float processedAt(StructureRevealShape built, int group) {
        if (groupProcessed == null) {
            groupProcessed = new float[built.groups];
            Arrays.fill(groupProcessed, -1);
            groupCursor = new int[built.groups];
            controllerGroup = built.groupAt(
                start.controllerX() - start.originX(),
                start.controllerY() - start.originY(),
                start.controllerZ() - start.originZ());
        }
        if (groupProcessed[group] >= 0) return groupProcessed[group];
        if (group == controllerGroup && doneTick < 0) return -1;
        for (int i = groupCursor[group]; i < progressValues.size(); i++) {
            if (progressValues.getInt(i) > built.last[group]) return groupProcessed[group] = progressTimes.getFloat(i);
        }
        groupCursor[group] = progressValues.size();
        return -1;
    }

    @Override
    public void release() {
        for (int list : shellLists) if (list > 0) GL11.glDeleteLists(list, 1);
        if (outlineList > 0) GL11.glDeleteLists(outlineList, 1);
        shellLists = new int[0];
        outlineList = 0;
    }

    @Override
    protected void render(Minecraft minecraft, double age, float partial, double cameraX, double cameraY,
        double cameraZ) {
        compileStep();
        StructureRevealShape built = ready();
        if (built == null) return;
        float t = (float) (age / TICKS_PER_SECOND);
        GL11.glTranslated(start.originX() - cameraX, start.originY() - cameraY, start.originZ() - cameraZ);
        prepare();
        if (t < solid()) hologram(t);
        else faded = solid(built, t);
    }

    @Override
    public float glow(double age) {
        float t = (float) (age / TICKS_PER_SECOND);
        return ready() != null && t < solid() ? blink(t) * GLOW : 0;
    }

    @Override
    protected void drawGlow(double cameraX, double cameraY, double cameraZ) {
        if (ready() == null || blink(seconds()) <= 0) return;
        GL11.glPushMatrix();
        GL11.glTranslated(start.originX() - cameraX, start.originY() - cameraY, start.originZ() - cameraZ);
        for (int list : shellLists) GL11.glCallList(list);
        GL11.glPopMatrix();
    }

    private static void prepare() {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_CONSTANT_COLOR, GL11.GL_ONE);
        GL11.glColor4f(1, 1, 1, 1);
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    private float blink(float t) {
        return Math.max(0, 1 - t / blinkFade);
    }

    private static void depthOnly(int[] lists, boolean[] include) {
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(true);
        for (int g = 0; g < lists.length; g++) if (include == null || include[g]) GL11.glCallList(lists[g]);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(false);
    }

    private static void constant(float value) {
        GL14.glBlendColor(value, value, value, 1);
    }

    // No real blocks yet: shells sit slightly behind the outline so the lines pass the depth test, and every pixel is
    // covered once so the white fades evenly instead of lingering where faces overlap.
    private void hologram(float t) {
        GL11.glPolygonOffset(1, 1);
        depthOnly(shellLists, null);
        constant(FILL);
        for (int list : shellLists) GL11.glCallList(list);
        constant(LINE);
        GL11.glLineWidth(2f);
        GL11.glCallList(outlineList);
        float blink = blink(t);
        if (blink > 0) {
            constant(blink);
            for (int list : shellLists) GL11.glCallList(list);
        }
    }

    // Real blocks are being placed underneath: shells are pulled in front of their faces and added over them, so a
    // fading piece looks over-exposed rather than see-through.
    private boolean solid(StructureRevealShape built, float t) {
        float[] covers = new float[built.groups];
        boolean[] visible = new boolean[built.groups];
        boolean any = false;
        for (int g = 0; g < built.groups; g++) {
            covers[g] = cover(built, g, t);
            visible[g] = covers[g] > 0;
            any |= visible[g];
        }
        if (!any) return endAt >= 0;
        GL11.glPolygonOffset(-1, -1);
        depthOnly(shellLists, visible);
        for (int g = 0; g < built.groups; g++) {
            if (!visible[g]) continue;
            constant(covers[g]);
            GL11.glCallList(shellLists[g]);
        }
        return false;
    }

    private void compileStep() {
        StructureRevealShape built = shape.getNow(null);
        if (built == null) return;
        do compileList(built); while (compiled <= built.groups && StructureAnimations.compileBudget() > 0);
    }

    private void compileList(StructureRevealShape built) {
        if (compiled > built.groups) return;
        if (shellLists.length == 0) shellLists = new int[built.groups];
        Tessellator tessellator = Tessellator.instance;
        int list = GL11.glGenLists(1);
        GL11.glNewList(list, GL11.GL_COMPILE);
        if (compiled < built.groups) {
            tessellator.startDrawingQuads();
            FloatArrayList shell = built.shells[compiled];
            for (int i = 0; i < shell.size(); i += 3)
                tessellator.addVertex(shell.getFloat(i), shell.getFloat(i + 1), shell.getFloat(i + 2));
            tessellator.draw();
            GL11.glEndList();
            shellLists[compiled] = list;
        } else {
            tessellator.startDrawing(GL11.GL_LINES);
            FloatArrayList outline = built.outline;
            for (int i = 0; i < outline.size(); i += 3)
                tessellator.addVertex(outline.getFloat(i), outline.getFloat(i + 1), outline.getFloat(i + 2));
            tessellator.draw();
            GL11.glEndList();
            outlineList = list;
        }
        compiled++;
    }

    private static float clamp(float value) {
        return value < 0 ? 0 : value > 1 ? 1 : value;
    }
}
