package com.science.gtnl.common.gui.recipe;

import java.awt.Rectangle;
import java.util.List;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizons.modularui.api.drawable.UITexture;
import com.gtnewhorizons.modularui.api.math.Pos2d;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.recipe.gtnl.treeDiagram.CircuitRecipeGenerationReport;

import gregtech.api.recipe.BasicUIProperties;
import gregtech.api.recipe.BasicUIPropertiesBuilder;
import gregtech.api.recipe.NEIRecipeProperties;
import gregtech.api.recipe.NEIRecipePropertiesBuilder;

public class TreeDiagramRecipeFrontend extends GTNLRecipeFrontend {

    private static final int COLUMNS = 4;
    private static final int SLOT_SIZE = 18;
    private static final int INPUT_Y = 6;
    private static final UITexture DIAGRAM = UITexture.fullImage(ScienceNotLeisure.MODID, "gui/picture/tree_diagram");
    private static final UITexture ARROW = UITexture
        .fullImage(ScienceNotLeisure.MODID, "gui/picture/tree_diagram_arrow");

    private volatile Layout layout;
    private final NEIRecipePropertiesBuilder neiBuilder;

    public TreeDiagramRecipeFrontend(BasicUIPropertiesBuilder ui, NEIRecipePropertiesBuilder nei) {
        super(ui, nei);
        neiBuilder = nei;
        resize(32, 4, 32, 4);
    }

    public void configure(CircuitRecipeGenerationReport report) {
        resize(
            Math.max(32, report.itemInputs()),
            Math.max(4, report.itemOutputs()),
            Math.max(32, report.fluidInputs()),
            Math.max(4, report.fluidOutputs()));
    }

    private void resize(int items, int itemOutputs, int fluids, int fluidOutputs) {
        int inputRows = rows(Math.max(items, fluids));
        int outputRows = rows(Math.max(itemOutputs, fluidOutputs));
        int connectorY = INPUT_Y + inputRows * SLOT_SIZE;
        int outputY = connectorY + 44;
        int height = outputY + outputRows * SLOT_SIZE + 4;
        BasicUIProperties ui = uiProperties.toBuilder()
            .maxItemInputs(rows(items) * COLUMNS)
            .maxItemOutputs(rows(itemOutputs) * COLUMNS)
            .maxFluidInputs(rows(fluids) * COLUMNS)
            .maxFluidOutputs(rows(fluidOutputs) * COLUMNS)
            .clearNEITransferRects()
            .addNEITransferRect(new Rectangle(44, connectorY, 76, 40))
            .build();
        NEIRecipeProperties nei = neiBuilder.recipeBackgroundSize(size(168, height))
            .recipeBackgroundOffset(position(0, 0))
            .neiSpecialInfoFormatter(neiProperties.neiSpecialInfoFormatter)
            .unificateOutput(false)
            .recipeComparator(neiProperties.comparator)
            .handlerInfoCreator(builder -> {
                if (neiProperties.handlerInfoCreator != null) builder = neiProperties.handlerInfoCreator.apply(builder);
                return builder.setHeight(height + 100)
                    .setMultipleWidgetsAllowed(false);
            })
            .build();
        layout = new Layout(ui, nei, connectorY, outputY);
    }

    private static int rows(int count) {
        return Math.max(1, (count + COLUMNS - 1) / COLUMNS);
    }

    @Override
    public BasicUIProperties getUIProperties() {
        return layout == null ? super.getUIProperties() : layout.ui();
    }

    @Override
    public NEIRecipeProperties getNEIProperties() {
        return layout == null ? super.getNEIProperties() : layout.nei();
    }

    @Override
    protected boolean borderless() {
        return true;
    }

    @Override
    protected List<Slot> itemInputSlots(int count) {
        return grid(count, 2, INPUT_Y, COLUMNS);
    }

    @Override
    protected List<Slot> fluidInputSlots(int count) {
        return grid(count, 94, INPUT_Y, COLUMNS);
    }

    @Override
    protected List<Slot> itemOutputSlots(int count) {
        return grid(count, 2, layout.outputY(), COLUMNS);
    }

    @Override
    protected List<Slot> fluidOutputSlots(int count) {
        return grid(count, 94, layout.outputY(), COLUMNS);
    }

    @Override
    public void addGregTechLogo(ModularWindow.Builder builder, Pos2d windowOffset) {
        builder.widget(
            new DrawableWidget().setDrawable(DIAGRAM)
                .setSize(72, 40)
                .setPos(position(48, layout.connectorY() + 2).add(windowOffset)));
        builder.widget(
            new DrawableWidget().setDrawable(ARROW)
                .setSize(10, 18)
                .setPos(position(27, layout.connectorY() + 12).add(windowOffset)));
        builder.widget(
            new DrawableWidget().setDrawable(ARROW)
                .setSize(10, 18)
                .setPos(position(132, layout.connectorY() + 12).add(windowOffset)));
    }

    @Desugar
    private record Layout(BasicUIProperties ui, NEIRecipeProperties nei, int connectorY, int outputY) {}
}
