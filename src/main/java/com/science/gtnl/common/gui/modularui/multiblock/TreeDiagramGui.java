package com.science.gtnl.common.gui.modularui.multiblock;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Alignment.MainAxis;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.science.gtnl.api.IControllerUpgrade;
import com.science.gtnl.common.gui.modularui.GTNLControllerUpgradePanels;
import com.science.gtnl.common.gui.modularui.GTNLMultiBlockBaseGui;
import com.science.gtnl.common.gui.modularui.TreeDiagramUpgradePanels;
import com.science.gtnl.common.gui.modularui.widget.FittedTextWidget;
import com.science.gtnl.common.machine.multiblock.wireless.TreeDiagram;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.synchandler.NBTTagSyncHandler;

public class TreeDiagramGui extends GTNLMultiBlockBaseGui<TreeDiagram> {

    private static final String POWER_PANEL_KEY = "treeDiagramPowerPanel";
    private static final String NANITE_SLOT_GROUP = "treeDiagramNanites";
    private static final String RUNTIME_KEY = "treeDiagramRuntime";
    private static final int POWER_PANEL_WIDTH = 300;
    private static final int POWER_PANEL_HEIGHT = 234;
    private static final int NANITE_SHIFT_CLICK_PRIORITY = SlotGroup.STORAGE_SLOT_PRIO - 1;

    private NBTTagCompound runtimeSnapshot;
    private long lastRuntimeSync = Long.MIN_VALUE;

    public TreeDiagramGui(TreeDiagram multiblock) {
        super(multiblock);
    }

    @Override
    public ButtonWidget<?> createPowerPanelButton(PanelSyncManager syncManager, ModularPanel parent) {
        IPanelHandler powerPanel = syncManager.syncedPanel(
            POWER_PANEL_KEY,
            true,
            (panelSyncManager, panelHandler) -> openPowerControlPanel(parent, syncManager, panelSyncManager));
        return new ButtonWidget<>().marginLeft(4)
            .overlay(GTGuiTextures.TT_OVERLAY_BUTTON_POWER_PANEL)
            .onMousePressed(mouseButton -> {
                if (powerPanel.isPanelOpen()) {
                    powerPanel.closePanel();
                } else {
                    powerPanel.openPanel();
                }
                return true;
            })
            .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.runtime")))
            .tooltipShowUpTimer(TOOLTIP_DELAY);
    }

    @Override
    protected GTNLControllerUpgradePanels createUpgradePanels(IControllerUpgrade controllerUpgrade) {
        return new TreeDiagramUpgradePanels(multiblock, panelMap);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager
            .syncValue(RUNTIME_KEY, new NBTTagSyncHandler(this::runtimeData, multiblock::applyCircuitDisplayData));
    }

    private NBTTagCompound runtimeData() {
        IGregTechTileEntity tile = multiblock.getBaseMetaTileEntity();
        long tick = tile == null ? 0
            : tile.getWorld()
                .getTotalWorldTime();
        if (runtimeSnapshot == null || tick < lastRuntimeSync || tick - lastRuntimeSync >= 5) {
            runtimeSnapshot = multiblock.getCircuitDisplayData();
            lastRuntimeSync = tick;
        }
        return runtimeSnapshot;
    }

