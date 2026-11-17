package com.science.gtnl.common.gui.recipe;

import static com.science.gtnl.common.gui.recipe.CircuitNanitesDataBoard.HEADER;
import static com.science.gtnl.common.gui.recipe.CircuitNanitesDataBoard.HEIGHT;
import static com.science.gtnl.common.gui.recipe.CircuitNanitesDataBoard.ROW;
import static com.science.gtnl.common.gui.recipe.CircuitNanitesDataBoard.ROWS;
import static com.science.gtnl.common.gui.recipe.CircuitNanitesDataBoard.WIDTH;
import static com.science.gtnl.common.gui.recipe.CircuitNanitesDataBoard.format;
import static com.science.gtnl.common.gui.recipe.CircuitNanitesDataBoard.tr;

import java.util.Collections;
import java.util.List;

import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizons.modularui.api.ModularUITextures;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.science.gtnl.common.circuit.nanite.CircuitNaniteRegistry;
import com.science.gtnl.common.circuit.nanite.NaniteProfile;
import com.science.gtnl.common.gui.recipe.CircuitNanitesDataBoard.Sheet;

import gregtech.api.enums.GTValues;
import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.util.GTRecipe;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.RecipeDisplayInfo;

public class CircuitNanitesDataFrontend extends GTNLRecipeFrontend implements IRecipeAreaHandler {

    public static final int GAP = 7, TEMPLATE_X = 5, TEMPLATE_Y = 11;

    private final BasicUIProperties fixedUI;
    private final NEIRecipeProperties fixedNEI;

    public CircuitNanitesDataFrontend(BasicUIPropertiesBuilder ui, NEIRecipePropertiesBuilder nei) {
        super(ui, nei);
        fixedUI = uiProperties.toBuilder()
            .maxItemInputs(1)
            .maxItemOutputs(0)
            .maxFluidInputs(0)
            .maxFluidOutputs(0)
            .clearNEITransferRects()
            .build();
        fixedNEI = nei.recipeBackgroundSize(size(WIDTH, HEIGHT))
            .recipeBackgroundOffset(position(0, 0))
            .neiSpecialInfoFormatter(neiProperties.neiSpecialInfoFormatter)
            .unificateOutput(false)
            .recipeComparator(neiProperties.comparator)
            .handlerInfoCreator(builder -> {
                if (neiProperties.handlerInfoCreator != null) builder = neiProperties.handlerInfoCreator.apply(builder);
                return builder.setHeight(HEIGHT + GAP)
                    .setMultipleWidgetsAllowed(true);
            })
            .build();
    }

    @Override
    public @NotNull BasicUIProperties getUIProperties() {
        return fixedUI;
    }

    @Override
    public @NotNull NEIRecipeProperties getNEIProperties() {
        return fixedNEI;
    }

    @Override
    protected List<Slot> itemInputSlots(int count) {
        return slots(1, 1);
    }

    @Override
    protected @NotNull List<Slot> fluidInputSlots(int count) {
        return Collections.emptyList();
    }

    @Override
    public ModularWindow.@NotNull Builder createNEITemplate(GTNEIDefaultHandler.@NotNull NEITemplateContext ctx) {
        ModularWindow.Builder builder = ModularWindow.builder(fixedNEI.recipeBackgroundSize);
        builder.widget(
            new DrawableWidget().setDrawable(new CircuitNanitesDataBoard(ctx.recipeSupplier))
                .setSize(WIDTH, HEIGHT)
                .setPos(ctx.windowOffset));
        builder.widget(
            SlotWidget.phantom(ctx.itemInputsInventory, 0)
                .setBackground(ModularUITextures.ITEM_SLOT)
                .setPos(position(1, 1).add(ctx.windowOffset))
                .setSize(18, 18));
        return builder;
    }

    @Override
    public void addGregTechLogo(ModularWindow.@NotNull Builder builder, @NotNull Pos2d windowOffset) {}

    @Override
    public void drawDescription(@NotNull RecipeDisplayInfo recipeInfo) {}

