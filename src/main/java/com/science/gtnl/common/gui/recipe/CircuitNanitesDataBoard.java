package com.science.gtnl.common.gui.recipe;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.science.gtnl.common.circuit.CircuitTarget;
import com.science.gtnl.common.circuit.nanite.CircuitNanitePolicy;
import com.science.gtnl.common.circuit.nanite.CircuitNaniteRegistry;
import com.science.gtnl.common.circuit.nanite.NaniteEffects;
import com.science.gtnl.common.circuit.nanite.NaniteMaintenancePolicy;
import com.science.gtnl.common.circuit.nanite.NaniteMaintenanceRequirement;
import com.science.gtnl.common.circuit.nanite.NaniteParticipation;
import com.science.gtnl.common.circuit.nanite.NaniteProfile;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard;
import com.science.gtnl.common.recipe.gtnl.CircuitNanitesDataRecipes;

import gregtech.api.enums.GTValues;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;

public class CircuitNanitesDataBoard implements IDrawable {

    public static final int WIDTH = 168, HEADER = 22, ROWS = 7, ROW = 11, STRIP_HEIGHT = ROWS * ROW + 6,
        HEIGHT = HEADER + STRIP_HEIGHT;
    public static final int GAUGE_X = 41, GAUGE_PIXELS = 164;
    public static final int[] TIER_COLORS = { rgb(64, 120, 210), rgb(80, 170, 235), rgb(120, 215, 235),
        rgb(205, 235, 235), rgb(245, 225, 120), rgb(245, 180, 70), rgb(240, 130, 50), rgb(225, 80, 50),
        rgb(180, 50, 70), rgb(160, 80, 220) };
    public static final int[] FILLS = { rgb(212, 154, 34), rgb(47, 164, 200), rgb(224, 106, 48), rgb(204, 64, 64),
        rgb(176, 80, 192) };
    public static final int[] FLUID_FILLS = { rgb(62, 168, 102), rgb(40, 130, 170), rgb(120, 130, 140) };
    public static final int LABEL = 0xFF555555, NAME = 0xFF202020, NOTE = 0xFF6A6A6A, BADGE_TEXT = 0xFF06080B;
    public static final int MIN_PARALLEL = CircuitNaniteRegistry.PARALLELS[0],
        MAX_PARALLEL = CircuitNaniteRegistry.PARALLELS[CircuitNaniteRegistry.PARALLELS.length - 1];
    public static final double MAX_SAVING = 0.40;

    private static NaniteMaintenancePolicy maintenance;
    private static long[] tierFluids;

    private final Supplier<GTNEIDefaultHandler.CachedDefaultRecipe> recipe;

    public CircuitNanitesDataBoard(Supplier<GTNEIDefaultHandler.CachedDefaultRecipe> recipe) {
        this.recipe = recipe;
    }

    @Desugar
    public record Sheet(String name, NaniteProfile profile, double failure, int[] support, List<String> fluidNames,
        long[] fluids, long fluidTotal, int highestTier) {}

    public static Sheet sheet(GTRecipe recipe) {
        if (recipe == null || recipe.mInputs == null || recipe.mInputs.length == 0 || recipe.mInputs[0] == null)
            return null;
        ItemStack stack = recipe.mInputs[0];
        CircuitNaniteRegistry registry = CircuitNanitesDataRecipes.loadedRegistry;
        NaniteProfile profile = registry == null ? null : registry.profile(stack);
        String name = stack.getDisplayName();
        if (profile == null) return new Sheet(name, null, 0, new int[3], List.of(), new long[3], 0, -1);
        CircuitNanitePolicy policy = new CircuitNanitePolicy();
        NaniteEffects support = policy
            .select(new NaniteProfile[] { profile, profile, profile, profile }, 1, Integer.MAX_VALUE);
        int[] contributions = new int[3];
        for (NaniteParticipation participant : support.participants()) {
            if (participant.slot() > 0) contributions[participant.slot() - 1] = participant.parallelContribution();
        }
        NaniteEffects alone = policy.select(new NaniteProfile[] { profile, null, null, null }, 1, Integer.MAX_VALUE);
        List<String> fluidNames = maintenance().fluids();
        long[] fluids = new long[fluidNames.size()];
        long total = 0;
        for (NaniteMaintenanceRequirement requirement : maintenance().requirements(alone)) {
            int index = fluidNames.indexOf(requirement.fluidName());
            if (index >= 0) fluids[index] = requirement.milliBucketsPerSecond();
            total += requirement.milliBucketsPerSecond();
        }
        return new Sheet(
            name,
            profile,
            alone.lossChance(),
            contributions,
            fluidNames,
            fluids,
            total,
            CircuitTarget.highestCircuitTier(profile.tier()));
    }

