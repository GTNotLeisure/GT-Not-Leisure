package com.science.gtnl.client.wireless;

import static com.science.gtnl.client.wireless.WirelessCardGui.ROW_HEIGHT;
import static net.minecraft.client.gui.Gui.drawRect;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WirelessCardTreeRows {

    public static final int TREE_X = 7, INDENT = 12, ARROW_X = 0, CHILD_ARROW_X = 12, ICON_X = 16, CHILD_ICON_X = 20,
        TEXT_GAP = 18, ARROW_SIZE = 16, ARROW_INSET = 1, ARROW_Y = 5, MAX_NEST_SHADE = 3, DARKEN_NEST_ALPHA = 0x33,
        LIGHTEN_NEST_ALPHA = 0x0F, SEPARATOR_ALPHA = 0x5D;

    private WirelessCardTreeRows() {}

    public static int lineX(int level) {
        return TREE_X + level * INDENT;
    }

    public static int arrowX(int depth) {
        return depth == 0 ? ARROW_X : CHILD_ARROW_X + (depth - 1) * INDENT;
    }

    public static int iconX(int depth, boolean group) {
        if (depth == 0) return ICON_X;
        return group ? arrowX(depth) + ARROW_SIZE : CHILD_ICON_X + (depth - 1) * INDENT;
    }

    static void drawBackground(WirelessCardGui gui, int origin, int left, int right, int y, int depth, boolean group,
        boolean lit, boolean separator) {
        if (lit) {
            int bottom = y + ROW_HEIGHT - 1;
            drawRect(left, y, right, bottom, gui.highlight);
            drawRect(left, y, right, y + 1, gui.highlightTop);
            drawRect(left, bottom - 1, right, bottom, gui.highlightShadow);
        } else if (depth > 0) drawRect(
            left,
            y,
            right,
            y + ROW_HEIGHT,
            Math.min(depth, MAX_NEST_SHADE) * gui.palette.nestAlpha << 24 | gui.palette.nest & 0xFFFFFF);
        if (!separator) return;
        int start = lit || depth == 0 ? left : origin + iconX(depth, group) - 2;
        drawRect(start, y + ROW_HEIGHT - 1, right, y + ROW_HEIGHT, lit ? gui.palette.line : gui.tint(SEPARATOR_ALPHA));
    }

    static void drawTree(WirelessCardGui gui, int origin, int y, int depth, boolean group, int lines, boolean last,
        boolean lit) {
        if (depth == 0) return;
        int color = 0xFF000000 | (lit ? gui.highlightTree : gui.palette.tree);
        for (int level = 0; level < depth - 1; level++) if ((lines & 1 << level) != 0)
            drawRect(origin + lineX(level), y, origin + lineX(level) + 1, y + ROW_HEIGHT, color);
        int x = origin + lineX(depth - 1), middle = y + ROW_HEIGHT / 2;
        drawRect(x, y, x + 1, last ? middle + 1 : y + ROW_HEIGHT, color);
        drawRect(
            x + 1,
            middle,
            origin + (group ? arrowX(depth) + ARROW_INSET : iconX(depth, false) - 2),
            middle + 1,
            color);
    }
}