    @Override
    public void addAreaTooltip(GTRecipe recipe, int mouseX, int mouseY, List<String> tooltip) {
        int x = mouseX + TEMPLATE_X, y = mouseY + TEMPLATE_Y - HEADER - 2;
        if (x < 1 || x >= WIDTH - 1 || y < 0 || y >= ROWS * ROW) return;
        Sheet sheet = CircuitNanitesDataBoard.sheet(recipe);
        if (sheet == null || sheet.profile() == null) return;
        int row = y / ROW;
        tooltip.add(EnumChatFormatting.WHITE + tr("row." + CircuitNanitesDataBoard.ROW_KEYS[row]));
        NaniteProfile profile = sheet.profile();
        int index = profile.tier() - 1;
        double low = 1 - CircuitNaniteRegistry.ROLL_SPREAD, high = 1 + CircuitNaniteRegistry.ROLL_SPREAD;
        switch (row) {
            case 0 -> {
                tooltip.add(EnumChatFormatting.GOLD + String.valueOf(profile.parallel()));
                tooltip.add(
                    EnumChatFormatting.GRAY
                        + tr("tip.support", sheet.support()[0], sheet.support()[1], sheet.support()[2]));
            }
            case 1 -> {
                double duration = profile.durationMultiplier();
                tooltip.add(EnumChatFormatting.AQUA + tr("tip.speed", format(1 / duration, 3), format(duration, 3)));
                tooltip.add(
                    EnumChatFormatting.GRAY + tr(
                        "tip.range",
                        format(1 / (CircuitNaniteRegistry.DURATION[index] * high), 3),
                        format(1 / (CircuitNaniteRegistry.DURATION[index] * low), 3)));
            }
            case 2 -> {
                double energy = profile.euMultiplier();
                tooltip
                    .add(EnumChatFormatting.GOLD + tr("tip.power", format(energy, 3), format((1 - energy) * 100, 1)));
                tooltip.add(
                    EnumChatFormatting.GRAY + tr(
                        "tip.range",
                        format(CircuitNaniteRegistry.ENERGY[index] * low, 3),
                        format(CircuitNaniteRegistry.ENERGY[index] * high, 3)));
            }
            case 3 -> {
                boolean fails = sheet.failure() > 0;
                tooltip.add(
                    (fails ? EnumChatFormatting.RED : EnumChatFormatting.GREEN) + format(sheet.failure() * 100, 0)
                        + "%");
                tooltip.add(EnumChatFormatting.GRAY + tr(fails ? "tip.failure_on" : "tip.failure_off"));
            }
            case 4 -> {
                boolean bonus = profile.bonusChance() > 0;
                tooltip.add(
                    (bonus ? EnumChatFormatting.LIGHT_PURPLE : EnumChatFormatting.GRAY)
                        + format(profile.bonusChance() * 100, 0)
                        + "%");
                tooltip.add(
                    EnumChatFormatting.GRAY
                        + (bonus ? tr("tip.bonus_on", format(profile.bonusFraction() * 100, 0)) : tr("tip.bonus_off")));
            }
            case 5 -> {
                for (int stage = 0; stage < sheet.fluids().length; stage++) {
                    if (sheet.fluids()[stage] <= 0) continue;
                    tooltip.add(
                        EnumChatFormatting.GREEN + tr(
                            "tip.fluid",
                            fluidName(
                                sheet.fluidNames()
                                    .get(stage)),
                            sheet.fluids()[stage]));
                }
            }
            default -> tooltip
                .add(EnumChatFormatting.GRAY + tr("tip.craftable", GTValues.VN[0], GTValues.VN[sheet.highestTier()]));
        }
        if (row != 4 && row != 6) tooltip.add(EnumChatFormatting.DARK_GRAY + tr("tip.ticks"));
    }

    @Override
    public boolean clickArea(GTRecipe recipe, int mouseX, int mouseY, int button) {
        return false;
    }

    private static String fluidName(String name) {
        Fluid fluid = FluidRegistry.getFluid(name);
        return fluid == null ? name : fluid.getLocalizedName(new FluidStack(fluid, 1));
    }
}