    public static NaniteMaintenancePolicy maintenance() {
        if (maintenance == null) maintenance = new NaniteMaintenancePolicy();
        return maintenance;
    }

    public static long tierFluids(int tier) {
        if (tierFluids == null) {
            long[] totals = new long[CircuitNaniteRegistry.PARALLELS.length];
            for (int k = 1; k <= totals.length; k++) {
                NaniteEffects effects = new NaniteEffects(null, List.of(new NaniteParticipation(0, k, 1)), 1, 0);
                for (NaniteMaintenanceRequirement requirement : maintenance().requirements(effects))
                    totals[k - 1] += requirement.milliBucketsPerSecond();
            }
            tierFluids = totals;
        }
        return tierFluids[tier - 1];
    }

    public static double speedScale(double duration) {
        double fastest = CircuitNaniteRegistry.DURATION[CircuitNaniteRegistry.DURATION.length - 1]
            * (1 - CircuitNaniteRegistry.ROLL_SPREAD);
        return (1 / duration - 1) / (1 / fastest - 1);
    }

    public static double powerScale(double energy) {
        return (1 - energy) / MAX_SAVING;
    }

    public static double logScale(double value, double low, double high) {
        return value <= low ? 0 : Math.log(value / low) / Math.log(high / low);
    }

    public static double parallelScale(int parallel) {
        return logScale(parallel, MIN_PARALLEL, MAX_PARALLEL);
    }

    public static double fluidScale(long amount) {
        return logScale(amount, tierFluids(1), tierFluids(CircuitNaniteRegistry.PARALLELS.length));
    }

    @Override
    public void draw(float x, float y, float width, float height, float partialTicks) {
        GTNEIDefaultHandler.CachedDefaultRecipe cached = recipe.get();
        Sheet sheet = cached == null ? null : sheet(cached.mRecipe);
        if (sheet == null) return;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        TreeDiagramBoard quads = new TreeDiagramBoard(x, y);
        quads.rect(0, HEADER, WIDTH, STRIP_HEIGHT, 0xFF8B8B8B);
        quads.rect(1, HEADER + 1, WIDTH - 1, STRIP_HEIGHT - 1, 0xFFFFFFFF);
        quads.rect(1, HEADER + 1, WIDTH - 2, STRIP_HEIGHT - 2, 0xFFDADADA);
        NaniteProfile profile = sheet.profile();
        if (profile != null) {
            quads.rect(148, 2, 18, 10, TIER_COLORS[profile.tier() - 1]);
            drawGauges(quads, sheet);
        }
        quads.flush();
        font.drawString(sheet.name(), (int) x + 23, (int) y + 2, NAME);
        if (profile == null) {
            String unrated = tr("unrated");
            font.drawString(unrated, (int) x + 23, (int) y + 12, NOTE);
            font.drawString(
                unrated,
                (int) x + (WIDTH - font.getStringWidth(unrated)) / 2,
                (int) y + HEADER + STRIP_HEIGHT / 2 - 4,
                NOTE);
            return;
        }
        font.drawString(
            tr("craftable_range", GTValues.VN[0], GTValues.VN[sheet.highestTier()]),
            (int) x + 23,
            (int) y + 12,
            NOTE);
        String badge = "T" + profile.tier();
        font.drawString(badge, (int) x + 157 - font.getStringWidth(badge) / 2, (int) y + 3, BADGE_TEXT);
        String[] values = values(sheet);
        int[] colors = valueColors(sheet);
        for (int row = 0; row < ROWS; row++) {
            int rowY = (int) y + HEADER + 3 + row * ROW;
            drawFitted(font, tr("row." + ROW_KEYS[row]), x + 4, rowY, GAUGE_X - 5, LABEL);
            font.drawString(values[row], (int) x + 165 - font.getStringWidth(values[row]), rowY, colors[row]);
        }
    }