    private ModularPanel openPowerControlPanel(ModularPanel parent, PanelSyncManager rootSync, PanelSyncManager sync) {
        NBTTagSyncHandler runtime = rootSync.findSyncHandler(RUNTIME_KEY, NBTTagSyncHandler.class);
        Supplier<NBTTagCompound> data = () -> runtime.getValue() == null ? new NBTTagCompound() : runtime.getValue();
        Dialog<?> panel = new Dialog<>(POWER_PANEL_KEY, null);
        panel.relative(parent)
            .size(POWER_PANEL_WIDTH, POWER_PANEL_HEIGHT)
            .background(GTGuiTextures.BACKGROUND_POPUP_STANDARD);
        panel.setDisablePanelsBelow(false)
            .setCloseOnOutOfBoundsClick(false)
            .setDraggable(true);
        panel.child(ButtonWidget.panelCloseButton());
        panel.child(
            new FittedTextWidget(() -> tr("runtime"), 1.0f).pos(8, 7)
                .width(260));
        panel.child(
            new FittedTextWidget(() -> status(data.get()), 1.0f).pos(8, 25)
                .width(280));
        panel.child(
            new FittedTextWidget(
                () -> tr(
                    "cu_available",
                    number(
                        data.get()
                            .getLong("available"))),
                1.0f).pos(8, 46)
                    .width(156));
        panel.child(
            new FittedTextWidget(
                () -> tr(
                    "cu_required",
                    number(
                        data.get()
                            .getLong("required"))),
                1.0f).pos(8, 59)
                    .width(156));
        panel.child(
            new FittedTextWidget(
                () -> tr(
                    "speed",
                    decimal(
                        data.get()
                            .getDouble("speed"),
                        2)),
                1.0f).pos(8, 72)
                    .width(156));
        panel.child(
            new FittedTextWidget(
                () -> tr(
                    "power",
                    number(
                        data.get()
                            .getLong("eu"))),
                1.0f).pos(8, 85)
                    .width(156));
        panel.child(
            new FittedTextWidget(
                () -> tr(
                    "eta",
                    decimal(
                        data.get()
                            .getDouble("seconds"),
                        1)),
                1.0f).pos(8, 98)
                    .width(156));
        panel.child(
            new FittedTextWidget(
                () -> tr(
                    "batch_parallel",
                    data.get()
                        .getInteger("parallel")),
                1.0f).pos(8, 111)
                    .width(156));
        panel.child(
            new FittedTextWidget(
                () -> tr(
                    "batch_roll",
                    data.get()
                        .getInteger("failed"),
                    data.get()
                        .getInteger("bonus")),
                1.0f).pos(8, 124)
                    .width(156));
        panel.child(
            new FittedTextWidget(() -> tr("main_nanite"), 1.0f).pos(179, 42)
                .width(110));
        panel.child(
            new FittedTextWidget(() -> tr("support_nanites"), 1.0f).pos(179, 81)
                .width(110));
        sync.registerSlotGroup(NANITE_SLOT_GROUP, 1, NANITE_SHIFT_CLICK_PRIORITY);
        ItemStackHandler handler = multiblock.getNaniteSlotHandler();
        for (int slot = 0; slot < 4; slot++) {
            int index = slot;
            int x = slot == 0 ? 209 : 185 + (slot - 1) * 24;
            int y = slot == 0 ? 58 : 98;
            panel.child(
                new ItemSlot().slot(
                    new ModularSlot(handler, slot).filter(multiblock::isValidNaniteStack)
                        .slotGroup(NANITE_SLOT_GROUP))
                    .background(GTGuiTextures.SLOT_ITEM_STANDARD)
                    .pos(x, y)
                    .tooltipBuilder(tooltip -> {
                        NBTTagCompound state = data.get();
                        NBTTagCompound profile = state.getCompoundTag("nanite" + index);
                        if (profile.getInteger("tier") == 0) {
                            tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.nanite_pending"));
                        } else {
                            tooltip.addLine(
                                tr(
                                    "nanite_profile",
                                    profile.getInteger("tier"),
                                    tr(
                                        "branch." + profile.getString("branch")
                                            .toLowerCase(Locale.ROOT))));
                            tooltip.addLine(
                                tr(
                                    "nanite_efficiency",
                                    decimal(profile.getDouble("eu"), 2),
                                    decimal(profile.getDouble("duration"), 2),
                                    profile.getInteger("parallel")));
                            tooltip.addLine(
                                tr(
                                    "nanite_cancellation",
                                    decimal(profile.getDouble("rawLoss") * 100, 1),
                                    decimal(profile.getDouble("cancellation") * 100, 1)));
                            tooltip.addLine(
                                tr(
                                    "nanite_risk",
                                    decimal(profile.getDouble("loss") * 100, 1),
                                    decimal(profile.getDouble("bonus") * 100, 1)));
                        }
                        tooltip.addLine(
                            tr(
                                (state.getInteger("lockedSlots") & 1 << index) != 0 ? "slot_locked"
                                    : index == 0 ? "main_hint" : "support_hint"));
                    })
                    .tooltipAutoUpdate(true));
        }
        panel.child(
            new FittedTextWidget(() -> tr("parallel_limit"), 1.0f).pos(179, 119)
                .width(110));
        panel.child(
            makeParallelConfigurator().pos(174, 134)
                .size(116, 18));
        panel.child(
            new FittedTextWidget(() -> tr("maintenance"), 1.0f).pos(8, 152)
                .width(282));
        for (int row = 0; row < 3; row++) {
            int index = row;
            panel.child(
                new FittedTextWidget(() -> maintenance(data.get(), index), 1.0f).pos(8, 167 + row * 13)
                    .width(282));
        }
        panel.child(
            new FittedTextWidget(() -> tr("runtime_hint"), 1.0f).pos(8, 216)
                .width(282));
        return panel;
    }

