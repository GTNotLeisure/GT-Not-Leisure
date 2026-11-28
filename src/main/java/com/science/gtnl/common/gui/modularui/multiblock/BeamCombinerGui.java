package com.science.gtnl.common.gui.modularui.multiblock;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.science.gtnl.common.gui.modularui.GTNLMultiBlockBaseGui;
import com.science.gtnl.common.machine.multiblock.BeamCombiner;

import gtnhlanth.common.beamline.Particle;

public class BeamCombinerGui extends GTNLMultiBlockBaseGui<BeamCombiner> {

    private static final String INPUT_COUNT_SYNC_KEY = "beamCombinerInputCount";
    private static final String PARTICLE_SYNC_KEY = "beamCombinerParticle";
    private static final String RATE_SYNC_KEY = "beamCombinerRate";
    private static final String ENERGY_SYNC_KEY = "beamCombinerEnergy";
    private static final String FOCUS_SYNC_KEY = "beamCombinerFocus";
    private static final String BUFFERED_TYPES_SYNC_KEY = "beamCombinerBufferedTypes";
    private static final String BUFFERED_PARTICLES_SYNC_KEY = "beamCombinerBufferedParticles";

    private int activeInputCount;
    private int particleId = -1;
    private int rate;
    private double energy;
    private double focus;
    private int bufferedTypes;
    private long bufferedParticles;

    public BeamCombinerGui(BeamCombiner multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.syncValue(
            INPUT_COUNT_SYNC_KEY,
            new IntSyncValue(multiblock::getActiveInputCount, value -> activeInputCount = value));
        syncManager.syncValue(
            PARTICLE_SYNC_KEY,
            new IntSyncValue(
                () -> multiblock.getCombinedBeam() == null ? -1
                    : multiblock.getCombinedBeam()
                        .getParticleId(),
                value -> particleId = value));
        syncManager.syncValue(
            RATE_SYNC_KEY,
            new IntSyncValue(
                () -> multiblock.getCombinedBeam() == null ? 0
                    : multiblock.getCombinedBeam()
                        .getRate(),
                value -> rate = value));
        syncManager.syncValue(
            ENERGY_SYNC_KEY,
            new DoubleSyncValue(
                () -> multiblock.getCombinedBeam() == null ? 0
                    : multiblock.getCombinedBeam()
                        .getEnergy(),
                value -> energy = value));
        syncManager.syncValue(
            FOCUS_SYNC_KEY,
            new DoubleSyncValue(
                () -> multiblock.getCombinedBeam() == null ? 0
                    : multiblock.getCombinedBeam()
                        .getFocus(),
                value -> focus = value));
        syncManager.syncValue(
            BUFFERED_TYPES_SYNC_KEY,
            new IntSyncValue(multiblock::getBufferedParticleTypeCount, value -> bufferedTypes = value));
        syncManager.syncValue(
            BUFFERED_PARTICLES_SYNC_KEY,
            new LongSyncValue(multiblock::getBufferedParticleCount, value -> bufferedParticles = value));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        return super.createTerminalTextWidget(syncManager, parent)
            .child(
                IKey.dynamic(
                    () -> StatCollector.translateToLocalFormatted("gtnl.gui.beam_combiner.inputs", activeInputCount))
                    .asWidget()
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> StatCollector.translateToLocalFormatted(
                        "gtnl.gui.beam_combiner.particle",
                        particleId < 0 || particleId >= Particle.VALUES.length
                            ? StatCollector.translateToLocal("gtnl.gui.beam_combiner.no_beam")
                            : Particle.VALUES[particleId].getLocalisedName()))
                    .asWidget()
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> StatCollector
                        .translateToLocalFormatted("gtnl.gui.beam_combiner.rate", NumberFormatUtil.formatNumber(rate)))
                    .asWidget()
                    .fullWidth())
            .child(
                IKey.dynamic(() -> StatCollector.translateToLocalFormatted("gtnl.gui.beam_combiner.energy", energy))
                    .asWidget()
                    .fullWidth())
            .child(
                IKey.dynamic(() -> StatCollector.translateToLocalFormatted("gtnl.gui.beam_combiner.focus", focus))
                    .asWidget()
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> StatCollector
                        .translateToLocalFormatted("gtnl.gui.beam_combiner.buffered_types", bufferedTypes))
                    .asWidget()
                    .fullWidth())
            .child(
                IKey.dynamic(
                    () -> StatCollector.translateToLocalFormatted(
                        "gtnl.gui.beam_combiner.buffered_particles",
                        NumberFormatUtil.formatNumber(bufferedParticles)))
                    .asWidget()
                    .fullWidth());
    }
}
