package com.science.gtnl.client.structureanimation;

import java.nio.DoubleBuffer;
import java.util.Random;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.gtnllib.client.fx.world.FxArcRenderer;
import com.gtnllib.client.fx.world.FxBlockMesh;
import com.gtnllib.client.fx.world.FxMoteRenderer;
import com.gtnllib.client.fx.world.FxMoteRenderer.Kind;
import com.gtnllib.client.fx.world.FxMoteRenderer.Mote;
import com.gtnllib.client.fx.world.FxVoxelOutline;
import com.gtnllib.utils.Vec3d;
import com.science.gtnl.common.packet.StructureAnimationStartPacket.Start;
import com.science.gtnl.common.structureanimation.StructureAnimationStyle;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@SideOnly(Side.CLIENT)
public class StructurePrintAnimation extends StructureMeshTask {

    public static final int INTRO_TICKS = StructureAnimationStyle.PRINT_INTRO, FLASH_TICKS = 8,
        OUTLINE_CELL_LIMIT = 20_000, SPARK_COLOR = 0x38BDF8, SPARK_CORE = 0xE0F9FF;
    public static final double CLIP_MARGIN = 0.04, FRAME_PAD = 0.25;
    public static final String SOUND = "sciencenotleisure:armory.";
    private static final DoubleBuffer CLIP = BufferUtils.createDoubleBuffer(4);
    private static final Random RANDOM = new Random();

    public final int scanTicks;
    public FxBlockMesh mesh;
    public float[] outlineLines;
    public int outlineList;
    public long finishedTick = -1;
    public boolean started;
    private double lastAge;

    public StructurePrintAnimation(Start start, ByteBuf extra) {
        super(start);
        scanTicks = Math.max(1, extra.readShort());
        int[] cells = new int[code.length];
        for (int i = 0; i < cells.length; i++) cells[i] = i;
        mesh = mesh(cells);
        if (code.length <= OUTLINE_CELL_LIMIT) {
            LongOpenHashSet set = new LongOpenHashSet(code.length);
            for (int i = 0; i < code.length; i++)
                set.add(FxVoxelOutline.pack(wx[i] - offsetX, wy[i] - offsetY, wz[i] - offsetZ));
            outlineLines = FxVoxelOutline.mergedLines(set);
        }
        started = start.elapsed() > 0;
        if (start.elapsed() > INTRO_TICKS + scanTicks) finishedTick = startTick + INTRO_TICKS + scanTicks;
    }

    public double progress(double age) {
        double value = Math.max(0, Math.min(1, (age - INTRO_TICKS) / scanTicks));
        if (placingTick >= 0) value = Math.max(value, Math.min(1, (age - (placingTick - startTick)) / 2));
        return value;
    }

    public double scanY(double age) {
        return start.originY() + progress(age) * (start.sizeY() + CLIP_MARGIN);
    }

    @Override
    protected void placedInCopy(int placed) {}

    @Override
    protected boolean settled(long section) {
        return finishedTick >= 0;
    }

    @Override
    protected boolean busy() {
        return finishedTick < 0 || now() - finishedTick < FLASH_TICKS;
    }

    @Override
    protected void releaseOwn() {
        if (mesh != null) mesh.release();
        mesh = null;
        if (outlineList > 0) GL11.glDeleteLists(outlineList, 1);
        outlineList = 0;
        outlineLines = null;
    }

    @Override
    protected void animate(Minecraft minecraft, double age) {
        if (!started && age >= 1) {
            started = true;
            sound(centerX, start.originY(), centerZ, SOUND + "pulse", 0.7F, 1.3F);
        }
        double p = progress(age);
        if (finishedTick < 0 && p >= 1) {
            finishedTick = now();
            sound(centerX, start.originY() + start.sizeY(), centerZ, SOUND + "lock", 0.9F, 1.15F);
            return;
        }
        if (finishedTick >= 0 || age < INTRO_TICKS) return;
        double y = scanY(age);
        int ticks = (int) age;
        if (ticks % 2 == 0) spawnWeldingArcs(y);
        spawnSparks(y);
        if (ticks % 16 == 0) sound(centerX, y, centerZ, SOUND + "hiss", 0.25F, 1.6F);
    }

