package com.science.gtnl.client.projector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.GuiTextures;
import com.gtnewhorizon.gtnhlib.eventbus.EventBusSubscriber;
import com.gtnllib.gui.theme.ae2.AE2Look;
import com.gtnllib.gui.theme.ae2.AE2Variant;
import com.gtnllib.gui.theme.ae2.PlainLook;
import com.science.gtnl.client.structureanimation.StructureAnimationTask;
import com.science.gtnl.client.structureanimation.StructureAnimations;
import com.science.gtnl.common.gui.ScreenThemes;
import com.science.gtnl.common.projector.ProjectorBuilder;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
@EventBusSubscriber(side = Side.CLIENT)
public class ProjectorBuildHud {

    public static final int WIDTH = 182, PADDING = 6, BAR_HEIGHT = 8, GAP = 3, ABOVE_HOTBAR = 62;

    @SubscribeEvent
    public static void overlay(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null || minecraft.gameSettings.hideGUI || minecraft.gameSettings.showDebugInfo)
            return;
        StructureAnimationTask task = StructureAnimations.ownTask();
        if (task == null) return;
        draw(minecraft, event.resolution, task);
    }

    private static void draw(Minecraft minecraft, ScaledResolution resolution, StructureAnimationTask task) {
        FontRenderer font = minecraft.fontRenderer;
        AE2Look look = AE2Look.ofTheme(ScreenThemes.projector());
        AE2Variant variant = look.variant();
        int height = PADDING * 2 + font.FONT_HEIGHT + GAP + BAR_HEIGHT;
        int left = (resolution.getScaledWidth() - WIDTH) / 2;
        int top = resolution.getScaledHeight() - ABOVE_HOTBAR - height;
        int inner = WIDTH - PADDING * 2;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glColor4f(1, 1, 1, 1);
            if (variant != null) variant.panel.draw(left, top, WIDTH, height);
            else GuiTextures.MC_BACKGROUND.draw(left, top, WIDTH, height);
            String label = label(task);
            font.drawString(
                label,
                left + (WIDTH - font.getStringWidth(label)) / 2,
                top + PADDING,
                look.panelText(true),
                false);
            int barTop = top + PADDING + font.FONT_HEIGHT + GAP;
            GL11.glColor4f(1, 1, 1, 1);
            look.drawWell(left + PADDING, barTop, inner, BAR_HEIGHT);
            int filled = (int) Math.round(fraction(task) * (inner - 2));
            if (filled > 0) GuiDraw.drawRect(
                left + PADDING + 1,
                barTop + 1,
                filled,
                BAR_HEIGHT - 2,
                variant != null ? variant.highlight : PlainLook.GOOD);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static double fraction(StructureAnimationTask task) {
        return task.buildProgress();
    }

    private static String label(StructureAnimationTask task) {
        return StatCollector.translateToLocalFormatted(
            ProjectorBuilder.LANG + (task.reversed ? "dismantle_progress" : "building"),
            Math.round(task.buildProgress() * 100));
    }
}
