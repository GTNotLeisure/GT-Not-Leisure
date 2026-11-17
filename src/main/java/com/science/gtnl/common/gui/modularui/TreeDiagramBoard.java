package com.science.gtnl.common.gui.modularui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.utils.Platform;
import com.github.bsideup.jabel.Desugar;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class TreeDiagramBoard {

    public enum Net {
        CU,
        NANITE,
        FLUID,
        POWER,
        UNLOCK,
        GRAY,
        WORK
    }

    @Desugar
    public record Wire(int dim, int bright, int hi) {}

    @Desugar
    public record Palette(int panel, int panelHi, int panelLo, int outline, int screen, int screenEdge,
        int screenEdgeHi, int trackDark, List<Wire> wires, int orbit, int orbitFar, int text, int muted, int dim,
        int ok, int warn, int bad, int gold, int slot, int slotLip, int button, int buttonOn, int barBack,
        int separator, int cross) {

        public Wire wire(Net net) {
            return wires.get(net.ordinal());
        }
    }

    public static final Palette DARK = new Palette(
        0xFF171A20,
        0xFF363C47,
        0xFF0B0C10,
        0xFF050608,
        0xFF0C0E12,
        0xFF2A2F39,
        0xFF404857,
        0xFF050608,
        List.of(
            new Wire(0xFF1B4A57, 0xFF3FC8E8, 0xFFC8F6FF),
            new Wire(0xFF5A4718, 0xFFE8B13F, 0xFFFFF0C0),
            new Wire(0xFF1E5233, 0xFF55D184, 0xFFD0FFE0),
            new Wire(0xFF5A2418, 0xFFEE6A40, 0xFFFFD8C8),
            new Wire(0xFF3C2A5E, 0xFFA77BF0, 0xFFEADCFF),
            new Wire(0xFF2C313B, 0xFF6B7385, 0xFFC0C6D2),
            new Wire(0xFF2E3A4A, 0xFFDFE8F5, 0xFFFFFFFF)),
        0xFF4A6FB0,
        0xFF263552,
        0xFFE6E9EF,
        0xFF8A919E,
        0xFF505661,
        0xFF6EE07A,
        0xFFFFB347,
        0xFFFF6B6B,
        0xFFFFC94A,
        0xFF07080B,
        0xFF181C24,
        0xFF1E222B,
        0xFF241B38,
        0xFF12161D,
        0xFF1A1E27,
        0xFFFFFFFF);

    public static final Palette LIGHT = new Palette(
        0xFFD6DBE2,
        0xFFF6F8FB,
        0xFF9CA4B0,
        0xFF5C6470,
        0xFFECEFF3,
        0xFFAAB3BF,
        0xFF86909E,
        0xFFC1C7D0,
        List.of(
            new Wire(0xFFAACCDC, 0xFF1C90C6, 0xFFCEF2FF),
            new Wire(0xFFDECCA2, 0xFFD29212, 0xFFFFE8A0),
            new Wire(0xFFAAD2BA, 0xFF269E5E, 0xFFC4F8DA),
            new Wire(0xFFE4C0AC, 0xFFE06026, 0xFFFFD4BC),
            new Wire(0xFFCABEEA, 0xFF7A54D6, 0xFFE6DAFF),
            new Wire(0xFFC6CCD6, 0xFF8A92A0, 0xFFF0F2F6),
            new Wire(0xFFC4CEDC, 0xFF3A5070, 0xFF96ACCC)),
        0xFF5680C4,
        0xFFACBCD6,
        0xFF1E242E,
        0xFF646E7C,
        0xFF9EA6B2,
        0xFF1C8C48,
        0xFFCE7012,
        0xFFCC3030,
        0xFFC4880A,
        0xFFF2F4F7,
        0xFFFCFDFE,
        0xFFE4E8EE,
        0xFFEEE8FC,
        0xFFE6EAEF,
        0xFFCED4DC,
        0xFF464E5A);

    public static Palette palette() {
        return TreeDiagramTheme.dark() ? DARK : LIGHT;
    }

    public static class Trace {

        public final int[] pixels;
        public final int[] rim;

        public Trace(int... points) {
            IntArrayList path = new IntArrayList();
            for (int i = 0; i + 3 < points.length; i += 2) {
                int x0 = points[i], y0 = points[i + 1], x1 = points[i + 2], y1 = points[i + 3];
                int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
                int sx = Integer.signum(x1 - x0), sy = Integer.signum(y1 - y0);
                for (int k = 0; k <= steps; k++) {
                    int packed = pack(x0 + sx * k, y0 + sy * k);
                    if (path.isEmpty() || path.getInt(path.size() - 1) != packed) path.add(packed);
                }
            }
            pixels = path.toIntArray();
            IntSet own = new IntOpenHashSet(pixels);
            IntSet ring = new IntOpenHashSet();
            for (int packed : pixels) {
                int x = unpackX(packed), y = unpackY(packed);
                for (int[] d : NEIGHBOURS) {
                    int q = pack(x + d[0], y + d[1]);
                    if (!own.contains(q)) ring.add(q);
                }
            }
            rim = ring.toIntArray();
        }
    }

    private static final int[][] NEIGHBOURS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

    public static int pack(int x, int y) {
        return (x + 1024) << 11 | y + 1024;
    }

    public static int unpackX(int packed) {
        return (packed >> 11) - 1024;
    }

    public static int unpackY(int packed) {
        return (packed & 2047) - 1024;
    }

    public static class Orbit {

        public final int[] near;
        public final int[] far;

        public Orbit(int cx, int cy, int rx, int ry) {
            IntArrayList front = new IntArrayList();
            IntArrayList back = new IntArrayList();
            IntSet seen = new IntOpenHashSet();
            int steps = (int) (2 * Math.PI * Math.max(rx, ry) * 2);
            int index = 0;
            for (int k = 0; k < steps; k++) {
                double a = 2 * Math.PI * k / steps;
                int packed = pack((int) Math.round(cx + rx * Math.cos(a)), (int) Math.round(cy + ry * Math.sin(a)));
                if (!seen.add(packed)) continue;
                if (index++ % 2 == 0) (Math.sin(a) >= 0 ? front : back).add(packed);
            }
            near = front.toIntArray();
            far = back.toIntArray();
        }
    }

    public final Palette palette;
    private final float originX, originY;
    private final FloatArrayList boxes = new FloatArrayList();
    private final IntArrayList colors = new IntArrayList();
    private final List<Runnable> overlays = new ObjectArrayList<>();

    public TreeDiagramBoard(float originX, float originY) {
        this(palette(), originX, originY);
    }

    public TreeDiagramBoard(Palette palette, float originX, float originY) {
        this.palette = palette;
        this.originX = originX;
        this.originY = originY;
    }

    public float left() {
        return originX;
    }

    public float top() {
        return originY;
    }

    public void rect(float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0) return;
        boxes.add(originX + x);
        boxes.add(originY + y);
        boxes.add(originX + x + w);
        boxes.add(originY + y + h);
        colors.add(color);
    }

    public void flush() {
        if (!colors.isEmpty()) {
            Platform.setupDrawColor();
            Platform.startDrawing(Platform.DrawMode.QUADS, Platform.VertexFormat.POS_COLOR, buffer -> {
                for (int i = 0; i < colors.size(); i++) {
                    GuiDraw.drawRectRaw(
                        buffer,
                        boxes.getFloat(i * 4),
                        boxes.getFloat(i * 4 + 1),
                        boxes.getFloat(i * 4 + 2),
                        boxes.getFloat(i * 4 + 3),
                        colors.getInt(i));
                }
            });
            Platform.endDrawColor();
        }
        boxes.clear();
        colors.clear();
        for (Runnable overlay : overlays) overlay.run();
        overlays.clear();
    }

    public void later(Runnable overlay) {
        overlays.add(overlay);
    }

    public void panel(int x, int y, int w, int h) {
        rect(x, y, w, h, palette.outline);
        rect(x + 1, y + 1, w - 2, h - 2, palette.panelLo);
        rect(x + 1, y + 1, w - 3, h - 3, palette.panelHi);
        rect(x + 2, y + 2, w - 4, h - 4, palette.panel);
    }

    public void screen(int x, int y, int w, int h) {
        int cut = 3;
        for (int j = 0; j < h; j++) {
            int inset = j < cut ? cut - j : j >= h - cut ? j - (h - cut) + 1 : 0;
            rect(x + inset, y + j, w - inset, 1, palette.screenEdge);
            if (j > 0 && j < h - 1) rect(x + inset + 1, y + j, w - inset - 2, 1, palette.screen);
        }
        rect(x + cut + 1, y, w - cut - 3, 1, palette.screenEdgeHi);
    }

    public void trace(Trace trace, Net net, boolean active, float... packets) {
        Wire wire = palette.wire(net);
        for (int packed : trace.rim) rect(unpackX(packed), unpackY(packed), 1, 1, palette.trackDark);
        int color = active ? wire.bright() : wire.dim();
        for (int packed : trace.pixels) rect(unpackX(packed), unpackY(packed), 1, 1, color);
        int n = trace.pixels.length;
        if (n < 2) return;
        for (float packet : packets) {
            int i = Math.min(n - 2, (int) (n * (packet - Math.floor(packet))));
            rect(unpackX(trace.pixels[i]), unpackY(trace.pixels[i]), 1, 1, wire.hi());
            rect(unpackX(trace.pixels[i + 1]), unpackY(trace.pixels[i + 1]), 1, 1, wire.hi());
        }
    }

    public void pad(int x, int y, Net net, boolean lit) {
        Wire wire = palette.wire(net);
        rect(x - 2, y - 2, 5, 5, palette.trackDark);
        rect(x - 1, y - 1, 3, 3, lit ? wire.bright() : wire.dim());
        rect(x, y, 1, 1, palette.trackDark);
    }

    public void orbit(Orbit orbit) {
        for (int packed : orbit.near) rect(unpackX(packed), unpackY(packed), 1, 1, palette.orbit);
        for (int packed : orbit.far) rect(unpackX(packed), unpackY(packed), 1, 1, palette.orbitFar);
    }

    public void litBar(int x, int y, int w, double value, Net net, double pending, Net pendingNet) {
        Wire wire = palette.wire(net);
        rect(x, y, w, 4, palette.trackDark);
        rect(x + 1, y + 1, w - 2, 2, wire.dim());
        int filled = (int) Math.round((w - 2) * Math.min(1, Math.max(0, value)));
        if (filled > 0) {
            rect(x + 1, y + 1, filled, 2, wire.bright());
            rect(x + 1, y + 1, filled, 1, wire.hi());
            rect(x + filled, y + 1, 1, 2, wire.hi());
        }
        int extra = (int) Math.round((w - 2) * Math.min(1 - Math.min(1, value), Math.max(0, pending)));
        for (int i = 0; i < extra; i++) {
            if (i % 3 != 2) rect(
                x + 1 + filled + i,
                y + 1,
                1,
                2,
                palette.wire(pendingNet)
                    .bright());
        }
        pad(x + w + 1, y + 2, net, value >= 1);
    }

    public void thickBar(int x, int y, int w, int h, double value, Net net, double... marks) {
        Wire wire = palette.wire(net);
        rect(x, y, w, h, palette.trackDark);
        rect(x + 1, y + 1, w - 2, h - 2, palette.barBack);
        int filled = (int) Math.round((w - 2) * Math.min(1, Math.max(0, value)));
        if (filled > 0) {
            rect(x + 1, y + 1, filled, h - 2, wire.dim());
            rect(x + 1, y + 1 + (h - 2) / 2, filled, 1, wire.bright());
            rect(x + filled, y + 1, 1, h - 2, wire.hi());
        }
        for (double mark : marks) rect(x + (int) Math.round((w - 1) * mark), y - 2, 1, h + 4, palette.muted);
    }

    public void slot(int x, int y, int size, int ring) {
        rect(x, y, size, size, ring == 0 ? palette.screenEdge : ring);
        rect(x + 1, y + 1, size - 2, size - 2, palette.slot);
        rect(x + 1, y + size - 2, size - 2, 1, palette.slotLip);
    }

    public void field(int x, int y, int w, int h) {
        rect(x, y, w, h, palette.screenEdge);
        rect(x + 1, y + 1, w - 2, h - 2, palette.slot);
    }

    public void button(int x, int y, int w, int h, Net net, boolean selected) {
        Wire wire = palette.wire(net);
        rect(x, y, w, h, selected ? wire.bright() : palette.screenEdge);
        rect(x + 1, y + 1, w - 2, h - 2, selected ? palette.buttonOn : palette.button);
        rect(x + 1, y + h - 2, w - 2, 1, selected ? wire.bright() : wire.dim());
    }

    public void closeBox(int x, int y) {
        rect(x, y, 11, 11, palette.screenEdge);
        rect(x + 1, y + 1, 9, 9, palette.screen);
        for (int i = 0; i < 5; i++) {
            rect(x + 3 + i, y + 3 + i, 1, 1, palette.cross);
            rect(x + 7 - i, y + 3 + i, 1, 1, palette.cross);
        }
    }

    public void text(String text, int x, int y, int color) {
        float left = originX + x;
        float top = originY + y;
        later(() -> GuiDraw.drawText(text, left, top, 1, color, false));
    }

    public void textRight(String text, int right, int y, int color) {
        text(text, right - width(text), y, color);
    }

    public void textCenter(String text, int center, int y, int color) {
        text(text, center - width(text) / 2, y, color);
    }

    public void textFit(String text, int x, int y, int maxWidth, int color, boolean alignRight) {
        int width = width(text);
        float scale = width <= maxWidth ? 1 : maxWidth / (float) width;
        float left = originX + (alignRight ? x - width * scale : x);
        float top = originY + y + (1 - scale) * 4;
        later(() -> GuiDraw.drawText(text, left, top, scale, color, false));
    }

    public int runs(int x, int y, Object... parts) {
        for (int i = 0; i + 1 < parts.length; i += 2) {
            String part = String.valueOf(parts[i]);
            text(part, x, y, (Integer) parts[i + 1]);
            x += width(part);
        }
        return x;
    }

    public int runsRight(int right, int y, Object... parts) {
        int width = 0;
        for (int i = 0; i + 1 < parts.length; i += 2) width += width(String.valueOf(parts[i]));
        return runs(right - width, y, parts);
    }

    public static int width(String text) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        return font.getStringWidth(text);
    }

    public static IDrawable slotBackground(int ringColor) {
        return (context, x, y, w, h, theme) -> {
            TreeDiagramBoard board = new TreeDiagramBoard(x, y);
            board.slot(0, 0, w, ringColor);
            board.flush();
        };
    }

    public static IDrawable fieldBackground() {
        return (context, x, y, w, h, theme) -> {
            TreeDiagramBoard board = new TreeDiagramBoard(x, y);
            board.field(0, 0, w, h);
            board.flush();
        };
    }

    public static IDrawable closeBackground() {
        return (context, x, y, w, h, theme) -> {
            TreeDiagramBoard board = new TreeDiagramBoard(x, y);
            board.closeBox(0, 0);
            board.flush();
        };
    }
}