    @Override
    public IWidget makeTitleTextWidget() {
        return IKey.lang("gtnl.tree_diagram.gui.runtime")
            .asWidget()
            .size(POWER_PANEL_WIDTH, 18);
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("gtnl.tree_diagram.gui." + key, args);
    }

    private static String number(long value) {
        return NumberFormatUtil.formatNumber(value);
    }

    private static String decimal(double value, int places) {
        return String.format(Locale.ROOT, "%." + places + "f", value);
    }

    private static String status(NBTTagCompound data) {
        String status = tr(
            "status." + data.getString("status")
                .toLowerCase(Locale.ROOT));
        String fluid = data.getString("missingFluid");
        return fluid.isEmpty() ? status : status + " · " + fluidName(fluid);
    }

    private static String fluidName(String name) {
        Fluid fluid = FluidRegistry.getFluid(name);
        return fluid == null ? name : fluid.getLocalizedName(new FluidStack(fluid, 1));
    }

    private static String maintenance(NBTTagCompound data, int index) {
        if (index >= data.getInteger("fluidCount")) return index == 0 ? tr("idle_maintenance") : "";
        NBTTagCompound fluid = data.getCompoundTag("fluid" + index);
        return tr("fluid_rate", fluidName(fluid.getString("name")), number((long) Math.ceil(fluid.getDouble("rate"))));
    }

    private Flow makeParallelConfigurator() {
        IntSyncValue maxParallelSyncer = new IntSyncValue(() -> Math.max(multiblock.getMaxParallelRecipes(), 1));
        BooleanSyncValue alwaysMaxParallelSyncer = new BooleanSyncValue(
            multiblock::isAlwaysMaxParallel,
            multiblock::setAlwaysMaxParallel).allowC2S();
        IntSyncValue powerPanelMaxParallelSyncer = new IntSyncValue(
            multiblock::getTrueParallel,
            multiblock::setPowerPanelMaxParallel).allowC2S();
        return Flow.row()
            .fullWidth()
            .marginBottom(4)
            .height(18)
            .paddingLeft(3)
            .paddingRight(3)
            .mainAxisAlignment(MainAxis.CENTER)
            .child(
                new TextFieldWidget().value(powerPanelMaxParallelSyncer)
                    .setTextAlignment(Alignment.Center)
                    .numbersInt(
                        () -> alwaysMaxParallelSyncer.getValue() ? maxParallelSyncer.getValue() : 1,
                        maxParallelSyncer::getValue)
                    .tooltipBuilder(
                        tooltip -> tooltip.addLine(
                            IKey.dynamic(
                                () -> alwaysMaxParallelSyncer.getValue()
                                    ? StatCollector.translateToLocalFormatted(
                                        "GT5U.gui.text.lockedvalue",
                                        maxParallelSyncer.getValue())
                                    : StatCollector.translateToLocalFormatted(
                                        "GT5U.gui.text.rangedvalue",
                                        1,
                                        maxParallelSyncer.getValue()))))
                    .tooltipShowUpTimer(TOOLTIP_DELAY)
                    .size(70, 14)
                    .marginBottom(4)
                    .marginRight(16))
            .child(
                new ButtonWidget<>()
                    .overlay(
                        new DynamicDrawable(
                            () -> alwaysMaxParallelSyncer.getValue() ? GTGuiTextures.OVERLAY_BUTTON_CHECKMARK
                                : GTGuiTextures.OVERLAY_BUTTON_CROSS))
                    .onMousePressed(mouseButton -> {
                        alwaysMaxParallelSyncer.setValue(!alwaysMaxParallelSyncer.getValue());
                        powerPanelMaxParallelSyncer.setValue(maxParallelSyncer.getValue());
                        return true;
                    })
                    .tooltipBuilder(tooltip -> tooltip.addLine(IKey.lang("GT5U.gui.button.max_parallel")))
                    .tooltipShowUpTimer(TOOLTIP_DELAY));
    }

}
