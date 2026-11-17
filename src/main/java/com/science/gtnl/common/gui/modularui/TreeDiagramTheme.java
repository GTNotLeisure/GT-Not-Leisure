package com.science.gtnl.common.gui.modularui;

import com.gtnewhorizons.angelica.client.font.DarkModeUtils;
import com.gtnllib.mod.ModList;
import com.science.gtnl.ScienceNotLeisure;

import cpw.mods.fml.common.Optional;

public class TreeDiagramTheme {

    private static final long NO_RECOLOR = -1L;
    private static boolean unsupportedAngelica;

    private TreeDiagramTheme() {}

    public static boolean dark() {
        return recolor(0xFF000000) != NO_RECOLOR;
    }

    public static int textColor(int color) {
        long recolored = recolor(color);
        return recolored == NO_RECOLOR ? color : (int) recolored;
    }

    private static long recolor(int color) {
        if (unsupportedAngelica || !ModList.Angelica.isModLoaded()) return NO_RECOLOR;
        try {
            return angelicaColor(color);
        } catch (LinkageError failure) {
            unsupportedAngelica = true;
            ScienceNotLeisure.LOG.warn("Angelica dark-mode API is unavailable; using the light theme", failure);
            return NO_RECOLOR;
        }
    }

    @Optional.Method(modid = "angelica")
    private static long angelicaColor(int color) {
        long recolored = DarkModeUtils.computeGuiFontRecolor(color);
        return recolored == DarkModeUtils.NO_RECOLOR ? NO_RECOLOR
            : Integer.toUnsignedLong(DarkModeUtils.unpackColor(recolored));
    }
}
