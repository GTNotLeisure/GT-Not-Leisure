package com.science.gtnl.client.projector;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.util.ForgeDirection;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnllib.client.fx.world.FxBlockMesh;
import com.gtnllib.client.fx.world.FxCamera;
import com.gtnllib.client.fx.world.FxVoxelOutline;
import com.gtnllib.utils.Vec3d;
import com.science.gtnl.client.structureanimation.StructureAnimations;
import com.science.gtnl.common.projector.ProjectorOrientation;
import com.science.gtnl.common.projector.ProjectorPlan;
import com.science.gtnl.common.projector.ProjectorSite;
import com.science.gtnl.common.projector.ProjectorStructure;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(side = Side.CLIENT)
public class ProjectorRenderer {

    private static final double STRIPE = 0.5, ARROW = 1.5, INSET = 0.02;
    private static final double SECTION_CENTER = 8, SECTION_RADIUS = 14;
    private static final int YELLOW = 0xFFD200, BLACK = 0x202020, RED = 0xE02020, GREEN = 0x30E040, CYAN = 0x40D8FF;
    private static final int MAX_WRONG_BOXES = 4096, FOCUS_ALPHA_MIN = 110, FOCUS_ALPHA_RANGE = 145;
    private static final double PULSE_MILLIS = 160;
    private static final float GHOST_ALPHA = 0.45f, GHOST_OFFSET = 2f;
    private static final long COMPILE_NANOS = 4_000_000L;
    private static final int CONSTANT_ALPHA = 0x8003, ONE_MINUS_CONSTANT_ALPHA = 0x8004;
    private static final int GHOST_BLOCKS = 200_000;
    private static final List<FxBlockMesh> GHOSTS = new ArrayList<>();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static int checkRevision = -1, wrongRevision = -1, wrongX, wrongY, wrongZ;
    private static float[] wrongLines = new float[0];

    private ProjectorRenderer() {}

    public static void release() {
        for (FxBlockMesh mesh : GHOSTS) mesh.release();
        GHOSTS.clear();
    }