    private static void drawFitted(FontRenderer font, String text, float x, float y, int maxWidth, int color) {
        int width = font.getStringWidth(text);
        if (width <= maxWidth) {
            font.drawString(text, (int) x, (int) y, color);
            return;
        }
        float scale = maxWidth / (float) width;
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y + (1 - scale) * 4, 0);
        GL11.glScalef(scale, scale, 1);
        font.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    public static final String[] ROW_KEYS = { "parallel", "speed", "power", "failure", "bonus", "maintenance",
        "craftable" };

    public static String[] values(Sheet sheet) {
        NaniteProfile profile = sheet.profile();
        return new String[] { String.valueOf(profile.parallel()), "×" + format(1 / profile.durationMultiplier(), 2),
            "×" + format(profile.euMultiplier(), 3), format(sheet.failure() * 100, 0) + "%",
            format(profile.bonusChance() * 100, 0) + "%", String.valueOf(sheet.fluidTotal()),
            GTValues.VN[sheet.highestTier()] };
    }

    private static int[] valueColors(Sheet sheet) {
        return new int[] { 0xFF9A6400, 0xFF136B86, 0xFFB0501A, sheet.failure() > 0 ? 0xFFB02828 : 0xFF2E7D4A,
            sheet.profile()
                .bonusChance() > 0 ? 0xFF8A3A9A : 0xFF8B8B8B,
            0xFF2E7D4A, 0xFF303030 };
    }

    private static void drawGauges(TreeDiagramBoard quads, Sheet sheet) {
        NaniteProfile profile = sheet.profile();
        int tiers = CircuitNaniteRegistry.PARALLELS.length;
        double[] parallelTicks = new double[tiers], speedTicks = new double[tiers], powerTicks = new double[tiers],
            failureTicks = new double[tiers], fluidTicks = new double[tiers];
        for (int k = 0; k < tiers; k++) {
            parallelTicks[k] = parallelScale(CircuitNaniteRegistry.PARALLELS[k]);
            speedTicks[k] = speedScale(CircuitNaniteRegistry.DURATION[k]);
            powerTicks[k] = powerScale(CircuitNaniteRegistry.ENERGY[k]);
            failureTicks[k] = (CircuitNaniteRegistry.GAMBLING_LOSS - NaniteMaintenancePolicy.lossCancellation(k + 1))
                / CircuitNaniteRegistry.GAMBLING_LOSS;
            fluidTicks[k] = fluidScale(tierFluids(k + 1));
        }
        int row = 0;
        float top = gaugeTop(row++);
        track(quads, top);
        ticks(quads, top, parallelTicks);
        double parallel = parallelScale(profile.parallel());
        fill(quads, top, 0, Math.max(0.02, parallel), FILLS[0]);
        marker(quads, top, parallel);

        top = gaugeTop(row++);
        track(quads, top);
        ticks(quads, top, speedTicks);
        double speed = speedScale(profile.durationMultiplier());
        fill(quads, top, 0, speed, FILLS[1]);
        int index = profile.tier() - 1;
        band(
            quads,
            top,
            speedScale(CircuitNaniteRegistry.DURATION[index] * (1 + CircuitNaniteRegistry.ROLL_SPREAD)),
            speedScale(CircuitNaniteRegistry.DURATION[index] * (1 - CircuitNaniteRegistry.ROLL_SPREAD)),
            FILLS[1]);
        marker(quads, top, speed);

        top = gaugeTop(row++);
        track(quads, top);
        ticks(quads, top, powerTicks);
        double power = powerScale(profile.euMultiplier());
        fill(quads, top, 0, power, FILLS[2]);
        band(
            quads,
            top,
            powerScale(CircuitNaniteRegistry.ENERGY[index] * (1 + CircuitNaniteRegistry.ROLL_SPREAD)),
            powerScale(CircuitNaniteRegistry.ENERGY[index] * (1 - CircuitNaniteRegistry.ROLL_SPREAD)),
            FILLS[2]);
        marker(quads, top, power);

        top = gaugeTop(row++);
        track(quads, top);
        ticks(quads, top, failureTicks);
        double failure = sheet.failure() / CircuitNaniteRegistry.GAMBLING_LOSS;
        fill(quads, top, 0, failure, FILLS[3]);
        marker(quads, top, failure);

        top = gaugeTop(row++);
        track(quads, top);
        double bonus = profile.bonusChance() / CircuitNaniteRegistry.GAMBLING_BONUS;
        fill(quads, top, 0, bonus, FILLS[4]);
        marker(quads, top, bonus);

        top = gaugeTop(row++);
        track(quads, top);
        ticks(quads, top, fluidTicks);
        double end = sheet.fluidTotal() > tierFluids(1) ? fluidScale(sheet.fluidTotal()) : 0.03;
        double start = 0;
        for (int stage = 0; stage < sheet.fluids().length; stage++) {
            if (sheet.fluids()[stage] <= 0) continue;
            double segment = end * sheet.fluids()[stage] / sheet.fluidTotal();
            fill(quads, top, start, start + segment, FLUID_FILLS[Math.min(stage, FLUID_FILLS.length - 1)]);
            start += segment;
        }
        marker(quads, top, end);

        top = gaugeTop(row);
        track(quads, top);
        int cells = GTValues.VN.length;
        for (int k = 0; k < cells; k++) {
            float left = pixel(k / (double) cells), right = pixel((k + 1) / (double) cells) - 1;
            int color = k <= sheet.highestTier() ? TIER_COLORS[Math.min(9, Math.max(0, k - 5))] : rgb(196, 196, 196);
            quads.rect(left, top + 2, right - left + 0.5f, 2.5f, color);
        }
    }

