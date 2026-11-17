package com.science.gtnl.common.gui.modularui.multiblock;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.opengl.GL11;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.Dialog;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.cleanroommc.modularui.widgets.slot.SlotGroup;
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.science.gtnl.api.IControllerUpgrade;
import com.science.gtnl.common.gui.modularui.GTNLControllerUpgradePanels;
import com.science.gtnl.common.gui.modularui.GTNLMultiBlockBaseGui;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard.Net;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard.Orbit;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard.Palette;
import com.science.gtnl.common.gui.modularui.TreeDiagramBoard.Trace;
import com.science.gtnl.common.gui.modularui.TreeDiagramUpgradePanels;
import com.science.gtnl.common.machine.multiblock.wireless.TreeDiagram;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.synchandler.NBTTagSyncHandler;

public class TreeDiagramGui extends GTNLMultiBlockBaseGui<TreeDiagram> {

    private static final String POWER_PANEL_KEY = "treeDiagramPowerPanel";
    private static final String NANITE_SLOT_GROUP = "treeDiagramNanites";
    private static final String RUNTIME_KEY = "treeDiagramRuntime";
    public static final int POWER_PANEL_WIDTH = 290;
    public static final int POWER_PANEL_HEIGHT = 190;
    private static final int MAINTENANCE_ROW_HEIGHT = 20;
    private static final int NANITE_SHIFT_CLICK_PRIORITY = SlotGroup.STORAGE_SLOT_PRIO - 1;
    public static final int[][] NANITE_SLOTS = { { 181, 49 }, { 155, 39 }, { 207, 39 }, { 181, 69 } };
    public static final int[] MAINTENANCE_TIERS = { 1, 4, 7 };