    private void spawnWeldingArcs(double y) {
        int arcs = Math.max(1, Math.min(4, particles((int) Math.max(2, radius / 4))));
        for (int i = 0; i < arcs; i++) {
            Vec3d edge = randomPerimeterPoint(y);
            Vec3d to = edge.add(
                (RANDOM.nextDouble() - 0.5) * 1.4,
                (RANDOM.nextDouble() - 0.3) * 0.6,
                (RANDOM.nextDouble() - 0.5) * 1.4);
            FxArcRenderer.spawnColored(edge, to, 6, 0.18, 4, SPARK_COLOR, 0.75);
        }
    }

    private void spawnSparks(double y) {
        int count = Math.max(2, Math.min(10, particles((int) Math.max(3, radius))));
        for (int i = 0; i < count; i++) {
            Vec3d origin = randomPerimeterPoint(y);
            double dx = origin.x() - centerX, dz = origin.z() - centerZ;
            double len = Math.max(0.5, Math.hypot(dx, dz));
            Vec3d velocity = new Vec3d(
                dx / len * (0.06 + RANDOM.nextDouble() * 0.1),
                0.04 + RANDOM.nextDouble() * 0.08,
                dz / len * (0.06 + RANDOM.nextDouble() * 0.1));
            FxMoteRenderer.spawn(
                new Mote(
                    Kind.SPARK,
                    origin,
                    0.14 + RANDOM.nextDouble() * 0.08,
                    12 + RANDOM.nextInt(10),
                    SPARK_COLOR,
                    SPARK_CORE).velocity(velocity)
                        .fall(0.94, 0.014)
                        .twinkle(RANDOM.nextFloat() * 6.28F),
                0);
        }
    }

    private Vec3d randomPerimeterPoint(double y) {
        int side = RANDOM.nextInt(4);
        double x, z;
        if (side == 0) {
            x = start.originX() + RANDOM.nextDouble() * start.sizeX();
            z = start.originZ();
        } else if (side == 1) {
            x = start.originX() + RANDOM.nextDouble() * start.sizeX();
            z = start.originZ() + start.sizeZ();
        } else if (side == 2) {
            x = start.originX();
            z = start.originZ() + RANDOM.nextDouble() * start.sizeZ();
        } else {
            x = start.originX() + start.sizeX();
            z = start.originZ() + RANDOM.nextDouble() * start.sizeZ();
        }
        return new Vec3d(x, y, z);
    }

    @Override
    public float glow(double age) {
        double p = progress(age);
        if (p > 0 && p < 1) return 0.75F;
        if (finishedTick >= 0) {
            float left = 1 - (float) (age - (finishedTick - startTick)) / FLASH_TICKS;
            return Math.max(0, left) * 0.65F;
        }
        return (float) Math.min(1, age / INTRO_TICKS) * 0.45F;
    }

    @Override
    protected void drawGlow(double cameraX, double cameraY, double cameraZ) {
        double p = progress(lastAge);
        if (p >= 1 && finishedTick >= 0 && lastAge - (finishedTick - startTick) >= FLASH_TICKS) return;
        double y = p >= 1 ? start.originY() + start.sizeY() : scanY(lastAge);
        GL11.glPushMatrix();
        GL11.glTranslated(-cameraX, -cameraY, -cameraZ);
        drawScanFrame(y, lastAge, 3.2F);
        GL11.glPopMatrix();
    }

    @Override
    protected void render(Minecraft minecraft, double age, float partial, double cameraX, double cameraY,
        double cameraZ) {
        lastAge = age;
        if (mesh != null) mesh.compile(StructureAnimations.compileBudget(), this::placed);
        double p = progress(age);
        double y = scanY(age);
        if (p < 1) drawBlueprint(age, y, cameraX, cameraY, cameraZ);
        if (p > 0) drawClippedMachine(age, p, y, cameraX, cameraY, cameraZ);
        if (age >= INTRO_TICKS * 0.5 && p < 1) drawScanPlane(age, y, cameraX, cameraY, cameraZ);
    }

