package com.science.gtnl.common.gui.modularui.multiblock;

import static com.science.gtnl.common.machine.multiblock.SmallArcaneAssembler.CENTIVIS_PER_VIS;
import static com.science.gtnl.common.machine.multiblock.SmallArcaneAssembler.PRIMAL_ASPECTS;
import static com.science.gtnl.common.machine.multiblock.SmallArcaneAssembler.VIS_BUFFER_CAPACITY_CV;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.science.gtnl.common.gui.modularui.GTNLMultiBlockBaseGui;
import com.science.gtnl.common.machine.multiblock.SmallArcaneAssembler;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.VoltageIndex;
import thaumcraft.api.aspects.Aspect;

public class SmallArcaneAssemblerGui extends GTNLMultiBlockBaseGui<SmallArcaneAssembler> {

    private static final String WAND_MAX_KEY = "arcaneAssemblerWandMax";
    private static final String WAND_VIS_KEY = "arcaneAssemblerWandVis";
    private static final String BUFFER_VIS_KEY = "arcaneAssemblerBufferVis";
    private static final String CHARGE_STATE_KEY = "arcaneAssemblerChargeState";
    private static final String CHARGE_OVERCLOCKS_KEY = "arcaneAssemblerChargeOverclocks";
    private static final String VIS_NODES_KEY = "arcaneAssemblerVisNodes";
    private static final String DRAINED_CV_KEY = "arcaneAssemblerDrainedCV";
    private static final String DRAIN_TARGET_CV_KEY = "arcaneAssemblerDrainTargetCV";
    private static final int MIN_TEXT_BRIGHTNESS = 0x80;

    public SmallArcaneAssemblerGui(SmallArcaneAssembler multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(WAND_MAX_KEY, new IntSyncValue(multiblock::getWandMaxVisForGui));
        syncManager.syncValue(CHARGE_STATE_KEY, new IntSyncValue(multiblock::getChargeStateForGui));
        syncManager.syncValue(CHARGE_OVERCLOCKS_KEY, new IntSyncValue(multiblock::chargeOverclocks));
        syncManager.syncValue(VIS_NODES_KEY, new IntSyncValue(multiblock::getVisNodeCountForGui));
        syncManager.syncValue(DRAINED_CV_KEY, new IntSyncValue(multiblock::getDrainedCVForGui));
        syncManager.syncValue(DRAIN_TARGET_CV_KEY, new IntSyncValue(multiblock::getDrainTargetCVForGui));
        for (int i = 0; i < PRIMAL_ASPECTS.size(); i++) {
            int aspectIndex = i;
            syncManager.syncValue(WAND_VIS_KEY, i, new IntSyncValue(() -> multiblock.getWandVisForGui(aspectIndex)));
            syncManager
                .syncValue(BUFFER_VIS_KEY, i, new IntSyncValue(() -> multiblock.getBufferedVisForGui(aspectIndex)));
        }
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        IntSyncValue wandMax = syncManager.findSyncHandler(WAND_MAX_KEY, IntSyncValue.class);
        IntSyncValue chargeState = syncManager.findSyncHandler(CHARGE_STATE_KEY, IntSyncValue.class);
        IntSyncValue overclocks = syncManager.findSyncHandler(CHARGE_OVERCLOCKS_KEY, IntSyncValue.class);
        IntSyncValue visNodes = syncManager.findSyncHandler(VIS_NODES_KEY, IntSyncValue.class);
        IntSyncValue drainedCV = syncManager.findSyncHandler(DRAINED_CV_KEY, IntSyncValue.class);
        IntSyncValue drainTargetCV = syncManager.findSyncHandler(DRAIN_TARGET_CV_KEY, IntSyncValue.class);
        ListWidget<IWidget, ?> terminal = super.createTerminalTextWidget(syncManager, parent);
        terminal.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocalFormatted(
                    "gtnl.machine.small_arcane_assembler.gui.charge_state",
                    StatCollector.translateToLocal(
                        "gtnl.machine.small_arcane_assembler.gui.charge_state." + chargeState.getIntValue())))
                .asWidget()
                .fullWidth());
        terminal.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocalFormatted(
                    "gtnl.machine.small_arcane_assembler.gui.charge_info",
                    GTValues.VN[VoltageIndex.LV + overclocks.getIntValue()],
                    1 << overclocks.getIntValue(),
                    visNodes.getIntValue(),
                    drainedCV.getIntValue(),
                    drainTargetCV.getIntValue()))
                .asWidget()
                .fullWidth());
        terminal.child(
            IKey.dynamic(
                () -> StatCollector.translateToLocal(
                    wandMax.getIntValue() > 0 ? "gtnl.machine.small_arcane_assembler.gui.vis_header"
                        : "gtnl.machine.small_arcane_assembler.gui.vis_header_no_wand"))
                .asWidget()
                .fullWidth());
        for (int i = 0; i < PRIMAL_ASPECTS.size(); i++) {
            Aspect aspect = PRIMAL_ASPECTS.get(i);
            IntSyncValue wandVis = syncManager.findSyncHandler(WAND_VIS_KEY, i, IntSyncValue.class);
            IntSyncValue bufferVis = syncManager.findSyncHandler(BUFFER_VIS_KEY, i, IntSyncValue.class);
            terminal.child(
                IKey.dynamic(
                    () -> aspectLine(aspect, wandVis.getIntValue(), wandMax.getIntValue(), bufferVis.getIntValue()))
                    .asWidget()
                    .color(readableColor(aspect.getColor()))
                    .fullWidth());
        }
        return terminal;
    }

    private static String aspectLine(Aspect aspect, int wandCV, int wandMaxCV, int bufferCV) {
        String name = StatCollector
            .translateToLocal("gtnl.machine.small_arcane_assembler.gui.aspect." + aspect.getTag());
        String wand = wandMaxCV > 0 ? formatVis(wandCV) + "/" + wandMaxCV / CENTIVIS_PER_VIS : "-";
        String buffer = formatVis(bufferCV) + "/" + VIS_BUFFER_CAPACITY_CV / CENTIVIS_PER_VIS;
        return StatCollector
            .translateToLocalFormatted("gtnl.machine.small_arcane_assembler.gui.vis_line", name, wand, buffer);
    }

    // Perditio is 0x404040 and would vanish on the dark terminal background.
    private static int readableColor(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        if (Math.max(r, Math.max(g, b)) >= MIN_TEXT_BRIGHTNESS) return rgb;
        return (r + 0xFF) / 2 << 16 | (g + 0xFF) / 2 << 8 | (b + 0xFF) / 2;
    }

    private static String formatVis(int centiVis) {
        return String.format("%.1f", centiVis / (double) CENTIVIS_PER_VIS);
    }
}
