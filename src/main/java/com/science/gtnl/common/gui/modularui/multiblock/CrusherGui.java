package com.science.gtnl.common.gui.modularui.multiblock;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DrawableStack;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.GenericSyncValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.ItemDisplayWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.science.gtnl.api.ICrusherGui;
import com.science.gtnl.common.gui.GTNLMui2Textures;
import com.science.gtnl.common.gui.modularui.GTNLMultiBlockBaseGui;
import com.science.gtnl.common.recipe.gtnl.IndustrialRockCrusherRecipes;

import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.modularui2.GTGuiTextures;

public class CrusherGui<M extends MTEMultiBlockBase & ICrusherGui> extends GTNLMultiBlockBaseGui<M> {

    private static final String ITEM_SELECTION_SYNC_KEY = "crusherItemSelection";
    private static final String SELECTION_PANEL_KEY = "crusherSelectionPanel";
    private static final int PANEL_WIDTH = 110;
    private static final int ENTRY_COLUMNS = 5;
    private static final int ENTRY_BUTTON_SIZE = 16;
    private static final int ENTRY_STEP = 18;
    private static final int ENTRY_START_X = 9;
    private static final int ENTRY_START_Y = 14;

    public CrusherGui(M multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            ITEM_SELECTION_SYNC_KEY,
            GenericSyncValue.builder(ItemId.class)
                .getter(multiblock::getItemEntry)
                .setter(multiblock::setItemEntry)
                .serializer((buffer, id) -> {
                    buffer.writeBoolean(id != null);
                    if (id != null) buffer.writeNBTTagCompoundToBuffer(id.write(new NBTTagCompound()));
                })
                .deserializer(
                    buffer -> buffer.readBoolean() ? ItemId.create(buffer.readNBTTagCompoundFromBuffer()) : null)
                .nullable()
                .build()
                .allowC2S());
    }

    @Override
    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        Flow row = super.createRightPanelGapRow(parent, syncManager);
        row.child(createSelectionPanelButton(parent, syncManager));
        return row;
    }

    private IWidget createSelectionPanelButton(ModularPanel parent, PanelSyncManager syncManager) {
        IPanelHandler selectionPanel = syncManager.syncedPanel(
            SELECTION_PANEL_KEY,
            true,
            (_, _) -> createSelectionPanel(parent, syncManager));
        return new ButtonWidget<>().size(18, 18)
            .overlay(new DrawableStack(GTGuiTextures.BUTTON_STANDARD, GTNLMui2Textures.OVERLAY_BUTTON_ARROW_GREEN_UP))
            .onMousePressed(_ -> {
                if (selectionPanel.isPanelOpen()) {
                    selectionPanel.closePanel();
                } else {
                    selectionPanel.openPanel();
                }
                return true;
            })
            .tooltipShowUpTimer(TOOLTIP_DELAY);
    }

    private ModularPanel createSelectionPanel(ModularPanel parent, PanelSyncManager syncManager) {
        Dialog<?> panel = new Dialog<>(SELECTION_PANEL_KEY);
        panel.relative(parent)
            .leftRel(1)
            .topRel(0)
            .size(PANEL_WIDTH, getSelectionPanelHeight())
            .background(GTGuiTextures.BACKGROUND_POPUP_STANDARD);
        panel.setDisablePanelsBelow(false)
            .setCloseOnOutOfBoundsClick(false)
            .setDraggable(true);
        panel.child(createEntryGrid(syncManager));
        panel.child(ButtonWidget.panelCloseButton());
        return panel;
    }

    private IWidget createEntryGrid(PanelSyncManager syncManager) {
        ParentWidget<?> grid = new ParentWidget<>().size(PANEL_WIDTH, getEntryGridHeight());
        List<ItemId> entries = IndustrialRockCrusherRecipes.ENTRIES;
        for (int i = 0; i < entries.size(); i++) {
            int col = i % ENTRY_COLUMNS;
            int row = i / ENTRY_COLUMNS;
            grid.child(
                createEntryButton(entries.get(i), syncManager)
                    .pos(ENTRY_START_X + col * ENTRY_STEP, ENTRY_START_Y + row * ENTRY_STEP));
        }
        return grid;
    }

    private Widget<?> createEntryButton(ItemId entry, PanelSyncManager syncManager) {
        if (entry != null) {
            var syncer = getItemSelectionSyncer(syncManager);
            ItemStack stack = entry.toStack(1);
            return createSelectionButton(
                new ItemDisplayWidget().item(stack)
                    .displayAmount(false)
                    .disableThemeBackground(true)
                    .disableHoverThemeBackground(true)
                    .size(ENTRY_BUTTON_SIZE),
                syncer,
                entry,
                () -> entry.equals(syncer.getValue()))
                    .tooltipBuilder(tooltip -> tooltip.addLine(stack.getDisplayName()));
        }
        return new ParentWidget<>().size(ENTRY_BUTTON_SIZE);
    }

    private ButtonWidget<?> createSelectionButton(IWidget icon, GenericSyncValue<ItemId, ?> syncer, ItemId entry,
        SelectedState selectedState) {
        return new ButtonWidget<>().size(ENTRY_BUTTON_SIZE)
            .background(
                new DynamicDrawable(
                    () -> selectedState.isSelected() ? GTGuiTextures.BUTTON_STANDARD_PRESSED
                        : GTGuiTextures.BUTTON_STANDARD))
            .child(icon)
            .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouseData -> {
                if (mouseData.isClient() || mouseData.mouseButton != 0) return;
                syncer.setValue(entry.equals(syncer.getValue()) ? null : entry, true, true);
            }))
            .tooltipShowUpTimer(TOOLTIP_DELAY);
    }

    private int getSelectionPanelHeight() {
        return 16 + (IndustrialRockCrusherRecipes.ENTRIES.size() + 8) / ENTRY_COLUMNS * ENTRY_STEP;
    }

    private int getEntryGridHeight() {
        return Math.max(ENTRY_START_Y + ENTRY_BUTTON_SIZE, getSelectionPanelHeight() - 30);
    }

    @SuppressWarnings("unchecked")
    private GenericSyncValue<ItemId, ?> getItemSelectionSyncer(PanelSyncManager syncManager) {
        return syncManager.findSyncHandler(ITEM_SELECTION_SYNC_KEY, GenericSyncValue.class);
    }

    private interface SelectedState {

        boolean isSelected();
    }
}
