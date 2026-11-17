package com.science.gtnl.common.gui.recipe;

import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.COLUMNS;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.CORE_X;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.CORE_Y;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.FLUID_Y;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.GRID_X;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.HEIGHT;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.ITEM_Y;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.SLOTS;
import static com.science.gtnl.common.gui.recipe.TreeDiagramNEIBoard.WIDTH;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

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
import com.science.gtnl.common.gui.modularui.TreeDiagramUpgradePanels;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.common.recipe.gtnl.treeDiagram.CircuitRecipeGenerationReport;
import com.science.gtnl.mixins.early.gregtech.AccessorRecipeDisplayInfo;
import com.science.gtnl.utils.recipes.data.CircuitRecipeRequirement;
import com.science.gtnl.utils.recipes.metadata.TreeDiagramCircuitMetadata;

import codechicken.nei.recipe.GuiCraftingRecipe;
import gregtech.api.enums.GTValues;
import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.nei.GTNEIDefaultHandler;
import gregtech.nei.RecipeDisplayInfo;

public class TreeDiagramRecipeFrontend extends GTNLRecipeFrontend implements IRecipeAreaHandler {

    public static final int TABLE_ROW = 11, TABLE_ROWS = 4, TABLE_LEFT = 3, TABLE_RIGHT = 95;
    public static final int TABLE_TOP = HEIGHT - 11 + 4;
    public static final int KEY_COLOR = 0xFF6A6A6A, VALUE_COLOR = 0xFF101010, COMPUTATION_COLOR = 0xFF136B86,
        NANITE_COLOR = 0xFF9A6400, UNLOCK_COLOR = 0xFF6A3FB8;

    private final BasicUIProperties fixedUI;
    private final NEIRecipeProperties fixedNEI;

    public TreeDiagramRecipeFrontend(BasicUIPropertiesBuilder ui, NEIRecipePropertiesBuilder nei) {
        super(ui, nei);
        fixedUI = uiProperties.toBuilder()
            .maxItemInputs(SLOTS)
            .maxItemOutputs(1)
            .maxFluidInputs(SLOTS)
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
                return builder.setHeight(HEIGHT + 4 + TABLE_ROWS * TABLE_ROW + 8)
                    .setMultipleWidgetsAllowed(false);
            })
            .build();
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
    public BasicUIProperties getUIProperties() {
        return fixedUI;
    }

    @Override
    public NEIRecipeProperties getNEIProperties() {
        return fixedNEI;
    }

    @Override
    protected List<Slot> itemInputSlots(int count) {
        return grid(count, GRID_X, ITEM_Y, COLUMNS);
    }

    @Override
    protected List<Slot> fluidInputSlots(int count) {
        return grid(count, GRID_X, FLUID_Y, COLUMNS);
    }

    @Override
    protected List<Slot> itemOutputSlots(int count) {
        return slots(CORE_X - 9, CORE_Y - 9);
    }

    @Override
    protected List<Slot> fluidOutputSlots(int count) {
        return Collections.emptyList();
    }

    @Override
    public ModularWindow.Builder createNEITemplate(GTNEIDefaultHandler.NEITemplateContext ctx) {
        ModularWindow.Builder builder = ModularWindow.builder(fixedNEI.recipeBackgroundSize);
        builder.widget(
            new DrawableWidget().setDrawable(new TreeDiagramNEIBoard(ctx.recipeSupplier))
                .setSize(WIDTH, HEIGHT)
                .setPos(ctx.windowOffset));
        List<Pos2d> items = getItemInputPositions(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            builder.widget(
                SlotWidget.phantom(ctx.itemInputsInventory, i)
                    .setBackground(IDrawable.EMPTY)
                    .setPos(
                        items.get(i)
                            .add(ctx.windowOffset))
                    .setSize(18, 18));
        }
        List<Pos2d> fluids = getFluidInputPositions(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            builder.widget(
                SlotWidget.phantom(ctx.fluidInputsInventory, i)
                    .setBackground(IDrawable.EMPTY)
                    .setPos(
                        fluids.get(i)
                            .add(ctx.windowOffset))
                    .setSize(18, 18));
        }
        builder.widget(
            SlotWidget.phantom(ctx.itemOutputsInventory, 0)
                .setBackground(IDrawable.EMPTY)
                .setPos(position(CORE_X - 9, CORE_Y - 9).add(ctx.windowOffset))
                .setSize(18, 18));
        return builder;
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder, Pos2d windowOffset) {}

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
        font.drawString(key, x, y, KEY_COLOR);
        int valueX = x + Math.max(22, font.getStringWidth(key) + 4);
        for (Run value : values) {
            font.drawString(value.text(), valueX, y, value.color());
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
        return mouseX >= TABLE_RIGHT && mouseX < WIDTH - 5 && mouseY >= TABLE_TOP - 1 && mouseY < TABLE_TOP + 9;
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