    public static float gaugeTop(int row) {
        return HEADER + 3 + row * ROW;
    }

    private static float pixel(double t) {
        return (GAUGE_X * 2 + (float) Math.rint(Math.max(0, Math.min(1, t)) * (GAUGE_PIXELS - 1))) / 2f;
    }

    private static void track(TreeDiagramBoard quads, float top) {
        float width = GAUGE_PIXELS / 2f;
        quads.rect(GAUGE_X, top + 1.5f, width, 3.5f, rgb(176, 176, 176));
        quads.rect(GAUGE_X, top + 1.5f, width, 0.5f, rgb(139, 139, 139));
        quads.rect(GAUGE_X, top + 4.5f, width, 0.5f, rgb(238, 238, 238));
    }

    private static void ticks(TreeDiagramBoard quads, float top, double[] values) {
        for (int k = 0; k < values.length; k++) quads.rect(pixel(values[k]), top, 0.5f, 1, scale(TIER_COLORS[k], 0.75));
    }

    private static void fill(TreeDiagramBoard quads, float top, double from, double to, int color) {
        if (to <= from) return;
        float left = pixel(from), width = pixel(to) - left + 0.5f;
        quads.rect(left, top + 2, width, 2.5f, color);
        quads.rect(left, top + 2, width, 0.5f, brighten(color, 50));
        quads.rect(left, top + 4, width, 0.5f, scale(color, 0.75));
    }

    private static void band(TreeDiagramBoard quads, float top, double from, double to, int color) {
        float left = pixel(from), right = pixel(to);
        quads.rect(left, top + 1, right - left + 0.5f, 4.5f, 0x46000000 | color & 0xFFFFFF);
        quads.rect(left, top + 1, 0.5f, 4.5f, 0xC8000000 | color & 0xFFFFFF);
        quads.rect(right, top + 1, 0.5f, 4.5f, 0xC8000000 | color & 0xFFFFFF);
    }

    private static void marker(TreeDiagramBoard quads, float top, double value) {
        quads.rect(pixel(value), top + 0.5f, 0.5f, 5.5f, rgb(32, 32, 32));
    }

    public static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("gtnl.tree_diagram.pool." + key, args);
    }

    public static String format(double value, int places) {
        return String.format(Locale.ROOT, "%." + places + "f", value);
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int scale(int color, double k) {
        return 0xFF000000 | (int) ((color >> 16 & 255) * k) << 16
            | (int) ((color >> 8 & 255) * k) << 8
            | (int) ((color & 255) * k);
    }

    private static int brighten(int color, int amount) {
        return 0xFF000000 | Math.min(255, (color >> 16 & 255) + amount) << 16
            | Math.min(255, (color >> 8 & 255) + amount) << 8
            | Math.min(255, (color & 255) + amount);
    }
}