    // The ghost display lists hold texture coordinates of the old block atlas.
    @SubscribeEvent
    public static void texturesReloaded(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() != 0) return;
        release();
        checkRevision = -1;
    }

    @SubscribeEvent
    public static void render(RenderWorldLastEvent event) {
        ProjectorPlan plan = ProjectorClient.plan;
        if (!ProjectorClient.visible || plan == null || StructureAnimations.ownActive()) return;
        FxCamera.capture();
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.renderViewEntity;
        if (camera == null) return;
        double cameraX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.partialTicks;
        double cameraY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.partialTicks;
        double cameraZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.partialTicks;
        ProjectorStructure structure = ProjectorClient.structure;
        ProjectorCheck check = ProjectorClient.CHECK;
        int x = ProjectorClient.x, y = ProjectorClient.y, z = ProjectorClient.z;
        int[] bounds = structure == null ? new int[6] : structure.worldBounds(ProjectorClient.facing);
        int[] offsets = ProjectorClient.offsets;
        GL11.glPushMatrix();
        GL11.glPushAttrib(
            GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_DEPTH_BUFFER_BIT
                | GL11.GL_COLOR_BUFFER_BIT);
        try {
            GL11.glTranslated(x - cameraX, y - cameraY, z - cameraZ);
            if (structure != null && !structure.isEmpty() && plan.display != ProjectorPlan.Display.BOX) {
                drawGhosts(
                    structure,
                    plan.display == ProjectorPlan.Display.DIFFERENCE && ProjectorClient.placed,
                    check,
                    offsets);
            }
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            int accent = ProjectorClient.completeTicks > 0 ? GREEN
                : check.wrong > 0 || ProjectorClient.tooHigh || ProjectorClient.tooLow ? RED : YELLOW;
            for (int pass = 0; pass < 2; pass++) {
                boolean hidden = pass == 0;
                if (hidden) GL11.glDisable(GL11.GL_DEPTH_TEST);
                else GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(false);
                GL11.glLineWidth(hidden ? 2f : 3f);
                int alpha = hidden ? 90 : 255;
                for (int copy = 0; copy < offsets.length; copy += 3) {
                    GL11.glPushMatrix();
                    GL11.glTranslated(offsets[copy], offsets[copy + 1], offsets[copy + 2]);
                    drawHazardBox(
                        bounds[0] - INSET,
                        bounds[1] - INSET,
                        bounds[2] - INSET,
                        bounds[3] + 1 + INSET,
                        bounds[4] + 1 + INSET,
                        bounds[5] + 1 + INSET,
                        accent,
                        alpha);
                    drawBox(0.1, 0.1, 0.1, 0.9, 0.9, 0.9, CYAN, alpha);
                    drawArrow(ProjectorClient.facing.getDirection(), CYAN, alpha);
                    GL11.glPopMatrix();
                }
            }
            if (plan.display != ProjectorPlan.Display.BOX) drawWrong(check, x, y, z);
            if (ProjectorClient.placed) drawFocus(check, x, y, z);
        } finally {
            GL11.glPopAttrib();
            GL11.glPopMatrix();
        }
    }

    // Showing only the missing blocks needs a mesh per copy, as each copy misses different blocks; with every block
    // shown one mesh serves all copies.
    private static void drawGhosts(ProjectorStructure structure, boolean difference, ProjectorCheck check,
        int[] offsets) {
        int copies = offsets.length / 3;
        int meshes = difference ? Math.min(copies, Math.max(1, GHOST_BLOCKS / structure.size)) : 1;
        if (GHOSTS.size() < meshes) checkRevision = -1;
        while (GHOSTS.size() < meshes) GHOSTS.add(new FxBlockMesh());
        while (GHOSTS.size() > meshes) GHOSTS.remove(GHOSTS.size() - 1)
            .release();
        int[][] positions = structure.simulationPositions();
        for (FxBlockMesh mesh : GHOSTS) {
            if (mesh.prepare(structure, structure.world, positions[0], positions[1], positions[2])) checkRevision = -1;
        }
        boolean current = check.structure == structure && check.copies() == copies;
        if (!difference) {
            GHOSTS.get(0)
                .show(null);
            checkRevision = -1;
        } else if (check.revision != checkRevision && current) {
            checkRevision = check.revision;
            for (int copy = 0; copy < meshes; copy++) {
                boolean[] missing = new boolean[structure.size];
                int first = copy * check.site.perCopy;
                for (int cell = 0; cell < structure.size; cell++) {
                    byte state = check.status[first + cell];
                    missing[cell] = state == ProjectorSite.MISSING || state == ProjectorSite.UNKNOWN;
                }
                GHOSTS.get(copy)
                    .show(missing);
            }
        }
        // Parts in view compile first; the rest waits until the camera turns to them.
        long deadline = System.nanoTime() + COMPILE_NANOS;
        List<Predicate<FxBlockMesh.Section>> views = new ArrayList<>();
        for (int copy = 0; copy * 3 < offsets.length; copy++)
            views.add(sectionsInView(structure, offsets[copy * 3], offsets[copy * 3 + 1], offsets[copy * 3 + 2]));
        for (int mesh = 0; mesh < GHOSTS.size(); mesh++) {
            Predicate<FxBlockMesh.Section> ready = difference ? views.get(mesh)
                : section -> views.stream()
                    .anyMatch(view -> view.test(section));
            GHOSTS.get(mesh)
                .compile(Math.max(0, deadline - System.nanoTime()), ready);
        }
        Minecraft.getMinecraft()
            .getTextureManager()
            .bindTexture(TextureMap.locationBlocksTexture);
        boolean mirrored = ProjectorOrientation
            .mirrors(ProjectorOrientation.matrix(structure.canonical, ProjectorClient.facing));
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1f);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glFrontFace(mirrored ? GL11.GL_CW : GL11.GL_CCW);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glColor4f(1f, 1f, 1f, 1f);
        // Pushed slightly back, so a real block in the same place covers its ghost instead of flickering with it.
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(GHOST_OFFSET, GHOST_OFFSET);
        // Depth first, so only the nearest face of the translucent ghost is coloured.
        GL11.glColorMask(false, false, false, false);
        GL11.glDepthMask(true);
        drawCopies(structure, difference, offsets);
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendColor(1f, 1f, 1f, GHOST_ALPHA);
        GL11.glBlendFunc(CONSTANT_ALPHA, ONE_MINUS_CONSTANT_ALPHA);
        drawCopies(structure, difference, offsets);
        GL11.glDepthMask(true);
        GL11.glFrontFace(GL11.GL_CCW);
        GL11.glPolygonOffset(0, 0);
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
    }

    private static void drawCopies(ProjectorStructure structure, boolean difference, int[] offsets) {
        for (int copy = 0; copy * 3 < offsets.length; copy++) {
            FxBlockMesh mesh = difference ? copy < GHOSTS.size() ? GHOSTS.get(copy) : null : GHOSTS.get(0);
            if (mesh == null) break;
            int dx = offsets[copy * 3], dy = offsets[copy * 3 + 1], dz = offsets[copy * 3 + 2];
            GL11.glPushMatrix();
            GL11.glTranslatef(dx, dy, dz);
            applyFacing(structure, ProjectorClient.facing);
            mesh.draw(sectionsInView(structure, dx, dy, dz));
            GL11.glPopMatrix();
        }
    }

    private static Predicate<FxBlockMesh.Section> sectionsInView(ProjectorStructure structure, int dx, int dy, int dz) {
        int[] m = ProjectorOrientation.matrix(structure.canonical, ProjectorClient.facing);
        double baseX = ProjectorClient.x + dx + 0.5, baseY = ProjectorClient.y + dy + 0.5,
            baseZ = ProjectorClient.z + dz + 0.5;
        Minecraft mc = Minecraft.getMinecraft();
        Entity camera = mc.renderViewEntity;
        double reach = mc.gameSettings.renderDistanceChunks * 16 + SECTION_RADIUS;
        return section -> {
            double sx = section.x + SECTION_CENTER - structure.originX - 0.5,
                sy = section.y + SECTION_CENTER - structure.originY - 0.5,
                sz = section.z + SECTION_CENTER - structure.originZ - 0.5;
            double wx = baseX + m[0] * sx + m[3] * sy + m[6] * sz, wy = baseY + m[1] * sx + m[4] * sy + m[7] * sz,
                wz = baseZ + m[2] * sx + m[5] * sy + m[8] * sz;
            if (camera != null && camera.getDistanceSq(wx, wy, wz) > reach * reach) return false;
            return FxCamera.inView(new Vec3d(wx, wy, wz), SECTION_RADIUS);
        };
    }

    private static boolean applyFacing(ProjectorStructure structure, ExtendedFacing facing) {
        int[] m = ProjectorOrientation.matrix(structure.canonical, facing);
        fillMatrix(MATRIX, m);
        GL11.glTranslatef(0.5f, 0.5f, 0.5f);
        GL11.glMultMatrix(MATRIX);
        GL11.glTranslatef(-structure.originX - 0.5f, -structure.originY - 0.5f, -structure.originZ - 0.5f);
        return ProjectorOrientation.mirrors(m);
    }

    public static void fillMatrix(FloatBuffer buffer, int[] rotation) {
        buffer.clear();
        for (int column = 0; column < 3; column++) {
            for (int row = 0; row < 3; row++) buffer.put(rotation[column * 3 + row]);
            buffer.put(0f);
        }
        buffer.put(0f)
            .put(0f)
            .put(0f)
            .put(1f);
        buffer.flip();
    }

    private static void drawHazardBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
        int accent, int alpha) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINES);
        double[] xs = { minX, maxX }, ys = { minY, maxY }, zs = { minZ, maxZ };
        for (double py : ys) {
            for (double pz : zs) stripe(tessellator, minX, py, pz, maxX, py, pz, accent, alpha);
            for (double px : xs) stripe(tessellator, px, py, minZ, px, py, maxZ, accent, alpha);
        }
        for (double px : xs) {
            for (double pz : zs) stripe(tessellator, px, minY, pz, px, maxY, pz, accent, alpha);
        }
        tessellator.draw();
    }

    private static void stripe(Tessellator tessellator, double x1, double y1, double z1, double x2, double y2,
        double z2, int accent, int alpha) {
        double length = Math.abs(x2 - x1) + Math.abs(y2 - y1) + Math.abs(z2 - z1);
        int segments = Math.max(1, (int) Math.ceil(length / STRIPE));
        for (int i = 0; i < segments; i++) {
            double from = (double) i / segments, to = (double) (i + 1) / segments;
            tessellator.setColorRGBA_I((i & 1) == 0 ? accent : BLACK, alpha);
            tessellator.addVertex(x1 + (x2 - x1) * from, y1 + (y2 - y1) * from, z1 + (z2 - z1) * from);
            tessellator.addVertex(x1 + (x2 - x1) * to, y1 + (y2 - y1) * to, z1 + (z2 - z1) * to);
        }
    }

    private static void drawBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int color,
        int alpha) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINES);
        tessellator.setColorRGBA_I(color, alpha);
        FxVoxelOutline.box(tessellator, minX, minY, minZ, maxX, maxY, maxZ);
        tessellator.draw();
    }

    private static void drawArrow(ForgeDirection front, int color, int alpha) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawing(GL11.GL_LINES);
        tessellator.setColorRGBA_I(color, alpha);
        double startX = 0.5 + front.offsetX * 0.5, startY = 0.5 + front.offsetY * 0.5,
            startZ = 0.5 + front.offsetZ * 0.5;
        double tipX = startX + front.offsetX * ARROW, tipY = startY + front.offsetY * ARROW,
            tipZ = startZ + front.offsetZ * ARROW;
        tessellator.addVertex(startX, startY, startZ);
        tessellator.addVertex(tipX, tipY, tipZ);
        ForgeDirection side = front.offsetY == 0 ? ForgeDirection.UP : ForgeDirection.EAST;
        for (int sign = -1; sign <= 1; sign += 2) {
            tessellator.addVertex(tipX, tipY, tipZ);
            tessellator.addVertex(
                tipX - front.offsetX * 0.4 + side.offsetX * 0.3 * sign,
                tipY - front.offsetY * 0.4 + side.offsetY * 0.3 * sign,
                tipZ - front.offsetZ * 0.4 + side.offsetZ * 0.3 * sign);
        }
        tessellator.draw();
    }

    // Neighbouring wrong blocks share one outline, rebuilt only when the check changes.
    private static void drawWrong(ProjectorCheck check, int x, int y, int z) {
        if (!check.isActive() || check.wrong == 0) return;
        if (check.revision != wrongRevision || x != wrongX || y != wrongY || z != wrongZ) {
            wrongRevision = check.revision;
            wrongX = x;
            wrongY = y;
            wrongZ = z;
            LongOpenHashSet cells = new LongOpenHashSet();
            for (int cell = 0; cell < check.status.length && cells.size() < MAX_WRONG_BOXES; cell++) {
                if (check.status[cell] == ProjectorSite.WRONG) cells
                    .add(FxVoxelOutline.pack(check.worldX[cell] - x, check.worldY[cell] - y, check.worldZ[cell] - z));
            }
            wrongLines = FxVoxelOutline.mergedLines(cells);
        }
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(2f);
        FxVoxelOutline.draw(Tessellator.instance, wrongLines, RED, 220);
    }

    private static void drawFocus(ProjectorCheck check, int x, int y, int z) {
        int[] cells = ProjectorClient.focus;
        if (cells.length == 0 || !check.isActive()) return;
        double pulse = (Math.sin(Minecraft.getSystemTime() / PULSE_MILLIS) + 1) / 2;
        Tessellator tessellator = Tessellator.instance;
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glLineWidth(3f);
        tessellator.startDrawing(GL11.GL_LINES);
        tessellator.setColorRGBA_I(ProjectorClient.focusColor, (int) (FOCUS_ALPHA_MIN + FOCUS_ALPHA_RANGE * pulse));
        int drawn = 0;
        for (int first = 0; first < check.worldX.length && drawn < MAX_WRONG_BOXES; first += check.site.perCopy) {
            for (int i = 0; i < cells.length && drawn < MAX_WRONG_BOXES; i++) {
                int cell = first + cells[i];
                if (cells[i] >= check.site.perCopy || cell >= check.worldX.length) continue;
                drawn++;
                double minX = check.worldX[cell] - x - INSET, minY = check.worldY[cell] - y - INSET,
                    minZ = check.worldZ[cell] - z - INSET;
                FxVoxelOutline.box(
                    tessellator,
                    minX,
                    minY,
                    minZ,
                    minX + 1 + 2 * INSET,
                    minY + 1 + 2 * INSET,
                    minZ + 1 + 2 * INSET);
            }
        }
        tessellator.draw();
    }
}