    private static final Trace CU_TOP = new Trace(9, 17, 70, 17, 73, 14, 110, 14);
    private static final Trace UNLOCK_LEFT = new Trace(4, 24, 4, 60, 6, 62, 6, 150);
    private static final Trace NANITE_RIGHT = new Trace(285, 24, 285, 100, 283, 102, 283, 172);
    private static final Trace FLUID_MIDDLE = new Trace(143, 74, 143, 162, 147, 166, 147, 182);
    private static final Trace POWER_MIDDLE = new Trace(146, 96, 146, 158, 150, 162, 150, 177);
    private static final Trace FLUID_BOTTOM = new Trace(60, 184, 120, 184, 124, 180, 140, 180);
    private static final Trace UNLOCK_CORNER = new Trace(3, 180, 14, 180, 20, 174);
    private static final Trace GRAY_CORNER = new Trace(3, 184, 30, 184);
    private static final Trace CU_CORNER = new Trace(286, 184, 250, 184, 246, 180);
    private static final Trace CU_FLOW = new Trace(40, 84, 40, 70);
    private static final Trace POWER_FLOW = new Trace(100, 84, 100, 79, 104, 75, 104, 70);
    private static final Trace NANITE_FLOW = new Trace(152, 58, 147, 58, 143, 54, 138, 54);
    private static final Trace FLUID_FLOW = new Trace(231, 106, 231, 98, 227, 94, 227, 92);
    private static final Orbit NANITE_ORBIT = new Orbit(190, 58, 30, 20);

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
        int lockedSlots = 0;
        for (int slot = 0; slot < NANITE_SLOTS.length; slot++) {
            if (multiblock.isNaniteSlotLocked(slot)) lockedSlots |= 1 << slot;
        }
        // Slot lock changes bypass the normal runtime refresh interval.
        if (runtimeSnapshot == null || runtimeSnapshot.getInteger("lockedSlots") != lockedSlots
            || tick < lastRuntimeSync
            || tick - lastRuntimeSync >= 5) {
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
            .background(new RuntimeBoard(data, multiblock.getNaniteSlotHandler()));
        panel.setDisablePanelsBelow(false)
            .setCloseOnOutOfBoundsClick(false)
            .setDraggable(true);
        ButtonWidget<?> close = new ButtonWidget<>();
        panel.child(
            close.pos(POWER_PANEL_WIDTH - 15, 4)
                .size(11)
                .background(TreeDiagramBoard.closeBackground())
                .hoverBackground(TreeDiagramBoard.closeBackground())
                .onMousePressed(mouseButton -> {
                    close.getPanel()
                        .closeIfOpen();
                    return true;
                }));
        panel.child(
            new Widget<>().pos(120, 4)
                .size(150, 12)
                .background(IDrawable.EMPTY)
                .tooltipBuilder(
                    tooltip -> tooltip.addLine(
                        IKey.str(
                            tr(
                                "status." + data.get()
                                    .getString("status")
                                    .toLowerCase(Locale.ROOT)))))
                .tooltipAutoUpdate(true));
        sync.registerSlotGroup(NANITE_SLOT_GROUP, 1, NANITE_SHIFT_CLICK_PRIORITY);
        ItemStackHandler handler = multiblock.getNaniteSlotHandler();
        for (int slot = 0; slot < NANITE_SLOTS.length; slot++) {
            int index = slot;
            panel.child(
                new ItemSlot().slot(
                    new ModularSlot(handler, slot).filter(multiblock::isValidNaniteStack)
                        .slotGroup(NANITE_SLOT_GROUP))
                    .background(IDrawable.EMPTY)
                    .overlay(naniteOverlay(index, data))
                    .pos(NANITE_SLOTS[slot][0], NANITE_SLOTS[slot][1])
                    .tooltipBuilder(tooltip -> {
                        NBTTagCompound state = data.get();
                        NBTTagCompound profile = state.getCompoundTag("nanite" + index);
                        if (profile.getInteger("tier") == 0) {
                            tooltip.addLine(IKey.lang("gtnl.tree_diagram.gui.nanite_pending"));
                        } else {
                            tooltip.addLine(
                                tr("nanite_profile", profile.getInteger("tier"), profile.getInteger("parallel")));
                            tooltip.addLine(
                                tr(
                                    "nanite_efficiency",
                                    decimal(profile.getDouble("eu"), 2),
                                    decimal(profile.getDouble("duration"), 2)));
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
        addParallelControls(panel, sync);
        addMaintenanceTooltips(panel, data);
        return panel;
    }

    @Override
    public IWidget makeTitleTextWidget() {
        return IKey.lang("gtnl.tree_diagram.gui.runtime")
            .asWidget()
            .size(POWER_PANEL_WIDTH, 18);
    }

    private static IDrawable naniteOverlay(int slot, Supplier<NBTTagCompound> data) {
        return (context, x, y, width, height, theme) -> {
            NBTTagCompound tag = data.get();
            Palette palette = TreeDiagramBoard.palette();
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            if ((tag.getInteger("lockedSlots") & 1 << slot) != 0) {
                GTGuiTextures.OVERLAY_BUTTON_LOCKED.draw(x + 11, y - 1, 8, 8);
            }
            if (slot > 0) {
                int contribution = tag.getInteger("contribution" + slot);
                String text = contribution > 0 ? "+" + contribution : "-";
                GuiDraw.drawText(
                    text,
                    x + 17 - TreeDiagramBoard.width(text),
                    y + 10,
                    1,
                    contribution > 0 ? palette.ok() : palette.dim(),
                    true);
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        };
    }

    private void addParallelControls(Dialog<?> panel, PanelSyncManager sync) {
        IntSyncValue maxParallelSyncer = new IntSyncValue(() -> Math.max(multiblock.getMaxParallelRecipes(), 1));
        BooleanSyncValue alwaysMaxParallelSyncer = new BooleanSyncValue(
            multiblock::isAlwaysMaxParallel,
            multiblock::setAlwaysMaxParallel).allowC2S();
        IntSyncValue powerPanelMaxParallelSyncer = new IntSyncValue(
            multiblock::getTrueParallel,
            multiblock::setPowerPanelMaxParallel).allowC2S();
        sync.syncValue("maxParallel", maxParallelSyncer);
        sync.syncValue("alwaysMaxParallel", alwaysMaxParallelSyncer);
        sync.syncValue("powerPanelMaxParallel", powerPanelMaxParallelSyncer);
        panel.child(
            new TextFieldWidget().value(powerPanelMaxParallelSyncer)
                .setTextAlignment(Alignment.Center)
                .setTextColor(
                    TreeDiagramBoard.palette()
                        .text())
                .numbersInt(
                    () -> alwaysMaxParallelSyncer.getValue() ? maxParallelSyncer.getValue() : 1,
                    maxParallelSyncer::getValue)
                .tooltipBuilder(
                    tooltip -> tooltip.addLine(
                        IKey.dynamic(
                            () -> alwaysMaxParallelSyncer.getValue() ? StatCollector
                                .translateToLocalFormatted("GT5U.gui.text.lockedvalue", maxParallelSyncer.getValue())
                                : StatCollector.translateToLocalFormatted(
                                    "GT5U.gui.text.rangedvalue",
                                    1,
                                    maxParallelSyncer.getValue()))))
                .tooltipShowUpTimer(TOOLTIP_DELAY)
                .background(TreeDiagramBoard.fieldBackground())
                .hoverBackground(TreeDiagramBoard.fieldBackground())
                .pos(13, 159)
                .size(70, 14));
        IDrawable maxButton = (context, x, y, width, height, theme) -> {
            TreeDiagramBoard board = new TreeDiagramBoard(x, y);
            boolean on = alwaysMaxParallelSyncer.getValue();
            board.button(0, 0, width, height, Net.CU, on);
            board.flush();
        };
        panel.child(
            new ButtonWidget<>().pos(99, 157)
                .size(18)
                .background(maxButton)
                .hoverBackground(maxButton)
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

    private static void addMaintenanceTooltips(Dialog<?> panel, Supplier<NBTTagCompound> data) {
        for (int row = 0; row < MAINTENANCE_TIERS.length; row++) {
            int index = row;
            panel.child(
                new Widget<>().pos(157, 118 + row * MAINTENANCE_ROW_HEIGHT)
                    .size(121, 18)
                    .background(IDrawable.EMPTY)
                    .tooltipBuilder(tooltip -> {
                        NBTTagCompound state = data.get();
                        String name = state.getCompoundTag("maintenance" + index)
                            .getString("name");
                        if (name.isEmpty()) return;
                        tooltip.addLine(fluidName(name));
                        tooltip.addLine(maintenanceState(state, index));
                    })
                    .tooltipAutoUpdate(true)
                    .tooltipShowUpTimer(TOOLTIP_DELAY));
        }
    }

    private static String maintenanceState(NBTTagCompound state, int row) {
        NBTTagCompound fluid = state.getCompoundTag("maintenance" + row);
        if (fluid.getBoolean("missing")) return tr("maintenance_missing");
        if (!fluid.getBoolean("needed")) return tr("maintenance_tier", MAINTENANCE_TIERS[row]);
        if (!state.hasKey("parallel")) return tr("maintenance_idle");
        return number((long) Math.ceil(fluid.getDouble("rate"))) + " mB/s";
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

    private static String duration(double seconds) {
        long total = (long) Math.ceil(Math.max(0, seconds));
        long hours = total / 3600, minutes = total % 3600 / 60, rest = total % 60;
        if (hours > 0) return tr("time.hms", hours, minutes, rest);
        return minutes > 0 ? tr("time.ms", minutes, rest) : tr("time.s", rest);
    }

    private static String fluidName(String name) {
        Fluid fluid = FluidRegistry.getFluid(name);
        return fluid == null ? name : fluid.getLocalizedName(new FluidStack(fluid, 1));
    }

    public static class RuntimeBoard implements IDrawable {

        private final Supplier<NBTTagCompound> data;
        private final ItemStackHandler nanites;
        private NBTTagCompound shown;
        private String product = "";

        public RuntimeBoard(Supplier<NBTTagCompound> data, ItemStackHandler nanites) {
            this.data = data;
            this.nanites = nanites;
        }

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme widgetTheme) {
            NBTTagCompound tag = data.get();
            if (tag != shown) {
                shown = tag;
                ItemStack stack = tag.hasKey("product") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("product"))
                    : null;
                product = stack == null ? "" : stack.getDisplayName();
            }
            TreeDiagramBoard board = new TreeDiagramBoard(x, y);
            float phase = Minecraft.getSystemTime() % 2400 / 2400f;
            board.panel(0, 0, width, height);
            drawHeader(board, tag, width);
            drawWiring(board, tag, phase);
            drawBatch(board, tag);
            drawComputation(board, tag);
            drawNanites(board, tag);
            drawMaintenance(board, tag);
            board.text(tr("parallel_limit"), 13, 147, board.palette.muted());
            board.flush();
        }

        private void drawHeader(TreeDiagramBoard board, NBTTagCompound tag, int width) {
            Palette p = board.palette;
            board.text(tr("runtime"), 9, 7, p.text());
            String status = tag.getString("status")
                .toLowerCase(Locale.ROOT);
            if (status.isEmpty()) return;
            String text = tr("state." + status);
            int color = switch (status) {
                case "running" -> p.ok();
                case "idle", "disabled" -> p.muted();
                case "low_computation", "legacy_batch", "output_full" -> p.warn();
                default -> p.bad();
            };
            int right = width - 20;
            board.rect(right - TreeDiagramBoard.width(text) - 7, 9, 4, 4, color);
            board.textRight(text, right, 7, color);
        }

        private void drawWiring(TreeDiagramBoard board, NBTTagCompound tag, float phase) {
            boolean working = tag.hasKey("parallel");
            boolean computing = working && tag.getLong("available") > 0;
            boolean powered = tag.getLong("eu") > 0;
            boolean flowing = working && maintenanceFlowing(tag);
            board.trace(CU_TOP, Net.CU, false);
            board.pad(111, 14, Net.CU, false);
            board.trace(UNLOCK_LEFT, Net.UNLOCK, false);
            board.trace(NANITE_RIGHT, Net.NANITE, false);
            board.trace(FLUID_MIDDLE, Net.FLUID, false);
            board.trace(POWER_MIDDLE, Net.POWER, false);
            board.pad(150, 178, Net.POWER, false);
            board.trace(FLUID_BOTTOM, Net.FLUID, false);
            board.pad(59, 184, Net.FLUID, false);
            board.pad(14, 78, Net.CU, false);
            board.pad(126, 78, Net.NANITE, false);
            board.pad(210, 99, Net.UNLOCK, false);
            board.pad(262, 99, Net.CU, false);
            flow(board, CU_FLOW, Net.CU, computing, phase + 0.3f);
            board.pad(40, 85, Net.CU, computing);
            flow(board, POWER_FLOW, Net.POWER, powered, phase + 0.5f);
            board.pad(100, 85, Net.POWER, powered);
            flow(board, NANITE_FLOW, Net.NANITE, working, phase + 0.4f);
            board.pad(153, 58, Net.NANITE, working);
            flow(board, FLUID_FLOW, Net.FLUID, flowing, phase + 0.2f);
            board.pad(231, 107, Net.FLUID, flowing);
            board.trace(UNLOCK_CORNER, Net.UNLOCK, false);
            board.trace(GRAY_CORNER, Net.GRAY, false);
            board.pad(31, 172, Net.GRAY, false);
            board.trace(CU_CORNER, Net.CU, false);
            board.pad(245, 167, Net.CU, false);
        }

        private static void flow(TreeDiagramBoard board, Trace trace, Net net, boolean active, float packet) {
            if (active) board.trace(trace, net, true, packet);
            else board.trace(trace, net, false);
        }

        private static boolean maintenanceFlowing(NBTTagCompound tag) {
            for (int row = 0; row < MAINTENANCE_TIERS.length; row++) {
                NBTTagCompound fluid = tag.getCompoundTag("maintenance" + row);
                if (fluid.getBoolean("needed") && !fluid.getBoolean("missing")) return true;
            }
            return false;
        }

        private void drawBatch(TreeDiagramBoard board, NBTTagCompound tag) {
            Palette p = board.palette;
            section(board, tr("batch"), 8, 20, 130, 50);
            boolean working = tag.hasKey("parallel");
            if (working) {
                String suffix = " ×" + tag.getInteger("parallel");
                int suffixWidth = TreeDiagramBoard.width(suffix);
                board.textRight(suffix, 133, 23, p.muted());
                board.textFit(product, 133 - suffixWidth, 23, 96 - suffixWidth, p.text(), true);
            } else {
                board.textRight(tr("idle_batch"), 133, 23, p.dim());
            }
            double progress = working ? tag.getDouble("progress") : 0;
            board.litBar(13, 33, 114, progress, Net.WORK, 0, Net.NANITE);
            board.text((int) Math.floor(progress * 100) + "%", 13, 41, working ? p.text() : p.dim());
            if (!working) return;
            board.textRight(tr("remaining", duration(tag.getDouble("seconds"))), 133, 41, p.muted());
            board.runs(
                13,
                55,
                tr("label.loss") + " ",
                p.muted(),
                tag.getInteger("failed"),
                p.bad(),
                "  " + tr("label.bonus") + " ",
                p.muted(),
                tag.getInteger("bonus"),
                p.ok());
            board.textRight(tr("output", number(tag.getLong("output"))), 133, 55, p.text());
        }

        private void drawComputation(TreeDiagramBoard board, NBTTagCompound tag) {
            Palette p = board.palette;
            section(board, tr("computation"), 8, 84, 130, 56);
            boolean working = tag.hasKey("parallel");
            double speed = tag.getDouble("speed");
            double ratio = tag.getDouble("ratio");
            if (working) {
                board.runsRight(
                    133,
                    87,
                    tr("label.speed") + " ",
                    p.muted(),
                    "×" + decimal(speed, 2),
                    speed < 1 ? p.warn() : p.ok());
            }
            board.thickBar(13, 99, 120, 6, ratio / 2, ratio < 1 ? Net.POWER : Net.CU, 0.5, 1.0);
            board.textCenter("1×", 73, 108, p.dim());
            board.textRight("2×", 133, 108, p.dim());
            long available = tag.getLong("available");
            long required = tag.getLong("required");
            board.runs(
                13,
                116,
                number(available),
                available < required ? p.warn() : p.text(),
                " / " + number(required) + " CU",
                p.muted());
            board.runs(
                13,
                128,
                tr("label.power") + " ",
                p.muted(),
                number(tag.getLong("eu")),
                p.text(),
                " EU/t",
                p.muted());
            if (working) {
                double multiplier = tag.getDouble("euMultiplier");
                board.textRight("×" + decimal(multiplier, 2), 133, 128, multiplier > 1 ? p.warn() : p.muted());
            }
        }

        private void drawNanites(TreeDiagramBoard board, NBTTagCompound tag) {
            Palette p = board.palette;
            section(board, tr("nanites"), 152, 20, 130, 72);
            board.runsRight(277, 23, tr("label.parallel") + " ", p.muted(), tag.getInteger("capacity"), p.text());
            board.orbit(NANITE_ORBIT);
            for (int slot = 0; slot < NANITE_SLOTS.length; slot++) {
                board.slot(NANITE_SLOTS[slot][0], NANITE_SLOTS[slot][1], 18, slot == 0 ? p.gold() : 0);
            }
            NBTTagCompound main = tag.getCompoundTag("nanite0");
            int tier = main.getInteger("tier");
            if (tier == 0) {
                board.textRight(tr("no_nanite"), 278, 34, p.dim());
                return;
            }
            ItemStack stack = nanites.getStackInSlot(0);
            String name = (stack == null ? "" : stack.getDisplayName() + " ") + "T" + tier;
            board.textFit(name, 278, 34, 52, p.text(), true);
            double durationMultiplier = main.getDouble("duration");
            double euMultiplier = main.getDouble("eu");
            double loss = main.getDouble("loss");
            double bonus = main.getDouble("bonus");
            line(
                board,
                45,
                tr("label.duration"),
                "×" + decimal(durationMultiplier, 2),
                durationMultiplier < 1 ? p.ok() : p.text());
            line(board, 56, tr("label.power"), "×" + decimal(euMultiplier, 2), euMultiplier < 1 ? p.ok() : p.text());
            line(board, 67, tr("label.loss"), decimal(loss * 100, 0) + "%", loss > 0 ? p.bad() : p.muted());
            line(board, 78, tr("label.bonus"), decimal(bonus * 100, 0) + "%", bonus > 0 ? p.ok() : p.muted());
        }

        private static void line(TreeDiagramBoard board, int y, String key, String value, int color) {
            board.runsRight(278, y, key + " ", board.palette.muted(), value, color);
        }

        private void drawMaintenance(TreeDiagramBoard board, NBTTagCompound tag) {
            Palette p = board.palette;
            section(board, tr("maintenance"), 152, 106, 130, 74);
            board.textFit(tr("supply_together"), 277, 109, 58, p.dim(), true);
            boolean working = tag.hasKey("parallel");
            for (int row = 0; row < MAINTENANCE_TIERS.length; row++) {
                NBTTagCompound fluid = tag.getCompoundTag("maintenance" + row);
                int y = 118 + row * MAINTENANCE_ROW_HEIGHT;
                boolean needed = fluid.getBoolean("needed");
                boolean missing = fluid.getBoolean("missing");
                boolean supplied = needed && !missing && working;
                board.slot(
                    157,
                    y + 2,
                    15,
                    supplied ? p.wire(Net.FLUID)
                        .bright() : 0);
                String name = fluid.getString("name");
                if (name.isEmpty()) continue;
                FluidStack stack = FluidRegistry.getFluidStack(name, 1);
                if (needed && stack != null) {
                    int left = 158, top = y + 3;
                    board.later(
                        () -> GuiDraw.drawFluidTexture(stack, board.left() + left, board.top() + top, 13, 13, 0));
                }
                board.textFit(fluidName(name), 177, y, 100, needed ? p.text() : p.dim(), false);
                int color = missing ? p.bad() : !needed || !working ? p.dim() : p.text();
                board.textFit(maintenanceState(tag, row), 277, y + 9, 100, color, true);
            }
        }

        private static void section(TreeDiagramBoard board, String title, int x, int y, int w, int h) {
            board.screen(x, y, w, h);
            board.text(title, x + 5, y + 3, board.palette.muted());
        }
    }
}
