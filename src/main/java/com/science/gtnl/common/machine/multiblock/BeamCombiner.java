package com.science.gtnl.common.machine.multiblock;

import static com.science.gtnl.ScienceNotLeisure.RESOURCE_ROOT_ID;
import static gregtech.api.enums.MetaTileEntityIDs.HATCH_BEAMLINE_INPUT;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainAllGlasses;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.science.gtnl.common.beamline.BeamBuffer;
import com.science.gtnl.common.gui.modularui.multiblock.BeamCombinerGui;
import com.science.gtnl.common.machine.tooltip.GTNLMultiblockTooltipBuilder;
import com.science.gtnl.utils.StructureUtils;
import com.science.gtnl.utils.enums.BlockIcons;

import gregtech.api.casing.Casings;
import gregtech.api.enums.TickTime;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.tileentities.machines.multi.beamcrafting.MTEBeamMultiBase;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.BeamLinePacket;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;
import gtnhlanth.common.hatch.MTEHatchOutputBeamline;

@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public class BeamCombiner extends MTEBeamMultiBase<BeamCombiner> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final String STRUCTURE_FILE_PATH = RESOURCE_ROOT_ID + ":multiblock/beam_combiner";
    private static final String[][] shape = StructureUtils.readStructureFromFile(STRUCTURE_FILE_PATH);
    private static final int HORIZONTAL_OFF_SET = 4;
    private static final int VERTICAL_OFF_SET = 2;
    private static final int DEPTH_OFF_SET = 0;
    private static final int INPUT_COUNT = 4;
    private static final int CASING_TEXTURE_ID = Casings.ShieldedAcceleratorCasing.getTextureId();

    private final BeamBuffer beamBuffer = new BeamBuffer();
    private BeamLinePacket outputPacket;
    private BeamInformation combinedBeam;
    private int activeInputCount;

    public BeamCombiner(String aName) {
        super(aName);
    }

    public BeamCombiner(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Override
    public String getLocalNameKey() {
        return "gtnl.machine.beam_combiner.name";
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new BeamCombiner(this.mName);
    }

    @Override
    public IStructureDefinition<BeamCombiner> getStructureDefinition() {
        return StructureDefinition.<BeamCombiner>builder()
            .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(shape))
            .addElement('A', chainAllGlasses())
            .addElement('B', Casings.ShieldedAcceleratorCasing.asElement())
            .addElement('C', buildBeamlineOutputHatch(BeamCombiner.class, CASING_TEXTURE_ID, 1))
            .addElement(
                'D',
                buildHatchAdder(BeamCombiner.class).hatchId(HATCH_BEAMLINE_INPUT.ID)
                    .casingIndex(CASING_TEXTURE_ID)
                    .hint(2)
                    .adder(BeamCombiner::addBeamLineInputHatch)
                    .exclusive()
                    .build())
            .addElement('E', Casings.GrateMachineCasing.asElement())
            .build();
    }

    @Override
    public boolean addBeamLineInputHatch(IGregTechTileEntity tileEntity, int casingIndex) {
        if (!super.addBeamLineInputHatch(tileEntity, casingIndex)) return false;
        mInputBeamline.get(mInputBeamline.size() - 1)
            .updateTexture(casingIndex);
        return true;
    }

    @Override
    public boolean addBeamLineOutputHatch(IGregTechTileEntity tileEntity, int casingIndex) {
        if (!super.addBeamLineOutputHatch(tileEntity, casingIndex)) return false;
        mOutputBeamline.get(mOutputBeamline.size() - 1)
            .updateTexture(casingIndex);
        return true;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing() {
        combinedBeam = null;
        activeInputCount = 0;
        if (mInputBeamline.size() != INPUT_COUNT || mOutputBeamline.size() != 1) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        MTEHatchOutputBeamline output = mOutputBeamline.get(0);
        if (!output.isValid()) return CheckRecipeResultRegistry.NO_RECIPE;
        for (MTEHatchInputBeamline hatch : mInputBeamline) {
            if (!hatch.isValid() || hatch.dataPacket == null) continue;
            if (beamBuffer.add(hatch.dataPacket.getContent())) activeInputCount++;
        }
        if (output.dataPacket == null) {
            BeamInformation beam = beamBuffer.poll();
            if (beam == null) return CheckRecipeResultRegistry.NO_RECIPE;
            outputPacket = new BeamLinePacket(beam);
            output.dataPacket = outputPacket;
            output.markDirty();
        }
        combinedBeam = output.dataPacket.getContent();
        mMaxProgresstime = TickTime.SECOND;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        lEUt = 0;
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void stopMachine(@NotNull ShutDownReason reason) {
        clearOutputBeam();
        super.stopMachine(reason);
    }

    private void clearOutputBeam() {
        combinedBeam = null;
        activeInputCount = 0;
        for (MTEHatchOutputBeamline hatch : mOutputBeamline) {
            if (outputPacket != null && hatch.dataPacket == outputPacket) {
                beamBuffer.add(outputPacket.getContent());
                hatch.dataPacket = null;
                hatch.markDirty();
            }
        }
        outputPacket = null;
    }

    @Override
    public void clearHatches() {
        clearOutputBeam();
        super.clearHatches();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setTag("BeamBuffer", beamBuffer.writeToNBT());
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        beamBuffer.readFromNBT(aNBT.getCompoundTag("BeamBuffer"));
    }

    public int getBufferedParticleTypeCount() {
        return beamBuffer.getParticleTypeCount();
    }

    public long getBufferedParticleCount() {
        return beamBuffer.getParticleCount();
    }

    public BeamInformation getCombinedBeam() {
        return combinedBeam;
    }

    public int getActiveInputCount() {
        return activeInputCount;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            return new ITexture[] { Casings.ShieldedAcceleratorCasing.getCasingTexture(), TextureFactory.builder()
                .addIcon(
                    aActive ? BlockIcons.OVERLAY_FRONT_BEAM_COMBINER_ACTIVE : BlockIcons.OVERLAY_FRONT_BEAM_COMBINER)
                .extFacing()
                .build() };
        }
        return new ITexture[] { Casings.ShieldedAcceleratorCasing.getCasingTexture() };
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        GTNLMultiblockTooltipBuilder tt = new GTNLMultiblockTooltipBuilder();
        tt.addAutoTooltip(this)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public boolean showRecipeTextInGUI() {
        return false;
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new BeamCombinerGui(this);
    }
}
