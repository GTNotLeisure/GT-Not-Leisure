package com.science.gtnl.common.gui.recipe;

import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.COLUMNS;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.CORE_X;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.CORE_Y;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.FLUID_Y;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.GRID_X;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.HEIGHT;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.ITEM_Y;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.OFFSET_X;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.SLOTS;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.SLOT_PITCH;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.SLOT_SIZE;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.TRANSFER_SIZE;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.TRANSFER_X;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.TRANSFER_Y;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.WIDTH;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

import org.jetbrains.annotations.NotNull;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.circuit.CircuitCatalog;
import com.science.gtnl.common.circuit.CircuitFamily;
import com.science.gtnl.common.gui.modularui.TreeDiagramTheme;
import com.science.gtnl.common.gui.modularui.TreeDiagramUpgradePanels;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.common.recipe.gtnl.treeDiagram.CircuitRecipeGenerationReport;
import com.science.gtnl.mixins.early.gregtech.AccessorRecipeDisplayInfo;
import com.science.gtnl.utils.recipes.data.CircuitRecipeRequirement;
import com.science.gtnl.utils.recipes.metadata.TreeDiagramCircuitMetadata;

import codechicken.nei.recipe.GuiCraftingRecipe;
import gregtech.api.enums.GTValues;
import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.RecipeDisplayInfo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class TreeDiagramRecipeFrontend extends GTNLRecipeFrontend implements IRecipeAreaHandler {

    public static final int TABLE_ROW = 11, TABLE_ROWS = 4, TABLE_LEFT = 3 + OFFSET_X, TABLE_RIGHT = 95 + OFFSET_X;
    public static final int TABLE_TOP = HEIGHT + 4;
    public static final int RECIPE_WIDTH = WIDTH + OFFSET_X;
    public static final int RECIPE_HEIGHT = TABLE_TOP + TABLE_ROWS * TABLE_ROW + 12;
    public static final int KEY_COLOR = 0xFF6A6A6A, VALUE_COLOR = 0xFF101010, COMPUTATION_COLOR = 0xFF136B86,
        NANITE_COLOR = 0xFF9A6400, UNLOCK_COLOR = 0xFF6A3FB8;

    // GT shifts NEI slot templates eleven pixels upward.
    private static final int TEMPLATE_Y_OFFSET = 11;

    public TreeDiagramRecipeFrontend(BasicUIPropertiesBuilder ui, NEIRecipePropertiesBuilder nei) {
        super(
            ui.maxItemInputs(SLOTS)
                .maxItemOutputs(1)
                .maxFluidInputs(SLOTS)
                .maxFluidOutputs(0)
                .clearNEITransferRects()
                .addNEITransferRect(
                    new Rectangle(TRANSFER_X + OFFSET_X, TRANSFER_Y + TEMPLATE_Y_OFFSET, TRANSFER_SIZE, TRANSFER_SIZE)),
            nei);
    }

    @Override
    protected @NotNull NEIRecipePropertiesBuilder modifyNEIProperties(NEIRecipePropertiesBuilder nei) {
        return nei.recipeBackgroundSize(size(WIDTH, HEIGHT))
            .recipeBackgroundOffset(position(OFFSET_X, TEMPLATE_Y_OFFSET))
            .unificateOutput(false);
    }

    public void configure(CircuitRecipeGenerationReport report) {
        if (report.itemInputs() > SLOTS || report.fluidInputs() > SLOTS || report.itemOutputs() > 1) {
            ScienceNotLeisure.LOG.warn(
                "Tree Diagram recipes need {} items, {} fluids and {} outputs; the NEI page shows {}, {} and 1",
                report.itemInputs(),
                report.fluidInputs(),
                report.itemOutputs(),
                SLOTS,
                SLOTS);
        }
    }

    @Override
    protected @NotNull List<Slot> itemInputSlots(int count) {
        return inputSlots(count, ITEM_Y);
    }

    @Override
    protected @NotNull List<Slot> fluidInputSlots(int count) {
        return inputSlots(count, FLUID_Y);
    }

    private static List<Slot> inputSlots(int count, int top) {
        List<Slot> slots = new ObjectArrayList<>(count);
        for (int i = 0; i < count; i++) {
            slots.add(
                new Slot(
                    GRID_X + OFFSET_X + i % COLUMNS * SLOT_PITCH,
                    top + TEMPLATE_Y_OFFSET + i / COLUMNS * SLOT_PITCH));
        }
        return slots;
    }

    @Override
    protected @NotNull List<Slot> itemOutputSlots(int count) {
        return slots(CORE_X - SLOT_SIZE / 2 + OFFSET_X, CORE_Y - SLOT_SIZE / 2 + TEMPLATE_Y_OFFSET);
    }

    @Override
    protected @NotNull List<Slot> fluidOutputSlots(int count) {
        return Collections.emptyList();
    }

    @Override
    public ModularWindow.@NotNull Builder createNEITemplate(GTNEIDefaultHandler.@NotNull NEITemplateContext ctx) {
        ModularWindow.Builder builder = ModularWindow.builder(neiProperties.recipeBackgroundSize);
        builder.widget(
            new DrawableWidget().setDrawable(new TreeDiagramNEIBoard(ctx.recipeSupplier))
                .setSize(WIDTH, HEIGHT)
                .setPos(ctx.windowOffset.add(OFFSET_X, TEMPLATE_Y_OFFSET)));
        List<Pos2d> items = getItemInputPositions(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            builder.widget(
                SlotWidget.phantom(ctx.itemInputsInventory, i)
                    .setBackground(IDrawable.EMPTY)
                    .setPos(
                        items.get(i)
                            .add(ctx.windowOffset))
                    .setSize(SLOT_SIZE, SLOT_SIZE));
        }
        List<Pos2d> fluids = getFluidInputPositions(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            builder.widget(
                SlotWidget.phantom(ctx.fluidInputsInventory, i)
                    .setBackground(IDrawable.EMPTY)
                    .setPos(
                        fluids.get(i)
                            .add(ctx.windowOffset))
                    .setSize(SLOT_SIZE, SLOT_SIZE));
        }
        builder.widget(
            SlotWidget.phantom(ctx.itemOutputsInventory, 0)
                .setBackground(IDrawable.EMPTY)
                .setPos(
                    getItemOutputPositions(1).get(0)
                        .add(ctx.windowOffset))
                .setSize(SLOT_SIZE, SLOT_SIZE));
        return builder;
    }

    @Override
    public void addGregTechLogo(ModularWindow.@NotNull Builder builder, @NotNull Pos2d windowOffset) {}

    @Override
    public void drawNEIOverlays(GTNEIDefaultHandler.CachedDefaultRecipe recipe) {
        TreeDiagramNEIBoard.drawFluidFrames(recipe.mInputs);
        super.drawNEIOverlays(recipe);
    }

    @Override
    public void drawDescription(RecipeDisplayInfo recipeInfo) {
        GTRecipe recipe = recipeInfo.recipe;
        CircuitRecipeRequirement requirement = recipe.getMetadata(TreeDiagramCircuitMetadata.INSTANCE);
        int top = TABLE_TOP;
        int left = TABLE_LEFT, right = TABLE_RIGHT;
        if (requirement != null) {
            int tier = Math.max(0, Math.min(14, requirement.circuitTier()));
            CircuitFamily family = CircuitCatalog.INSTANCE.family(requirement.familyId());
            String familyName = family == null || family.members()
                .size() <= 1 ? tr("single") : TreeDiagramUpgradePanels.familyName(family);
            cell(
                left,
                top,
                tr("tier"),
                color(VALUE_COLOR, GTValues.TIER_COLORS[tier] + GTValues.VN[tier]),
                color(VALUE_COLOR, " · " + familyName));
            cell(right, top, tr("nanite"), color(NANITE_COLOR, "T" + requirement.requiredNaniteTier()));
            cell(
                left,
                top + TABLE_ROW,
                tr("computation"),
                color(COMPUTATION_COLOR, NumberFormatUtil.formatNumber(requirement.baseComputation())),
                color(KEY_COLOR, " CU"));
            if (family != null) {
                cell(
                    left,
                    top + TABLE_ROW * 3,
                    tr("unlock"),
                    color(UNLOCK_COLOR, NumberFormatUtil.formatNumber(family.requiredItems())),
                    color(
                        VALUE_COLOR,
                        tr(
                            "unlock_item",
                            family.unlockItem()
                                .toStack(1)
                                .getDisplayName())));
            }
        }
        cell(
            right,
            top + TABLE_ROW,
            tr("time"),
            color(VALUE_COLOR, seconds(recipe.mDuration)),
            color(KEY_COLOR, tr("seconds")));
        int voltage = GTUtility.getTier(recipe.mEUt);
        int voltageTier = Math.max(0, Math.min(GTValues.VN.length - 1, voltage));
        cell(
            left,
            top + TABLE_ROW * 2,
            tr("power"),
            color(VALUE_COLOR, NumberFormatUtil.formatNumber(recipe.mEUt)),
            color(KEY_COLOR, " EU/t "),
            color(VALUE_COLOR, GTValues.TIER_COLORS[voltageTier] + "(" + GTValues.VN[voltageTier] + ")"));
        ((AccessorRecipeDisplayInfo) recipeInfo).setYPos(top + TABLE_ROW * TABLE_ROWS);
    }

    @Desugar
    public record Run(String text, int color) {}

    private static void cell(int x, int y, String key, Run... values) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawString(key, x, y, TreeDiagramTheme.textColor(KEY_COLOR));
        int valueX = x + Math.max(22, font.getStringWidth(key) + 4);
        for (Run value : values) {
            font.drawString(value.text(), valueX, y, TreeDiagramTheme.textColor(value.color()));
            valueX += font.getStringWidth(value.text());
        }
    }

    private static Run color(int color, String text) {
        return new Run(text, color);
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? NumberFormatUtil.formatNumber(ticks / 20)
            : String.format(Locale.ROOT, "%.1f", ticks / 20.0);
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("gtnl.tree_diagram.nei." + key, args);
    }

    private static boolean overNaniteCell(int mouseX, int mouseY) {
        return mouseX >= TABLE_RIGHT && mouseX < WIDTH + OFFSET_X - 5
            && mouseY >= TABLE_TOP - 1
            && mouseY < TABLE_TOP + 9;
    }

    @Override
    public void addAreaTooltip(GTRecipe recipe, int mouseX, int mouseY, List<String> tooltip) {
        if (overNaniteCell(mouseX, mouseY)) tooltip.add(tr("nanite_link"));
    }

    @Override
    public boolean clickArea(GTRecipe recipe, int mouseX, int mouseY, int button) {
        if (button != 0 || !overNaniteCell(mouseX, mouseY)) return false;
        return GuiCraftingRecipe.openRecipeGui(GTNLRecipeMaps.CircuitNanitesDataRecipes.unlocalizedName);
    }
}
