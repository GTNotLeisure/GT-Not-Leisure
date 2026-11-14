package com.science.gtnl.common.gui.modularui;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.StringValue;
import com.cleanroommc.modularui.value.sync.InteractionSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.layout.Grid;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.science.gtnl.common.circuit.CircuitCatalog;
import com.science.gtnl.common.circuit.CircuitFamily;
import com.science.gtnl.common.gui.GTNLMui2Textures;
import com.science.gtnl.common.gui.modularui.widget.FittedTextWidget;
import com.science.gtnl.common.machine.multiblock.wireless.TreeDiagram;

import codechicken.nei.recipe.GuiCraftingRecipe;
import codechicken.nei.recipe.GuiUsageRecipe;
import gregtech.api.enums.GTValues;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.synchandler.NBTTagSyncHandler;
import gregtech.common.modularui2.widget.SlotLikeButtonWidget;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class TreeDiagramUpgradePanels extends GTNLControllerUpgradePanels {

    private static final String PAID_KEY = "treeDiagramFamilyPayments";
    private static final int WIDTH = 320;
    private static final int HEIGHT = 222;
    private static final int ROWS = 5;

    private final TreeDiagram machine;
    private final Map<String, IPanelHandler> panels;

    public TreeDiagramUpgradePanels(TreeDiagram machine, Map<String, IPanelHandler> panels) {
        super(machine, machine, panels);
        this.machine = machine;
        this.panels = panels;
    }

    @Override
    public void registerPanels(ModularPanel parent, PanelSyncManager sync) {
        panels.put(
            UPGRADE_CURRENT_PANEL_KEY,
            sync.syncedPanel(
                UPGRADE_CURRENT_PANEL_KEY,
                true,
                (panelSync, handler) -> createUpgradePanel(parent, sync, panelSync, true, 0)));
    }

    @Override
    public void registerSyncValues(PanelSyncManager sync) {
        sync.syncValue(PAID_KEY, new NBTTagSyncHandler(machine::getUnlockDisplayData, ignored -> {}));
    }

    @Override
    public IWidget createUpgradeButton(PanelSyncManager sync) {
        return new ButtonWidget<>().size(18)
            .overlay(GTNLMui2Textures.OVERLAY_BUTTON_ARROW_GREEN_UP)
            .onMousePressed(button -> {
                panels.get(UPGRADE_CURRENT_PANEL_KEY)
                    .openPanel();
                return true;
            })
            .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.unlock")));
    }

    @Override
    protected ModularPanel createUpgradePanel(ModularPanel parent, PanelSyncManager rootSync,
        PanelSyncManager panelSync, boolean currentPanel, int displayPage) {
        NBTTagSyncHandler paid = rootSync.findSyncHandler(PAID_KEY, NBTTagSyncHandler.class);
        FamilyView view = new FamilyView(paid);
        Dialog<?> panel = new Dialog<>(UPGRADE_CURRENT_PANEL_KEY, null);
        panel.relative(parent)
            .size(WIDTH, HEIGHT)
            .background(GTGuiTextures.BACKGROUND_POPUP_STANDARD);
        panel.setDisablePanelsBelow(false)
            .setCloseOnOutOfBoundsClick(false)
            .setDraggable(true);
        panel.child(ButtonWidget.panelCloseButton());
        panel.child(
            new FittedTextWidget(() -> tr("gtnl.tree_diagram.gui.unlock"), 1.0f).pos(8, 7)
                .width(WIDTH - 35));
        panel.child(
            new TextFieldWidget().value(view.search)
                .setMaxLength(128)
                .autoUpdateOnChange(true)
                .pos(8, 25)
                .size(224, 18)
                .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.search"))));
        for (int row = 0; row < ROWS; row++) {
            int index = row;
            int y = 48 + row * 24;
            panel.child(
                new SlotLikeButtonWidget(() -> view.item(index)).pos(8, y)
                    .size(18)
                    .onMousePressed(button -> {
                        ItemStack item = view.item(index);
                        if (item == null) return false;
                        if (button == 0) GuiCraftingRecipe.openRecipeGui("item", item);
                        else if (button == 1) GuiUsageRecipe.openRecipeGui("item", item);
                        return true;
                    })
                    .tooltipBuilder(tooltip -> {
                        CircuitFamily family = view.family(index);
                        if (family == null) return;
                        tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.family_members"));
                        for (ItemId member : family.members()) tooltip.addLine(
                            member.toStack(1)
                                .getDisplayName());
                    })
                    .tooltipAutoUpdate(true));
            panel.child(
                new FittedTextWidget(() -> view.title(index), 1.0f).pos(30, y - 1)
                    .width(200));
            panel.child(
                new FittedTextWidget(() -> view.progress(index), 1.0f).pos(30, y + 10)
                    .width(200));
        }
        panel.child(
            new ButtonWidget<>().pos(8, 174)
                .size(18, 16)
                .overlay(IKey.str("<"))
                .onMousePressed(button -> {
                    view.page = Math.max(0, view.page - 1);
                    return true;
                }));
        panel.child(
            new ButtonWidget<>().pos(214, 174)
                .size(18, 16)
                .overlay(IKey.str(">"))
                .onMousePressed(button -> {
                    view.page = Math.min(view.pageCount() - 1, view.page + 1);
                    return true;
                }));
        panel.child(
            new FittedTextWidget(
                () -> tr(
                    "gtnl.tree_diagram.gui.pages",
                    view.page + 1,
                    view.pageCount(),
                    view.filtered()
                        .size()),
                1.0f).pos(35, 177)
                    .width(172));
        panel.child(
            new FittedTextWidget(() -> tr("gtnl.tree_diagram.gui.input"), 1.0f).pos(240, 29)
                .width(72));
        panelSync.registerSlotGroup(UPGRADE_INPUT_SLOT_GROUP, 4, SlotGroup.STORAGE_SLOT_PRIO - 1);
        panel.child(
            new Grid().coverChildren()
                .gridOfWidthHeight(
                    4,
                    4,
                    (x, y, index) -> new ItemSlot().slot(
                        new ModularSlot(machine.getUpgradeInputSlotHandler(), index).ignoreMaxStackSize(true)
                            .slotGroup(UPGRADE_INPUT_SLOT_GROUP)))
                .pos(240, 48));
        panel.child(
            new ButtonWidget<>().pos(240, 126)
                .size(72, 20)
                .overlay(
                    IKey.lang("gtnl.tree_diagram.gui.submit")
                        .scale(0.75f))
                .syncHandler(new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    if (machine.getBaseMetaTileEntity() == null || !machine.getBaseMetaTileEntity()
                        .isServerSide()) return;
                    machine.tryConsumeItems();
                    paid.notifyUpdate();
                }))
                .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.submit_hint"))));
        panel.child(
            new ButtonWidget<>().pos(240, 154)
                .size(72, 20)
                .overlay(
                    IKey.dynamic(
                        () -> tr(
                            view.lockedOnly ? "gtnl.tree_diagram.gui.locked_only"
                                : "gtnl.tree_diagram.gui.all_families"))
                        .scale(0.65f))
                .onMousePressed(button -> {
                    view.lockedOnly = !view.lockedOnly;
                    view.lastSearch = null;
                    return true;
                }));
        panel.child(
            new FittedTextWidget(() -> tr("gtnl.tree_diagram.gui.unlock_hint"), 1.0f).pos(8, 199)
                .width(WIDTH - 16));
        return panel;
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    private static class FamilyView {

        private final NBTTagSyncHandler paid;
        private final StringValue search = new StringValue("");
        private List<CircuitFamily> visible = Collections.emptyList();
        private String lastSearch;
        private NBTTagCompound lastPayments;
        private boolean lockedOnly;
        private int page;

        private FamilyView(NBTTagSyncHandler paid) {
            this.paid = paid;
        }

        private List<CircuitFamily> filtered() {
            String query = search.getStringValue()
                .trim()
                .toLowerCase(Locale.ROOT);
            NBTTagCompound payments = paid.getValue();
            if (!query.equals(lastSearch) || payments != lastPayments) {
                boolean changedSearch = !query.equals(lastSearch);
                lastSearch = query;
                lastPayments = payments;
                visible = new ObjectArrayList<>();
                for (CircuitFamily family : CircuitCatalog.INSTANCE.families()) {
                    if (lockedOnly && paid(family) >= family.requiredItems()) continue;
                    boolean matches = family.id()
                        .contains(query)
                        || GTValues.VN[family.unlockTier()].toLowerCase(Locale.ROOT)
                            .contains(query);
                    for (ItemId member : family.members()) {
                        if (member.toStack(1)
                            .getDisplayName()
                            .toLowerCase(Locale.ROOT)
                            .contains(query)) matches = true;
                    }
                    if (matches) visible.add(family);
                }
                page = changedSearch ? 0 : Math.min(page, Math.max(0, (visible.size() - 1) / ROWS));
            }
            return visible;
        }

        private int pageCount() {
            return Math.max(1, (filtered().size() + ROWS - 1) / ROWS);
        }

        private CircuitFamily family(int row) {
            List<CircuitFamily> families = filtered();
            int index = page * ROWS + row;
            return index < families.size() ? families.get(index) : null;
        }

        private ItemStack item(int row) {
            CircuitFamily family = family(row);
            return family == null ? null
                : family.unlockItem()
                    .toStack(1);
        }

        private int paid(CircuitFamily family) {
            NBTTagCompound payments = paid.getValue();
            return payments == null ? 0
                : Math.max(0, Math.min(family.requiredItems(), payments.getInteger(family.id())));
        }

        private String title(int row) {
            CircuitFamily family = family(row);
            return family == null ? ""
                : GTValues.VN[family.unlockTier()] + " · "
                    + family.unlockItem()
                        .toStack(1)
                        .getDisplayName();
        }

        private String progress(int row) {
            CircuitFamily family = family(row);
            if (family == null) return "";
            int remaining = family.requiredItems() - paid(family);
            return remaining == 0 ? EnumChatFormatting.DARK_GREEN + tr("gtnl.tree_diagram.gui.unlocked")
                : tr(
                    "gtnl.tree_diagram.gui.remaining",
                    NumberFormatUtil.formatNumber(remaining),
                    NumberFormatUtil.formatNumber(family.requiredItems()));
        }
    }
}