    private void drawBlueprint(double age, double y, double cameraX, double cameraY, double cameraZ) {
        float intro = (float) Math.min(1, age / INTRO_TICKS);
        GL11.glPushMatrix();
        GL11.glTranslated(offsetX - cameraX, offsetY - cameraY, offsetZ - cameraZ);
        CLIP.clear();
        CLIP.put(0)
            .put(1)
            .put(0)
            .put(-(y - offsetY))
            .flip();
        GL11.glClipPlane(GL11.GL_CLIP_PLANE0, CLIP);
        GL11.glEnable(GL11.GL_CLIP_PLANE0);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        GL11.glLineWidth(1.5F);
        GL11.glColor4f(0.22F, 0.78F, 1F, 0.48F * intro);
        if (outlineLines != null) {
            if (outlineList == 0) compileOutline();
            if (outlineList > 0) GL11.glCallList(outlineList);
        } else {
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawing(GL11.GL_LINES);
            FxVoxelOutline.box(
                tessellator,
                start.originX() - offsetX,
                start.originY() - offsetY,
                start.originZ() - offsetZ,
                start.originX() + start.sizeX() - offsetX,
                start.originY() + start.sizeY() - offsetY,
                start.originZ() + start.sizeZ() - offsetZ);
            tessellator.draw();
        }
        GL11.glDepthMask(true);
        GL11.glDisable(GL11.GL_CLIP_PLANE0);
        GL11.glPopMatrix();
    }

    private void compileOutline() {
        outlineList = GL11.glGenLists(1);
        GL11.glNewList(outlineList, GL11.GL_COMPILE);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINES);
        for (int i = 0; i < outlineLines.length; i += 3)
            tessellator.addVertex(outlineLines[i], outlineLines[i + 1], outlineLines[i + 2]);
        tessellator.draw();
        GL11.glEndList();
    }

    private void drawClippedMachine(double age, double p, double y, double cameraX, double cameraY, double cameraZ) {
        if (mesh == null) return;
        GL11.glPushMatrix();
        GL11.glTranslated(offsetX - cameraX, offsetY - cameraY, offsetZ - cameraZ);
        if (p < 1) {
            CLIP.clear();
            CLIP.put(0)
                .put(-1)
                .put(0)
                .put(y - offsetY)
                .flip();
            GL11.glClipPlane(GL11.GL_CLIP_PLANE0, CLIP);
            GL11.glEnable(GL11.GL_CLIP_PLANE0);
        } else {
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-1, -1);
        }
        FxBlockMesh.solidState();
        drawMesh(mesh);
        if (finishedTick >= 0) {
            float flash = 1 - (float) (age - (finishedTick - startTick)) / FLASH_TICKS;
            if (flash > 0) mesh.drawFlash(flash * 0.55F, this::visible);
        }
        GL11.glDisable(GL11.GL_CLIP_PLANE0);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPopMatrix();
    }

    private void drawScanPlane(double age, double y, double cameraX, double cameraY, double cameraZ) {
        double minX = start.originX() - FRAME_PAD, maxX = start.originX() + start.sizeX() + FRAME_PAD;
        double minZ = start.originZ() - FRAME_PAD, maxZ = start.originZ() + start.sizeZ() + FRAME_PAD;
        GL11.glPushMatrix();
        GL11.glTranslated(-cameraX, -cameraY, -cameraZ);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(0.22F, 0.82F, 1F, 0.16F);
        tessellator.addVertex(minX, y, minZ);
        tessellator.addVertex(minX, y, maxZ);
        tessellator.addVertex(maxX, y, maxZ);
        tessellator.addVertex(maxX, y, minZ);
        tessellator.draw();
        GL11.glColor4f(0.75F, 0.96F, 1F, 0.95F);
        drawScanFrame(y, age, 2.2F);
        GL11.glDepthMask(true);
        GL11.glPopMatrix();
    }

    private void drawScanFrame(double y, double age, float lineWidth) {
        double minX = start.originX() - FRAME_PAD, maxX = start.originX() + start.sizeX() + FRAME_PAD;
        double minZ = start.originZ() - FRAME_PAD, maxZ = start.originZ() + start.sizeZ() + FRAME_PAD;
        double sweepX = minX + (Math.sin(age * 0.45) * 0.5 + 0.5) * (maxX - minX);
        GL11.glLineWidth(lineWidth);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINES);
        tessellator.addVertex(minX, y, minZ);
        tessellator.addVertex(maxX, y, minZ);
        tessellator.addVertex(maxX, y, minZ);
        tessellator.addVertex(maxX, y, maxZ);
        tessellator.addVertex(maxX, y, maxZ);
        tessellator.addVertex(minX, y, maxZ);
        tessellator.addVertex(minX, y, maxZ);
        tessellator.addVertex(minX, y, minZ);
        tessellator.addVertex(sweepX, y, minZ);
        tessellator.addVertex(sweepX, y, maxZ);
        tessellator.draw();
    }
}
