package com.science.gtnl.common.gui.recipe;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;

import org.lwjgl.opengl.GL11;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard;
import com.science.gtnl.common.gui.modularui.TreeDiagramTheme;

import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class TreeDiagramNEIBoard implements IDrawable {

    public static final int WIDTH = 168, HEIGHT = 168, OFFSET_X = 5;
    public static final int COLUMNS = 5, ROWS = 4, SLOTS = COLUMNS * ROWS;
    public static final int SLOT_SIZE = 18, SLOT_PITCH = SLOT_SIZE;
    public static final int GRID_X = 7, ITEM_Y = 9, FLUID_Y = ITEM_Y + ROWS * SLOT_PITCH + 2,
        BOTTOM = FLUID_Y + ROWS * SLOT_PITCH;
    public static final int CORE_X = 139, CORE_Y = 86, ORBIT = 26;
    public static final int TRANSFER_X = CORE_X - 27, TRANSFER_Y = CORE_Y - 8, TRANSFER_SIZE = 16;
    public static final int LOOP_MILLIS = 2400, GLIMMER_LOOP = 144, GLIMMER_FADE = 33, FRAMES_PER_LOOP = 72;

    @Desugar
    public record Ramp(int[] stops) {

        public int at(int index) {
            return stops[index];
        }

        public Ramp faded(int background) {
            int[] out = new int[stops.length];
            for (int i = 0; i < stops.length; i++) out[i] = mix(stops[i], background, 0.72);
            return new Ramp(out);
        }
    }

    @Desugar
    public record Style(boolean dark, int background, int scan, int dots, int frame, int highlight, int accent,
        int label, int ringAccent, int ringAccentDim, int deco, Map<String, Ramp> ramps, int socketUsed,
        int socketEmpty, int fluidWell, int fluidEmpty, int fluidDrop, int[] edgeDark, int[] edgeLit, int fluidLed,
        int glimmerBase, int itemGlimmer, int fluidGlimmer, int glimmerShift) {

        public Ramp ramp(String net) {
            return ramps.get(net);
        }
    }

    public static final Style LIGHT = new Style(
        false,
        rgb(222, 227, 233),
        -1,
        rgb(204, 211, 220),
        rgb(128, 139, 154),
        rgb(250, 252, 255),
        rgb(44, 146, 202),
        rgb(112, 124, 140),
        rgb(52, 146, 198),
        rgb(150, 186, 210),
        rgb(204, 211, 220),
        Map.of(
            "item",
            ramp(186, 176, 220, 152, 126, 226, 120, 82, 232, 92, 46, 218),
            "fluid",
            ramp(160, 204, 178, 108, 190, 144, 58, 170, 106, 28, 138, 78),
            "cu",
            ramp(160, 204, 222, 98, 182, 216, 44, 160, 212, 18, 126, 186),
            "power",
            ramp(226, 184, 158, 228, 140, 92, 228, 106, 44, 194, 78, 18),
            "nanite",
            ramp(220, 200, 150, 224, 178, 72, 220, 156, 20, 182, 122, 6)),
        rgb(244, 247, 251),
        rgb(230, 234, 240),
        rgb(244, 247, 251),
        rgb(230, 234, 240),
        rgb(176, 206, 192),
        new int[] { rgb(140, 152, 170), rgb(176, 186, 200) },
        new int[] { rgb(255, 255, 255), rgb(246, 248, 251) },
        rgb(36, 160, 110),
        rgb(222, 227, 233),
        rgb(170, 150, 222),
        rgb(118, 186, 152),
        -1);

    public static final Style DARK = new Style(
        true,
        rgb(9, 13, 21),
        rgb(7, 10, 17),
        rgb(24, 34, 48),
        rgb(64, 82, 106),
        rgb(40, 54, 72),
        rgb(84, 204, 255),
        rgb(88, 112, 140),
        rgb(84, 204, 255),
        rgb(34, 82, 110),
        rgb(26, 36, 50),
        Map.of(
            "item",
            ramp(62, 46, 108, 118, 88, 198, 172, 142, 255, 226, 212, 255),
            "fluid",
            ramp(28, 74, 52, 58, 148, 98, 112, 232, 162, 204, 255, 228),
            "cu",
            ramp(22, 64, 88, 44, 138, 188, 102, 216, 252, 206, 246, 255),
            "power",
            ramp(86, 42, 18, 168, 88, 38, 250, 150, 90, 255, 218, 186),
            "nanite",
            ramp(86, 66, 18, 168, 128, 28, 250, 206, 92, 255, 242, 196)),
        rgb(17, 27, 42),
        rgb(11, 16, 24),
        rgb(5, 12, 16),
        rgb(5, 12, 16),
        rgb(47, 110, 85),
        new int[] { rgb(4, 7, 12), rgb(8, 12, 18) },
        new int[] { rgb(60, 84, 112), rgb(26, 34, 46) },
        rgb(100, 230, 170),
        rgb(14, 21, 31),
        rgb(98, 74, 156),
        rgb(44, 114, 88),
        -1);

    public static final int CORE_EDGE = rgb(80, 190, 236), CORE_INNER = rgb(26, 58, 82), CORE_FILL = rgb(10, 17, 28),
        CORE_WELL = rgb(40, 112, 152), CORE_FLOOR = rgb(12, 22, 36);
    public static final int CELL_DIM = rgb(18, 44, 64), CELL_LIT = rgb(70, 190, 240), CELL_HOT = rgb(220, 250, 255);
    public static final int[][] CORE_RINGS = { { 14, 20 }, { 17, 26 }, { 20, 32 } };
    public static final int[][] CORE_PATHS = { { 18, 42, 74 }, { 12, 35, 64 }, { 4, 25, 52 }, { 18, 43, 76 }, { 5, 27 },
        { 2, 23, 51 }, { 15, 39, 71 }, { 12, 35, 66 }, { 5, 26, 55 }, { 0, 20, 46 }, { 6 }, { 13, 36 } };
    public static final int[][] DECO = { { 112, 120, 112, 132, 120, 140, 128, 140 }, { 106, 152, 118, 152, 124, 158 },
        { 160, 22, 160, 40, 154, 46 }, { 150, 10, 150, 18, 156, 24 } };
    public static final int[][] ORBIT_GAPS = { { 80, 100 }, { 170, 190 }, { 260, 280 }, { 37, 53 } };
    public static final Map<Character, String[]> FONT = Map.of(
        'C',
        new String[] { "011", "100", "100", "100", "011" },
        'U',
        new String[] { "101", "101", "101", "101", "111" },
        'E',
        new String[] { "111", "100", "110", "100", "111" },
        'T',
        new String[] { "111", "010", "010", "010", "010" },
        'R',
        new String[] { "110", "101", "110", "101", "101" },
        'D',
        new String[] { "110", "101", "101", "101", "110" },
        'I',
        new String[] { "111", "010", "010", "010", "111" },
        'A',
        new String[] { "010", "101", "111", "101", "101" },
        'G',
        new String[] { "011", "100", "101", "101", "011" },
        'M',
        new String[] { "10001", "11011", "10101", "10001", "10001" });

    private static final int BOARD_CUT = 5;
    private static final int[] CELLS = ringCells();
    private static final float[][] GLIMMERS = { glimmerPoints(11), glimmerPoints(29) };
    private static final Map<String, Layer> LAYERS = new Object2ObjectOpenHashMap<>();

    private final Supplier<GTNEIDefaultHandler.CachedDefaultRecipe> recipe;

    public TreeDiagramNEIBoard(Supplier<GTNEIDefaultHandler.CachedDefaultRecipe> recipe) {
        this.recipe = recipe;
    }

    public static Style style() {
        return TreeDiagramTheme.dark() ? DARK : LIGHT;
    }

    @Override
    public void draw(float x, float y, float width, float height, float partialTicks) {
        GTNEIDefaultHandler.CachedDefaultRecipe cached = recipe.get();
        if (cached == null) return;
        GTRecipe current = cached.mRecipe;
        int items = Math.min(SLOTS, count(current.mInputs));
        int fluids = Math.min(SLOTS, count(current.mFluidInputs));
        Style style = style();
        Layer layer = layer(style, rows(items), rows(fluids));
        layer.drawTexture(x, y);
        long now = Minecraft.getSystemTime();
        float phase = now % LOOP_MILLIS / (float) LOOP_MILLIS;
        TreeDiagramBoard quads = new TreeDiagramBoard(x, y);
        drawScan(quads, style, phase);
        drawComets(quads, layer, phase);
        drawCore(quads, phase);
        drawRing(quads, style, layer, phase);
        drawItemSockets(quads, style, items);
        drawFluidSockets(quads, style, fluids);
        float frame = now * FRAMES_PER_LOOP / (float) LOOP_MILLIS;
        drawGlimmer(quads, style, ITEM_Y, GLIMMERS[0], style.itemGlimmer(), frame);
        drawGlimmer(quads, style, FLUID_Y, GLIMMERS[1], style.fluidGlimmer(), frame);
        quads.rect(CORE_X - 9, CORE_Y - 9, 18, 18, CORE_FLOOR);
        for (int i = 0; i < 4; i++) {
            quads.rect(TRANSFER_X + 5 + i, CORE_Y - 4 + i, 1, 1, CORE_EDGE);
            quads.rect(TRANSFER_X + 5 + i, CORE_Y + 3 - i, 1, 1, CORE_EDGE);
        }
        quads.flush();
    }

    private static void drawScan(TreeDiagramBoard quads, Style style, float phase) {
        int row = 4 + (int) ((HEIGHT - 8) * phase);
        quads.rect(4, row, WIDTH - 8, 1, 0x38000000 | style.accent() & 0xFFFFFF);
        quads.rect(4, row + 1, WIDTH - 8, 1, 0x1F000000 | style.accent() & 0xFFFFFF);
    }

    private static void drawComets(TreeDiagramBoard quads, Layer layer, float phase) {
        for (Fibre fibre : layer.fibres) {
            int n = fibre.pixels.length;
            int head = (int) (((phase * 2 + fibre.offset) % 1) * n);
            int[] colors = { 0xFFFFFFFF, fibre.ramp.at(3), fibre.ramp.at(3), fibre.ramp.at(2), fibre.ramp.at(2),
                fibre.ramp.at(1) };
            for (int d = 0; d < colors.length; d++) {
                int index = head - d;
                if (index < 0 || index >= n) continue;
                int packed = fibre.pixels[index];
                quads.rect(TreeDiagramBoard.unpackX(packed), TreeDiagramBoard.unpackY(packed), 1, 1, colors[d]);
            }
        }
    }

    private static void drawCore(TreeDiagramBoard quads, float phase) {
        for (int k = 0; k < CORE_PATHS.length; k++) {
            int[] path = CORE_PATHS[k];
            float t = (phase - k / (float) CORE_PATHS.length) % 1;
            if (t < 0) t += 1;
            float reach = t * (path.length + 5);
            for (int i = 0; i < path.length && i <= reach; i++) {
                float age = reach - i;
                int color = age < 1 ? CELL_HOT : mix(CELL_LIT, CELL_DIM, (age - 1) / 5);
                int cell = CELLS[path[i]];
                quads.rect(TreeDiagramBoard.unpackX(cell), TreeDiagramBoard.unpackY(cell), 2, 2, color);
            }
        }
    }

    private static void drawRing(TreeDiagramBoard quads, Style style, Layer layer, float phase) {
        float head = phase * 360;
        for (int i = 0; i < layer.ring.size(); i += 3) {
            float lag = ((head - layer.ring.getInt(i)) % 360 + 360) % 360;
            if (lag > 40) continue;
            float k = 1 - lag / 40;
            quads.rect(layer.ring.getInt(i + 1), layer.ring.getInt(i + 2), 1, 1, mix(style.accent(), 0xFFFFFFFF, k));
        }
    }

    private static void drawItemSockets(TreeDiagramBoard quads, Style style, int used) {
        for (int i = 0; i < SLOTS; i++) {
            int x = GRID_X + i % COLUMNS * SLOT_PITCH, y = ITEM_Y + i / COLUMNS * SLOT_PITCH;
            boolean on = i < used;
            quads.rect(x, y, SLOT_SIZE, SLOT_SIZE, on ? style.socketUsed() : style.socketEmpty());
            int dark = style.edgeDark()[on ? 0 : 1], lit = style.edgeLit()[on ? 0 : 1];
            slotFrame(quads, x, y, dark, lit);
        }
    }

    private static void drawFluidSockets(TreeDiagramBoard quads, Style style, int used) {
        for (int i = 0; i < SLOTS; i++) {
            int x = GRID_X + i % COLUMNS * SLOT_PITCH, y = FLUID_Y + i / COLUMNS * SLOT_PITCH;
            boolean on = i < used;
            quads.rect(x, y, SLOT_SIZE, SLOT_SIZE, on ? style.fluidWell() : style.fluidEmpty());
            int dark = style.edgeDark()[on ? 0 : 1];
            int lip = mix(style.edgeLit()[on ? 0 : 1], style.fluidLed(), on ? 0.3 : 0.1);
            slotFrame(quads, x, y, dark, lip);
            if (!on) {
                quads.rect(x + 8, y + 6, 2, 2, style.fluidDrop());
                quads.rect(x + 7, y + 8, 4, 3, style.fluidDrop());
                quads.rect(x + 8, y + 11, 2, 1, style.fluidDrop());
            }
        }
    }

    private static void slotFrame(TreeDiagramBoard quads, int x, int y, int shadow, int highlight) {
        quads.rect(x, y, SLOT_SIZE, 1, shadow);
        quads.rect(x, y + 1, 1, SLOT_SIZE - 1, shadow);
        quads.rect(x + SLOT_SIZE - 1, y + 1, 1, SLOT_SIZE - 1, highlight);
        quads.rect(x + 1, y + SLOT_SIZE - 1, SLOT_SIZE - 1, 1, highlight);
    }

    private static void drawGlimmer(TreeDiagramBoard quads, Style style, int top, float[] points, int peak,
        float frame) {
        for (int i = 0; i < points.length; i += 4) {
            float t = ((frame - points[i + 2]) % GLIMMER_LOOP + GLIMMER_LOOP) % GLIMMER_LOOP;
            if (t >= 2 * GLIMMER_FADE) continue;
            float strength = points[i + 3];
            double level = t < GLIMMER_FADE ? strength * (0.5 - 0.5 * Math.cos(Math.PI * t / GLIMMER_FADE))
                : strength * (0.5 + 0.5 * Math.cos(Math.PI * (t - GLIMMER_FADE) / GLIMMER_FADE));
            int x = GRID_X + (int) points[i] * SLOT_PITCH + style.glimmerShift();
            int y = top + (int) points[i + 1] * SLOT_PITCH + style.glimmerShift();
            quads.rect(x, y, 1, 1, mix(style.glimmerBase(), peak, level));
        }
    }

    private static int count(Object[] values) {
        int n = 0;
        if (values != null) for (Object value : values) if (value != null) n++;
        return n;
    }

    private static int rows(int count) {
        return (count + COLUMNS - 1) / COLUMNS;
    }

    private static Layer layer(Style style, int itemRows, int fluidRows) {
        String key = style.dark() + ":" + itemRows + ":" + fluidRows;
        Layer layer = LAYERS.get(key);
        if (layer == null) {
            layer = new Layer(style, itemRows, fluidRows);
            LAYERS.put(key, layer);
        }
        return layer;
    }

    @Desugar
    public record Fibre(int[] pixels, Ramp ramp, float offset) {}

    public static class Layer {

        public final int[] pixels = new int[WIDTH * HEIGHT];
        public final List<Fibre> fibres = new ObjectArrayList<>();
        public final IntArrayList ring = new IntArrayList();
        private DynamicTexture texture;

        public Layer(Style style, int itemRows, int fluidRows) {
            paintBoard(style);
            int left = CORE_X - ORBIT;
            paintRows(
                style,
                "item",
                ITEM_Y,
                new int[] { CORE_Y - 9, CORE_Y - 7, CORE_Y - 5, CORE_Y - 3 },
                true,
                itemRows);
            paintRows(
                style,
                "fluid",
                FLUID_Y,
                new int[] { CORE_Y + 2, CORE_Y + 4, CORE_Y + 6, CORE_Y + 8 },
                false,
                fluidRows);
            List<Fibre> extra = new ObjectArrayList<>();
            for (int k = 0; k < 4; k++) {
                int x = CORE_X - 3 + k * 2;
                extra.add(halo(style, new int[] { x, 2, x, CORE_Y - ORBIT }, style.ramp("cu"), 0.13f + k * 0.29f));
                extra.add(
                    halo(
                        style,
                        new int[] { x, HEIGHT - 3, x, CORE_Y + ORBIT - 1 },
                        style.ramp("power"),
                        0.13f + k * 0.29f));
            }
            for (int k = 0; k < 2; k++) {
                extra.add(
                    halo(
                        style,
                        new int[] { WIDTH - 3, CORE_Y + 26 + k * 2, CORE_X + 20 - k * 2, CORE_Y + 20 },
                        style.ramp("nanite"),
                        0.13f + k * 0.29f));
            }
            for (Fibre fibre : extra) paint(fibre);
            fibres.addAll(extra);
            paintOrbit(style);
            dock(left, CORE_Y - 6, style.ramp("item"));
            dock(left, CORE_Y + 5, style.ramp("fluid"));
            dock(CORE_X, CORE_Y - ORBIT, style.ramp("cu"));
            dock(CORE_X, CORE_Y + ORBIT - 1, style.ramp("power"));
            dock(CORE_X + 18, CORE_Y + 18, style.ramp("nanite"));
            label(
                "CU",
                CORE_X + 6,
                5,
                style.ramp("cu")
                    .at(3));
            label(
                "EU",
                CORE_X + 6,
                HEIGHT - 10,
                style.ramp("power")
                    .at(3));
            paintCore();
            collectRing(style);
        }

        private void put(int x, int y, int color) {
            if (chamfer(x, y, BOARD_CUT)) pixels[y * WIDTH + x] = color;
        }

        private int get(int x, int y) {
            return x >= 0 && y >= 0 && x < WIDTH && y < HEIGHT ? pixels[y * WIDTH + x] : 0;
        }

        private void paintBoard(Style style) {
            int cut = BOARD_CUT;
            for (int x = 0; x < WIDTH; x++) {
                for (int y = 0; y < HEIGHT; y++) {
                    if (!chamfer(x, y, cut)) continue;
                    boolean border = !(chamfer(x + 1, y, cut) && chamfer(x - 1, y, cut)
                        && chamfer(x, y + 1, cut)
                        && chamfer(x, y - 1, cut));
                    boolean lit = !(chamfer(x - 1, y, cut) && chamfer(x, y - 1, cut)
                        && chamfer(x - 2, y, cut)
                        && chamfer(x, y - 2, cut));
                    put(x, y, border ? style.frame() : lit ? style.highlight() : style.background());
                }
            }
            if (style.scan() != -1) {
                for (int y = 3; y < HEIGHT - 3; y++) {
                    if (y % 2 != 0) continue;
                    for (int x = 3; x < WIDTH - 3; x++) put(x, y, style.scan());
                }
            }
            for (int y = 4; y < HEIGHT - 4; y += 3) {
                for (int x = 4 + (y / 3 % 2 == 1 ? 2 : 0); x < WIDTH - 4; x += 4) put(x, y, style.dots());
            }
            for (int[] path : DECO) {
                for (int packed : pathPixels(path))
                    put(TreeDiagramBoard.unpackX(packed), TreeDiagramBoard.unpackY(packed), style.deco());
                int last = path.length - 2;
                for (int end : new int[] { 0, last }) {
                    for (int i = -1; i <= 1; i++)
                        for (int j = -1; j <= 1; j++) put(path[end] + i, path[end + 1] + j, style.deco());
                }
            }
            label("TREE DIAGRAM", GRID_X + 6, BOTTOM + 5, style.label());
        }

        private static boolean chamfer(int x, int y, int cut) {
            int dx = Math.min(x, WIDTH - 1 - x);
            int dy = Math.min(y, HEIGHT - 1 - y);
            return dx >= 0 && dy >= 0 && dx + dy >= cut;
        }

        private void paintRows(Style style, String net, int top, int[] ports, boolean down, int used) {
            int left = CORE_X - ORBIT;
            int[] lanes = { CORE_X - 29, CORE_X - 31, CORE_X - 33, CORE_X - 35 };
            int source = GRID_X + COLUMNS * SLOT_PITCH + 3;
            for (int rank = 0; rank < ROWS; rank++) {
                int row = down ? rank : ROWS - 1 - rank;
                boolean live = row < used;
                Ramp ramp = live ? style.ramp(net)
                    : style.ramp(net)
                        .faded(style.background());
                int y = top + row * SLOT_PITCH + SLOT_SIZE / 2;
                for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) put(source + i, y + j, ramp.at(2));
                put(source, y, live ? 0xFFFFFFFF : ramp.at(3));
                Fibre fibre = fibre(
                    new int[] { source + 2, y, lanes[rank], y, lanes[rank], ports[row], left - 1, ports[row] },
                    ramp,
                    rank * 0.23f);
                if (style.dark() && live) glow(fibre);
                paint(fibre);
                if (live) fibres.add(fibre);
            }
        }

        private Fibre halo(Style style, int[] points, Ramp ramp, float offset) {
            Fibre fibre = fibre(points, ramp, offset);
            if (style.dark()) glow(fibre);
            return fibre;
        }

        private Fibre fibre(int[] points, Ramp ramp, float offset) {
            IntArrayList pixels = new IntArrayList();
            for (int packed : pathPixels(bend(points))) {
                if (chamfer(TreeDiagramBoard.unpackX(packed), TreeDiagramBoard.unpackY(packed), BOARD_CUT)) {
                    pixels.add(packed);
                }
            }
            return new Fibre(pixels.toIntArray(), ramp, offset);
        }

        private void glow(Fibre fibre) {
            int base = fibre.ramp.at(1);
            int glow = 0xFF000000 | ((base >> 16 & 255) * 2 / 5) << 16
                | ((base >> 8 & 255) * 2 / 5) << 8
                | (base & 255) * 2 / 5;
            IntSet own = new IntOpenHashSet(fibre.pixels);
            for (int packed : fibre.pixels) {
                int x = TreeDiagramBoard.unpackX(packed), y = TreeDiagramBoard.unpackY(packed);
                int[][] around = { { x + 1, y }, { x - 1, y }, { x, y + 1 }, { x, y - 1 } };
                for (int[] q : around) if (!own.contains(TreeDiagramBoard.pack(q[0], q[1]))) put(q[0], q[1], glow);
            }
        }

        private void paint(Fibre fibre) {
            int n = fibre.pixels.length;
            for (int k = 0; k < n; k++) {
                double u = 3.0 * k / Math.max(1, n - 1);
                int i = Math.min(2, (int) u);
                put(
                    TreeDiagramBoard.unpackX(fibre.pixels[k]),
                    TreeDiagramBoard.unpackY(fibre.pixels[k]),
                    mix(fibre.ramp.at(i), fibre.ramp.at(i + 1), u - i));
            }
        }

        private void paintOrbit(Style style) {
            for (int packed : circle(CORE_X, CORE_Y, ORBIT)) {
                int x = TreeDiagramBoard.unpackX(packed), y = TreeDiagramBoard.unpackY(packed);
                double a = angle(x, y);
                boolean gap = false;
                for (int[] range : ORBIT_GAPS) gap |= range[0] <= a && a <= range[1];
                if (!gap) put(x, y, style.ringAccent());
            }
            for (int k = 0; k < 36; k += 3) {
                double a = Math.toRadians(k * 10 + 5);
                put(
                    (int) Math.rint(CORE_X - 0.5 + 24 * Math.cos(a)),
                    (int) Math.rint(CORE_Y - 0.5 + 24 * Math.sin(a)),
                    style.ringAccentDim());
            }
            for (int packed : circle(CORE_X, CORE_Y, ORBIT + 3)) {
                int x = TreeDiagramBoard.unpackX(packed), y = TreeDiagramBoard.unpackY(packed);
                double a = angle(x, y);
                if ((a >= 200 && a <= 245 || a >= 20 && a <= 65) && (x + y) % 2 == 0) put(x, y, style.ringAccentDim());
            }
        }

        private void dock(int x, int y, Ramp ramp) {
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) put(x + i, y + j, ramp.at(3));
            put(x, y, 0xFFFFFFFF);
        }

        private void label(String text, int x, int y, int color) {
            for (char ch : text.toCharArray()) {
                String[] glyph = FONT.get(ch);
                if (glyph == null) {
                    x += 3;
                    continue;
                }
                for (int j = 0; j < glyph.length; j++) {
                    for (int i = 0; i < glyph[j].length(); i++) if (glyph[j].charAt(i) == '1') put(x + i, y + j, color);
                }
                x += glyph[0].length() + 1;
            }
        }

        private void paintCore() {
            for (int x = CORE_X - 30; x < CORE_X + 30; x++) {
                for (int y = CORE_Y - 30; y < CORE_Y + 30; y++) {
                    if (!disc(x, y)) continue;
                    if (discEdge(x, y)) put(x, y, CORE_EDGE);
                    else if (innerEdge(x, y)) put(x, y, CORE_INNER);
                    else put(x, y, CORE_FILL);
                }
            }
            for (int i = -11; i < 11; i++) {
                put(CORE_X + i, CORE_Y - 11, CORE_WELL);
                put(CORE_X + i, CORE_Y + 10, CORE_WELL);
                put(CORE_X - 11, CORE_Y + i, CORE_WELL);
                put(CORE_X + 10, CORE_Y + i, CORE_WELL);
            }
            for (int cell : CELLS) {
                int x = TreeDiagramBoard.unpackX(cell), y = TreeDiagramBoard.unpackY(cell);
                for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) put(x + i, y + j, CELL_DIM);
            }
        }

        private static boolean disc(int x, int y) {
            double dx = x - CORE_X + 0.5, dy = y - CORE_Y + 0.5;
            return dx * dx + dy * dy <= 21 * 21;
        }

        private static boolean discEdge(int x, int y) {
            return disc(x, y) && !(disc(x + 1, y) && disc(x - 1, y) && disc(x, y + 1) && disc(x, y - 1));
        }

        private static boolean inner(int x, int y) {
            return disc(x, y) && !discEdge(x, y)
                && disc(x - 2, y - 2)
                && disc(x - 2, y + 2)
                && disc(x + 2, y - 2)
                && disc(x + 2, y + 2);
        }

        private static boolean innerEdge(int x, int y) {
            return inner(x, y) && !(inner(x + 1, y) && inner(x - 1, y) && inner(x, y + 1) && inner(x, y - 1));
        }

        private void collectRing(Style style) {
            for (int packed : circle(CORE_X, CORE_Y, ORBIT)) {
                int x = TreeDiagramBoard.unpackX(packed), y = TreeDiagramBoard.unpackY(packed);
                if (get(x, y) != style.ringAccent()) continue;
                ring.add((int) Math.round(angle(x, y)));
                ring.add(x);
                ring.add(y);
            }
        }

        public void drawTexture(float x, float y) {
            if (texture == null) {
                texture = new DynamicTexture(WIDTH, HEIGHT);
                System.arraycopy(pixels, 0, texture.getTextureData(), 0, pixels.length);
                texture.updateDynamicTexture();
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getGlTextureId());
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + HEIGHT, 0, 0, 1);
            tessellator.addVertexWithUV(x + WIDTH, y + HEIGHT, 0, 1, 1);
            tessellator.addVertexWithUV(x + WIDTH, y, 0, 1, 0);
            tessellator.addVertexWithUV(x, y, 0, 0, 0);
            tessellator.draw();
        }
    }

    private static double angle(int x, int y) {
        double a = Math.toDegrees(Math.atan2(y - CORE_Y + 0.5, x - CORE_X + 0.5)) % 360;
        return a < 0 ? a + 360 : a;
    }

    public static int[] circle(int cx, int cy, int r) {
        IntSet points = new IntOpenHashSet();
        int x = r, y = 0, d = 1 - r;
        while (x >= y) {
            int[][] octants = { { x, y }, { y, x }, { -y, x }, { -x, y }, { -x, -y }, { -y, -x }, { y, -x },
                { x, -y } };
            for (int[] o : octants) points.add(TreeDiagramBoard.pack(cx + o[0], cy + o[1]));
            y++;
            if (d < 0) d += 2 * y + 1;
            else {
                x--;
                d += 2 * (y - x) + 1;
            }
        }
        return points.toIntArray();
    }

    public static int[] bend(int[] points) {
        IntArrayList out = new IntArrayList();
        out.add(points[0]);
        out.add(points[1]);
        int corners = points.length / 2 - 2;
        for (int i = 0; i < corners; i++) {
            int ax = points[i * 2], ay = points[i * 2 + 1];
            int bx = points[i * 2 + 2], by = points[i * 2 + 3];
            int cx = points[i * 2 + 4], cy = points[i * 2 + 5];
            int d1x = Integer.signum(bx - ax), d1y = Integer.signum(by - ay);
            int d2x = Integer.signum(cx - bx), d2y = Integer.signum(cy - by);
            if (d1x == d2x && d1y == d2y) continue;
            int l1 = Math.max(Math.abs(bx - ax), Math.abs(by - ay));
            int l2 = Math.max(Math.abs(cx - bx), Math.abs(cy - by));
            int k = Math.min(2, Math.min(i == 0 ? l1 : l1 / 2, i == corners - 1 ? l2 : l2 / 2));
            if (k <= 0) {
                out.add(bx);
                out.add(by);
                continue;
            }
            out.add(bx - d1x * k);
            out.add(by - d1y * k);
            out.add(bx + d2x * k);
            out.add(by + d2y * k);
        }
        out.add(points[points.length - 2]);
        out.add(points[points.length - 1]);
        return out.toIntArray();
    }

    public static int[] pathPixels(int[] points) {
        IntArrayList out = new IntArrayList();
        for (int i = 0; i + 3 < points.length; i += 2) {
            int x0 = points[i], y0 = points[i + 1], x1 = points[i + 2], y1 = points[i + 3];
            int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
            int sx = Integer.signum(x1 - x0), sy = Integer.signum(y1 - y0);
            int error = dx + dy;
            while (true) {
                int packed = TreeDiagramBoard.pack(x0, y0);
                if (out.isEmpty() || out.getInt(out.size() - 1) != packed) out.add(packed);
                if (x0 == x1 && y0 == y1) break;
                int twiceError = error * 2;
                if (twiceError >= dy) {
                    error += dy;
                    x0 += sx;
                }
                if (twiceError <= dx) {
                    error += dx;
                    y0 += sy;
                }
            }
        }
        return out.toIntArray();
    }

    private static int[] ringCells() {
        IntArrayList out = new IntArrayList();
        for (int[] ring : CORE_RINGS) {
            int r = ring[0], n = ring[1];
            for (int k = 0; k < n; k++) {
                double a = Math.toRadians(360.0 * k / n + (r % 2 == 1 ? 180.0 / n : 0));
                out.add(
                    TreeDiagramBoard.pack(
                        (int) Math.rint(CORE_X - 1 + r * Math.cos(a)),
                        (int) Math.rint(CORE_Y - 1 + r * Math.sin(a))));
            }
        }
        return out.toIntArray();
    }

    private static float[] glimmerPoints(long seed) {
        Random random = new Random(seed);
        float[] out = new float[(COLUMNS - 1) * (ROWS - 1) * 4];
        int i = 0;
        for (int c = 1; c < COLUMNS; c++) {
            for (int r = 1; r < ROWS; r++) {
                out[i++] = c;
                out[i++] = r;
                out[i++] = random.nextInt(GLIMMER_LOOP);
                out[i++] = 0.55f + 0.45f * random.nextFloat();
            }
        }
        return out;
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static Ramp ramp(int... rgb) {
        int[] stops = new int[rgb.length / 3];
        for (int i = 0; i < stops.length; i++) stops[i] = rgb(rgb[i * 3], rgb[i * 3 + 1], rgb[i * 3 + 2]);
        return new Ramp(stops);
    }

    public static int mix(int from, int to, double k) {
        k = Math.max(0, Math.min(1, k));
        int r = (int) ((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * k);
        int g = (int) ((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * k);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }
}
