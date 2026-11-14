package com.science.gtnl.common.machine.multiblock.wireless;

import static com.science.gtnl.ScienceNotLeisure.RESOURCE_ROOT_ID;
import static com.science.gtnl.common.machine.multiMachineBase.MultiMachineBase.CustomHatchElement.ParallelCon;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.google.common.collect.ImmutableList;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.util.Vec3Impl;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.api.IConfigurationMaintenance;
import com.science.gtnl.api.IControllerUpgrade;
import com.science.gtnl.api.casing.GTNLCasings;
import com.science.gtnl.common.circuit.CircuitCatalog;
import com.science.gtnl.common.circuit.CircuitFamily;
import com.science.gtnl.common.circuit.CircuitMaintenanceHatches;
import com.science.gtnl.common.circuit.CircuitResourceLedger;
import com.science.gtnl.common.circuit.CircuitResourceLedger.Charge;
import com.science.gtnl.common.circuit.CircuitResourceLedger.FluidCharge;
import com.science.gtnl.common.circuit.CircuitUnlockState;
import com.science.gtnl.common.circuit.CircuitWorkState;
import com.science.gtnl.common.circuit.CircuitWorkStatus;
import com.science.gtnl.common.circuit.computation.CircuitComputationPolicy;
import com.science.gtnl.common.circuit.computation.CircuitComputationSource;
import com.science.gtnl.common.circuit.computation.ComputationEffects;
import com.science.gtnl.common.circuit.computation.DataHatchComputationSource;
import com.science.gtnl.common.circuit.nanite.CircuitNanitePolicy;
import com.science.gtnl.common.circuit.nanite.CircuitNaniteRegistry;
import com.science.gtnl.common.circuit.nanite.NaniteBranch;
import com.science.gtnl.common.circuit.nanite.NaniteEffects;
import com.science.gtnl.common.circuit.nanite.NaniteMaintenancePolicy;
import com.science.gtnl.common.circuit.nanite.NaniteMaintenanceRequirement;
import com.science.gtnl.common.circuit.nanite.NaniteProfile;
import com.science.gtnl.common.circuit.processing.TreeDiagramParallelHelper;
import com.science.gtnl.common.gui.modularui.multiblock.TreeDiagramGui;
import com.science.gtnl.common.machine.multiMachineBase.WirelessEnergyMultiMachineBase;
import com.science.gtnl.common.machine.tooltip.GTNLMultiblockTooltipBuilder;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.utils.StructureUtils;
import com.science.gtnl.utils.enums.GTNLStructureChannels;
import com.science.gtnl.utils.recipes.GTNLOverclockCalculator;
import com.science.gtnl.utils.recipes.GTNLParallelHelper;
import com.science.gtnl.utils.recipes.TransactionalProcessingLogic;
import com.science.gtnl.utils.recipes.data.CircuitRecipeRequirement;
import com.science.gtnl.utils.recipes.metadata.TreeDiagramCircuitMetadata;
import com.science.gtnl.utils.structure.TieredCasing;

import gregtech.api.casing.Casings;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.objects.XSTR;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.FluidEjectionHelper;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.ItemEjectionHelper;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import gregtech.common.misc.WirelessNetworkManager;
import gtPlusPlus.core.material.MaterialsElements;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import tectech.thing.metaTileEntity.hatch.MTEHatchDataInput;

@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public class TreeDiagram extends WirelessEnergyMultiMachineBase<TreeDiagram>
    implements ISurvivalConstructable, IControllerUpgrade {

    private static final TieredCasing<Integer> CELESTIAL_TUNGSTEN_FRAME = new TieredCasing<>(
        (block, meta) -> block == Block.getBlockFromItem(
            MaterialsElements.STANDALONE.CELESTIAL_TUNGSTEN.getFrameBox(1)
                .getItem()) ? 1 : null,
        () -> ImmutableList.of(
            Pair.of(
                Block.getBlockFromItem(
                    MaterialsElements.STANDALONE.CELESTIAL_TUNGSTEN.getFrameBox(1)
                        .getItem()),
                0)),
        -1);

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final String TD_STRUCTURE_FILE_PATH = RESOURCE_ROOT_ID + ":" + "multiblock/tree_diagram";
    private static final String[][] shape = StructureUtils.readStructureFromFile(TD_STRUCTURE_FILE_PATH);
    private static final int HORIZONTAL_OFF_SET = 95;
    private static final int VERTICAL_OFF_SET = 26;
    private static final int DEPTH_OFF_SET = 69;
    private static final String NANITE_SLOTS_NBT_KEY = "treeDiagramNaniteSlots";
    private static final int NANITE_SLOT_COUNT = 4;
    private static final String STATE_KEY = "treeDiagramState";
    private static final int MAX_BATCHES_PER_TICK = 64;
    private static final IHatchElement<TreeDiagram> COMPUTATION_INPUT = new IHatchElement<>() {

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return Collections.singletonList(MTEHatchDataInput.class);
        }

        @Override
        public IGTHatchAdder<? super TreeDiagram> adder() {
            return TreeDiagram::addComputationInput;
        }

        @Override
        public String name() {
            return "TreeDiagramComputation";
        }

        @Override
        public String getDescriptionLangKey() {
            return "gtnl.tree_diagram.hatch.computation";
        }

        @Override
        public long count(TreeDiagram machine) {
            return machine.dataInputHatches.size();
        }
    };

    private final CircuitUnlockState circuitUnlockState = new CircuitUnlockState();
    private final CircuitNanitePolicy nanitePolicy = new CircuitNanitePolicy();
    private final CircuitComputationPolicy computationPolicy = new CircuitComputationPolicy();
    private final CircuitResourceLedger resourceLedger = new CircuitResourceLedger();
    private final CircuitMaintenanceHatches maintenanceHatches = new CircuitMaintenanceHatches(mInputHatches);
    private final List<MTEHatchDataInput> dataInputHatches = new ObjectArrayList<>();
    private final CircuitComputationSource computationSource = new DataHatchComputationSource(dataInputHatches);
    private final NaniteProfile[] naniteProfiles = new NaniteProfile[NANITE_SLOT_COUNT];
    private CircuitNaniteRegistry naniteRegistry;
    private NaniteMaintenancePolicy maintenancePolicy;
    private Vec3Impl computationPosition;
    private CircuitWorkState activeWork;
    private CircuitWorkState preparedWork;
    private NBTTagCompound unreadableWork;
    private ComputationEffects currentComputationEffects = new ComputationEffects(0, 0, 0, 0, 0);
    private CircuitWorkStatus workStatus = CircuitWorkStatus.IDLE;
    private String missingMaintenance = "";
    private long availableComputation;
    private long actualEnergyThisTick;
    private boolean loadingInventories;
    private int clientLockedSlots;

    private final ItemStack[] upgradeItems = new ItemStack[16];
    @Getter
    private final ItemStackHandler upgradeInputSlotHandler = new ItemStackHandler(upgradeItems) {

        @Override
        public int getSlotLimit(int slot) {
            return 524288;
        }

        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return stack == null ? 0 : getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return CircuitCatalog.INSTANCE.unlockFamily(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            markCircuitDirty();
        }
    };

    @Getter
    private final ItemStackHandler naniteSlotHandler = new ItemStackHandler(NANITE_SLOT_COUNT) {

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !isNaniteSlotLocked(slot) && isValidNaniteStack(stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isNaniteSlotLocked(slot) || !isValidNaniteStack(stack) ? stack
                : super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return isNaniteSlotLocked(slot) ? null : super.extractItem(slot, amount, simulate);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            IGregTechTileEntity tile = getBaseMetaTileEntity();
            if (loadingInventories || tile != null && tile.isClientSide() || !isNaniteSlotLocked(slot)) {
                super.setStackInSlot(slot, stack);
            }
        }

        @Override
        protected void onContentsChanged(int slot) {
            if (!loadingInventories) {
                refreshNaniteProfiles();
                markCircuitDirty();
            }
        }
    };

    public TreeDiagram(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public TreeDiagram(String aName) {
        super(aName);
    }

    @Override
    public String getLocalNameKey() {
        return "gtnl.machine.tree_diagram.name";
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity iGregTechTileEntity) {
        return new TreeDiagram(this.mName);
    }

    @Override
    public IStructureDefinition<TreeDiagram> getStructureDefinition() {
        return StructureDefinition.<TreeDiagram>builder()
            .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(shape))
            .addElement(
                'A',
                GTNLStructureChannels.STRUCTURE_RENDER.use(CELESTIAL_TUNGSTEN_FRAME.create((t, m) -> {}, t -> -1)))
            .addElement('B', Casings.LuVMachineCasing.asElement())
            .addElement('C', Casings.DimensionalBridge.asElement())
            .addElement('D', Casings.ActiveNeutroniumCasing.asElement())
            .addElement('E', Casings.NeutroniumStabilizationCasing.asElement())
            .addElement('F', Casings.ExtremeDensitySpaceBendingCasing.asElement())
            .addElement('G', Casings.ContainmentFieldMachineCasing.asElement())
            .addElement('H', Casings.ChemicallyInertMachineCasing.asElement())
            .addElement(
                'I',
                buildHatchAdder(TreeDiagram.class).casingIndex(getCasingTextureID())
                    .hint(1)
                    .atLeast(
                        HatchElement.Maintenance,
                        HatchElement.InputHatch,
                        HatchElement.InputBus,
                        HatchElement.OutputBus,
                        HatchElement.OutputHatch,
                        COMPUTATION_INPUT,
                        HatchElement.Energy.or(HatchElement.ExoticEnergy),
                        ParallelCon)
                    .buildAndChain(
                        StructureUtility
                            .onElementPass(x -> ++x.mCountCasing, Casings.MiningNeutroniumCasing.asElement())))
            .addElement('J', Casings.AdvancedIridiumPlatedMachineCasing.asElement())
            .addElement('K', Casings.RadiantNaquadahAlloyCasing.asElement())
            .addElement('L', Casings.ReinforcedPhotolithographicFrameworkCasing.asElement())
            .addElement('M', Casings.PBIPipeCasing.asElement())
            .addElement('N', Casings.AdvancedFilterCasing.asElement())
            .addElement('O', Casings.HeatResistantTriniumPlatedCasing.asElement())
            .addElement('P', Casings.NaquadriaReinforcedWaterPlantCasing.asElement())
            .addElement('Q', Casings.TeslaBaseCasing.asElement())
            .addElement('R', Casings.UEVMachineCasing.asElement())
            .addElement('S', Casings.MolecularCasing.asElement())
            .addElement('T', Casings.ContainmentFieldGenerator.asElement())
            .addElement('U', Casings.HollowCasing.asElement())
            .addElement('V', Casings.ReinforcedSCTurbineCasing.asElement())
            .addElement('W', GTNLCasings.BlackLamp.asElement())
            .addElement('X', GTNLCasings.LightBlueLamp.asElement())
            .addElement('Y', GTNLCasings.PurpleLamp.asElement())
            .addElement('Z', GTNLCasings.GrayLamp.asElement())
            .addElement('0', GTNLCasings.LightGrayLamp.asElement())
            .addElement('1', GTNLCasings.WhiteLamp.asElement())
            .addElement('2', GTNLCasings.Antifreeze_Heatproof_Machine_Casing.asElement())
            .addElement('3', GTNLCasings.ChemicallyResistantCasing.asElement())
            .addElement('4', GTNLCasings.QuantumComputerCore.asElement())
            .build();
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        if (!GTNLStructureChannels.STRUCTURE_RENDER.hasValue(stackSize)) return;
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (this.mMachine) return -1;
        if (!GTNLStructureChannels.STRUCTURE_RENDER.hasValue(stackSize)) return -1;

        int realBudget = elementBudget >= 500 ? elementBudget : Math.min(500, elementBudget * 5);

        return this.survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFF_SET,
            VERTICAL_OFF_SET,
            DEPTH_OFF_SET,
            realBudget,
            env,
            false,
            true);
    }

    @Override
    public IAlignmentLimits getInitialAlignmentLimits() {
        return (d, r, f) -> d.offsetY == 0;
    }

    @Override
    public void checkMachine(IGregTechTileEntity iGregTechTileEntity, ItemStack aStack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFF_SET, VERTICAL_OFF_SET, DEPTH_OFF_SET, errors)) return;
        setupParameters();
        checkHatch(errors);
        checkCasingMin(errors, mCountCasing, 30);
    }

    @Override
    public ProcessingLogic createProcessingLogic() {
        return new CircuitProcessingLogic().setMaxParallelSupplier(this::getTrueParallel);
    }

    @Override
    public double getEUtDiscount() {
        NaniteProfile main = naniteProfiles[0];
        return Math.max(0.01, (wirelessUpgrade ? 0.5 : 1) - mParallelTier / 50.0)
            * (main == null ? 1 : main.euMultiplier());
    }

    @Override
    public double getDurationModifier() {
        NaniteProfile main = naniteProfiles[0];
        return Math.max(0.01, (wirelessUpgrade ? 0.5 : 1) - Math.max(0, mParallelTier - 1) / 50.0)
            * (main == null ? 1 : main.durationMultiplier());
    }

    @Override
    public int getMaxParallelRecipes() {
        NaniteEffects effects = nanitePolicy.select(naniteProfiles, 1, Integer.MAX_VALUE);
        return effects == null ? 0 : Math.min(effects.maxParallel(), super.getMaxParallelRecipes());
    }

    @Override
    public boolean supportsBatchMode() {
        return false;
    }

    @Override
    public int getItemOutputLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getFluidOutputLimit() {
        return Integer.MAX_VALUE;
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new TreeDiagramGui(this);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTNLRecipeMaps.TreeDiagramRecipes;
    }

    @Override
    public int getCasingTextureID() {
        return Casings.MiningNeutroniumCasing.getTextureId();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.builder()
                    .addIcon(Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.builder()
                    .addIcon(Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        GTNLMultiblockTooltipBuilder tt = new GTNLMultiblockTooltipBuilder();
        tt.addAutoTooltip(this)
            .toolTipFinisher();
        return tt;
    }

    public boolean isValidNaniteStack(ItemStack stack) {
        return naniteRegistry != null && naniteRegistry.recognizes(stack);
    }

    public boolean isNaniteSlotLocked(int slot) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        int mask = tile != null && tile.isClientSide() ? clientLockedSlots
            : activeWork == null ? 0 : activeWork.getLockedSlots();
        return (mask & 1 << slot) != 0;
    }

    public void applyCircuitDisplayData(NBTTagCompound data) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile != null && tile.isClientSide()) clientLockedSlots = data.getInteger("lockedSlots") & 15;
    }

    public void refreshNaniteProfiles() {
        if (naniteRegistry == null) return;
        for (int slot = 0; slot < NANITE_SLOT_COUNT; slot++)
            naniteProfiles[slot] = naniteRegistry.profile(naniteSlotHandler.getStackInSlot(slot));
    }

    private void markCircuitDirty() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (!loadingInventories && tile != null && tile.isServerSide()) tile.markDirty();
    }

    @Override
    public void onFirstTick(IGregTechTileEntity tile) {
        super.onFirstTick(tile);
        naniteRegistry = new CircuitNaniteRegistry(
            tile.getWorld()
                .getSeed());
        maintenancePolicy = new NaniteMaintenancePolicy();
        computationPosition = new Vec3Impl(tile.getXCoord(), tile.getYCoord(), tile.getZCoord());
        refreshNaniteProfiles();
        computationSource.reset();
    }

    public boolean addComputationInput(IGregTechTileEntity tile, int texture) {
        if (tile == null || !(tile.getMetaTileEntity() instanceof MTEHatchDataInput hatch)) return false;
        hatch.updateTexture(texture);
        if (!dataInputHatches.contains(hatch)) dataInputHatches.add(hatch);
        return true;
    }

    @Override
    public void clearHatches() {
        super.clearHatches();
        dataInputHatches.clear();
        computationSource.invalidate();
        availableComputation = 0;
    }

    private void updateComputationReading() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || tile.isClientSide() || computationPosition == null) return;
        availableComputation = computationSource.available(
            tile.getWorld()
                .getTotalWorldTime(),
            computationPosition);
        if (activeWork != null) currentComputationEffects = computationFor(activeWork);
    }

    private ComputationEffects computationFor(CircuitWorkState work) {
        return work.isLegacy() ? new ComputationEffects(availableComputation, 0, 1, 1, 1)
            : computationPolicy.evaluate(availableComputation, work.getRequiredCu());
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        if (activeWork != null) return CheckRecipeResultRegistry.SUCCESSFUL;
        if (unreadableWork != null) return SimpleCheckRecipeResult.ofFailure("tree_diagram_invalid_saved_work");
        workStatus = CircuitWorkStatus.IDLE;
        missingMaintenance = "";
        preparedWork = null;
        maxParallelStored = -1;
        resetParallelTier();
        refreshNaniteProfiles();
        updateComputationReading();
        setupProcessingLogic(processingLogic);
        CheckRecipeResult result = postCheckRecipe(doCheckRecipe(), processingLogic);
        updateSlots();
        if (!result.wasSuccessful()) return result;
        if (preparedWork == null) throw new IllegalStateException("Circuit recipe committed without a work state");
        activeWork = preparedWork;
        preparedWork = null;
        mEfficiency = 10000 - (getIdealStatus() - getRepairStatus()) * 1000;
        mEfficiencyIncrease = 10000;
        mProgresstime = 0;
        mMaxProgresstime = (int) Math.ceil(activeWork.getTotalWork());
        mOutputItems = activeWork.getItemOutputs();
        mOutputFluids = activeWork.getFluidOutputs();
        costingEU = BigInteger.ZERO;
        costingEUText = "0";
        lEUt = 0;
        currentComputationEffects = computationFor(activeWork);
        workStatus = CircuitWorkStatus.RUNNING;
        markCircuitDirty();
        return result;
    }

    @Override
    public void runMachineLifecycle(IGregTechTileEntity tile, long tick) {
        actualEnergyThisTick = 0;
        lEUt = 0;
        updateComputationReading();
        if (unreadableWork != null) {
            workStatus = CircuitWorkStatus.INVALID_SAVED_WORK;
            return;
        }
        if (!mMachine) {
            workStatus = CircuitWorkStatus.STRUCTURE_INCOMPLETE;
            return;
        }
        if (!tile.isAllowedToWork()) {
            workStatus = CircuitWorkStatus.DISABLED;
            return;
        }
        checkMaintenance();
        if (getRepairStatus() <= 0) {
            workStatus = CircuitWorkStatus.NO_REPAIR;
            return;
        }
        mEfficiency = 10000 - (getIdealStatus() - getRepairStatus()) * 1000;
        if (activeWork == null
            && (workStatus == CircuitWorkStatus.DISABLED || workStatus == CircuitWorkStatus.STRUCTURE_INCOMPLETE
                || workStatus == CircuitWorkStatus.NO_REPAIR))
            workStatus = CircuitWorkStatus.IDLE;
        runMachine(tile, tick);
    }

    @Override
    public void runMachine(IGregTechTileEntity tile, long tick) {
        if (activeWork == null) {
            runIdleMachineTick(tile, tick);
        } else if (doRandomMaintenanceDamage()) {
            runActiveMachineTick(tile);
        }
    }

    @Override
    public void runActiveMachineTick(IGregTechTileEntity tile) {
        double remainingTick = 1;
        for (int batches = 0; batches < MAX_BATCHES_PER_TICK && activeWork != null && remainingTick > 0; batches++) {
            if (activeWork.remainingWork() == 0) {
                if (!finishCircuitWork()) return;
                if (!tile.isAllowedToWork() || !tryCheckRecipe()) return;
                continue;
            }
            currentComputationEffects = computationFor(activeWork);
            double rate = activeWork.getBaseWorkRate() * currentComputationEffects.speedMultiplier();
            double timeToFinish = activeWork.remainingWork() / rate;
            double elapsed = Math.min(remainingTick, timeToFinish);
            if (!payForWork(activeWork, currentComputationEffects, elapsed)) return;
            activeWork.advance(elapsed >= timeToFinish ? activeWork.remainingWork() : rate * elapsed);
            remainingTick = Math.max(0, remainingTick - elapsed);
            mProgresstime = (int) Math.min(mMaxProgresstime - 1, activeWork.getCompletedWork());
            workStatus = activeWork.isLegacy() ? CircuitWorkStatus.LEGACY_BATCH
                : currentComputationEffects.supplyRatio() < 1 ? CircuitWorkStatus.LOW_COMPUTATION
                    : CircuitWorkStatus.RUNNING;
            markCircuitDirty();
            if (activeWork.remainingWork() == 0) {
                if (!finishCircuitWork()) return;
                if (!tile.isAllowedToWork() || !tryCheckRecipe()) return;
            }
        }
    }

    private boolean finishCircuitWork() {
        ItemEjectionHelper items = new ItemEjectionHelper(getOutputBusses(), true);
        FluidEjectionHelper fluids = new FluidEjectionHelper(getOutputHatches(mOutputFluids), true);
        if (mOutputItems.length > 0 && items.ejectItems(Arrays.asList(mOutputItems), 1) < 1
            || mOutputFluids.length > 0 && fluids.ejectFluids(Arrays.asList(mOutputFluids), 1) < 1) {
            workStatus = CircuitWorkStatus.OUTPUT_FULL;
            return false;
        }
        items.commit();
        fluids.commit();
        mOutputItems = null;
        mOutputFluids = null;
        activeWork = null;
        workStatus = CircuitWorkStatus.IDLE;
        triggerOutputAfterRecipe();
        finishRecipeProgress(getControllerSlot());
        markCircuitDirty();
        return true;
    }

    private Charge quoteWork(CircuitWorkState work, ComputationEffects computation, double elapsed) {
        double maintenanceSeconds = elapsed / 20
            * Math.max(1, work.getBaseWorkRate())
            * Math.max(1, computation.speedMultiplier());
        int efficiency = work == activeWork ? mEfficiency : 10000 - (getIdealStatus() - getRepairStatus()) * 1000;
        double energy = elapsed * work.getBaseEu()
            * computation.euMultiplier()
            * (10000.0 / Math.max(1000, efficiency));
        return resourceLedger.quote(energy, work.getMaintenance(), maintenanceSeconds);
    }

    private boolean payForWork(CircuitWorkState work, ComputationEffects computation, double elapsed) {
        try {
            Charge charge = quoteWork(work, computation, elapsed);
            if (!canPayEnergy(charge.energy())) {
                workStatus = CircuitWorkStatus.NO_POWER;
                return false;
            }
            if (!canPayMaintenance(charge)) {
                workStatus = CircuitWorkStatus.NO_MAINTENANCE;
                return false;
            }
            for (FluidCharge fluid : charge.fluids()) {
                long remaining = fluid.amount();
                while (remaining > 0) {
                    int requested = (int) Math.min(Integer.MAX_VALUE, remaining);
                    int paid = depleteInputQuantity(
                        new FluidStack(FluidRegistry.getFluid(fluid.fluidName()), requested),
                        false);
                    resourceLedger.prepayFluid(fluid.fluidName(), paid);
                    markCircuitDirty();
                    remaining -= paid;
                    if (paid < requested) {
                        missingMaintenance = fluid.fluidName();
                        workStatus = CircuitWorkStatus.NO_MAINTENANCE;
                        return false;
                    }
                }
            }
            charge = quoteWork(work, computation, elapsed);
            if (!payEnergy(charge.energy())) {
                workStatus = CircuitWorkStatus.NO_POWER;
                return false;
            }
            resourceLedger.commit(charge);
            actualEnergyThisTick = GTUtility.addSafe(actualEnergyThisTick, charge.energy());
            lEUt = -actualEnergyThisTick;
            costingEU = costingEU.add(BigInteger.valueOf(charge.energy()));
            costingEUText = costingEU.toString();
            missingMaintenance = "";
            return true;
        } catch (ArithmeticException e) {
            workStatus = CircuitWorkStatus.CAPACITY_EXCEEDED;
            return false;
        }
    }

    private boolean canPayMaintenance(Charge charge) {
        missingMaintenance = maintenanceHatches.missingFluid(charge.fluids());
        return missingMaintenance.isEmpty();
    }

    private boolean canPayEnergy(long energy) {
        if (energy <= 0) return true;
        if (wirelessMode) return ownerUUID != null && WirelessNetworkManager.getUserEU(ownerUUID)
            .compareTo(BigInteger.valueOf(energy)) >= 0;
        long remaining = energy;
        for (MTEHatch hatch : getExoticAndNormalEnergyHatchList()) {
            if (hatch.isValid()) remaining -= Math.min(
                remaining,
                Math.max(
                    0,
                    hatch.getBaseMetaTileEntity()
                        .getStoredEU()));
            if (remaining == 0) return true;
        }
        return false;
    }

    private boolean payEnergy(long energy) {
        if (energy <= 0) return true;
        if (!canPayEnergy(energy)) return false;
        return wirelessMode ? WirelessNetworkManager.addEUToGlobalEnergyMap(ownerUUID, -energy)
            : drainEnergyInput(energy);
    }

    @Override
    protected long getActualEnergyUsage() {
        return actualEnergyThisTick;
    }

    @Override
    public void updateRuntimeIndicators(IGregTechTileEntity tile) {
        super.updateRuntimeIndicators(tile);
        tile.setActive(actualEnergyThisTick > 0);
        setMufflers(tile.isActive() && mPollution > 0);
    }

    @Override
    public void stopMachine(@NotNull ShutDownReason reason) {
        int previousEfficiency = mEfficiency;
        int previousEfficiencyIncrease = mEfficiencyIncrease;
        if (reason.wasCritical()) {
            activeWork = null;
            unreadableWork = null;
        }
        preparedWork = null;
        super.stopMachine(reason);
        actualEnergyThisTick = 0;
        if (activeWork != null) {
            restoreWorkProgress();
            mEfficiency = previousEfficiency;
            mEfficiencyIncrease = previousEfficiencyIncrease;
        }
        workStatus = CircuitWorkStatus.DISABLED;
        markCircuitDirty();
    }

    @Override
    public ItemStack[] getUpgradeRequiredItems() {
        List<CircuitFamily> families = CircuitCatalog.INSTANCE.families();
        ItemStack[] items = new ItemStack[families.size()];
        for (int i = 0; i < items.length; i++) items[i] = families.get(i)
            .unlockItem()
            .toStack(
                families.get(i)
                    .requiredItems());
        return items;
    }

    @Override
    public String getUpgradeButtonTooltip() {
        return StatCollector.translateToLocal("gtnl.tree_diagram.gui.unlock");
    }

    @Override
    public ItemStack[] getStoredUpgradeWindowItems() {
        ItemStack[] items = new ItemStack[upgradeInputSlotHandler.getSlots()];
        for (int slot = 0; slot < items.length; slot++) items[slot] = upgradeInputSlotHandler.getStackInSlot(slot);
        return items;
    }

    @Override
    public int[] getUpgradePaidCosts() {
        List<CircuitFamily> families = CircuitCatalog.INSTANCE.families();
        int[] paid = new int[families.size()];
        for (int i = 0; i < paid.length; i++) paid[i] = circuitUnlockState.paid(families.get(i));
        return paid;
    }

    @Override
    public boolean isUpgradeConsumed() {
        List<CircuitFamily> families = CircuitCatalog.INSTANCE.families();
        return !families.isEmpty() && families.stream()
            .allMatch(circuitUnlockState::unlocked);
    }

    @Override
    public void setUpgradeConsumed(boolean consumed) {
        // Family payments are authoritative; the compatibility flag cannot unlock circuits.
    }

    @Override
    public boolean tryConsumeItems() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || !tile.isServerSide()) return false;
        for (int slot = 0; slot < upgradeInputSlotHandler.getSlots(); slot++) {
            ItemStack stack = upgradeInputSlotHandler.getStackInSlot(slot);
            CircuitFamily family = CircuitCatalog.INSTANCE.unlockFamily(stack);
            if (family == null || circuitUnlockState.unlocked(family)) continue;
            int requested = Math.min(stack.stackSize, family.requiredItems() - circuitUnlockState.paid(family));
            ItemStack remaining = stack.copy();
            remaining.stackSize -= requested;
            upgradeInputSlotHandler.setStackInSlot(slot, remaining.stackSize > 0 ? remaining : null);
            circuitUnlockState.submit(family, requested);
        }
        markCircuitDirty();
        return isUpgradeConsumed();
    }

    public NBTTagCompound getUnlockDisplayData() {
        return circuitUnlockState.write();
    }

    @Override
    public void saveUpgradeNBTData(NBTTagCompound tag) {
        tag.setTag("treeDiagramUnlocks", circuitUnlockState.write());
        tag.setTag("treeDiagramUpgradeInput", upgradeInputSlotHandler.serializeNBT());
    }

    @Override
    public void loadUpgradeNBTData(NBTTagCompound tag) {
        circuitUnlockState.read(tag.getCompoundTag("treeDiagramUnlocks"));
        loadInventory(upgradeInputSlotHandler, tag.getCompoundTag("treeDiagramUpgradeInput"), upgradeItems.length);
    }

    private void loadInventory(ItemStackHandler handler, NBTTagCompound serialized, int slots) {
        NBTTagCompound fixedSize = (NBTTagCompound) serialized.copy();
        fixedSize.setInteger("Size", slots);
        handler.deserializeNBT(fixedSize);
    }

    @Override
    public void dropStoredUpgradeItems(IGregTechTileEntity tile) {
        // The controller item carries this inventory, including unsubmitted payments.
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        saveCircuitData(tag);
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        saveCircuitData(tag);
    }

    private void saveCircuitData(NBTTagCompound tag) {
        saveUpgradeNBTData(tag);
        tag.setTag(NANITE_SLOTS_NBT_KEY, naniteSlotHandler.serializeNBT());
        NBTTagCompound state = new NBTTagCompound();
        state.setTag("resources", resourceLedger.write());
        if (activeWork != null) state.setTag("work", activeWork.write());
        else if (unreadableWork != null) state.setTag("work", unreadableWork.copy());
        tag.setTag(STATE_KEY, state);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        loadingInventories = true;
        try {
            loadUpgradeNBTData(tag);
            loadInventory(naniteSlotHandler, tag.getCompoundTag(NANITE_SLOTS_NBT_KEY), NANITE_SLOT_COUNT);
        } finally {
            loadingInventories = false;
        }
        NBTTagCompound state = tag.getCompoundTag(STATE_KEY);
        resourceLedger.read(state.getCompoundTag("resources"));
        activeWork = null;
        unreadableWork = null;
        if (state.hasKey("work")) {
            try {
                activeWork = CircuitWorkState.read(state.getCompoundTag("work"));
            } catch (IllegalArgumentException | ArithmeticException e) {
                unreadableWork = (NBTTagCompound) state.getCompoundTag("work")
                    .copy();
                ScienceNotLeisure.LOG.error("Tree Diagram preserved an unreadable saved batch", e);
            }
        } else if (!tag.hasKey(STATE_KEY) && mMaxProgresstime > 0) {
            // Finish an already committed batch with its previous duration and remaining energy cost.
            activeWork = CircuitWorkState.resumeLegacy(
                mMaxProgresstime,
                mProgresstime,
                lEUt < 0 ? Math.negateExact(lEUt) : 0,
                mOutputItems,
                mOutputFluids);
        }
        if (activeWork != null) restoreWorkProgress();
        availableComputation = 0;
        actualEnergyThisTick = 0;
        lEUt = 0;
        currentComputationEffects = new ComputationEffects(0, 0, 0, 0, 0);
        computationSource.reset();
        refreshNaniteProfiles();
    }

    private void restoreWorkProgress() {
        mMaxProgresstime = (int) Math.ceil(activeWork.getTotalWork());
        mProgresstime = (int) Math.min(mMaxProgresstime - 1, activeWork.getCompletedWork());
        mOutputItems = activeWork.getItemOutputs();
        mOutputFluids = activeWork.getFluidOutputs();
    }

    public NBTTagCompound getCircuitDisplayData() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("status", workStatus.name());
        tag.setString("missingFluid", workStatus == CircuitWorkStatus.NO_MAINTENANCE ? missingMaintenance : "");
        tag.setLong("available", availableComputation);
        tag.setLong("required", activeWork == null || activeWork.isLegacy() ? 0 : activeWork.getRequiredCu());
        tag.setLong("eu", actualEnergyThisTick);
        tag.setDouble("speed", activeWork == null ? 0 : currentComputationEffects.speedMultiplier());
        tag.setDouble("ratio", activeWork == null ? 0 : currentComputationEffects.supplyRatio());
        if (activeWork != null) {
            tag.setInteger(
                "parallel",
                activeWork.getOutputRoll()
                    .parallels());
            tag.setInteger(
                "failed",
                activeWork.getOutputRoll()
                    .failedParallels());
            tag.setInteger(
                "bonus",
                activeWork.getOutputRoll()
                    .bonusParallels());
            tag.setDouble(
                "seconds",
                activeWork.remainingWork()
                    / Math.max(0.000001, activeWork.getBaseWorkRate() * currentComputationEffects.speedMultiplier())
                    / 20);
            tag.setInteger("lockedSlots", activeWork.getLockedSlots());
            double multiplier = Math.max(1, activeWork.getBaseWorkRate())
                * Math.max(1, currentComputationEffects.speedMultiplier());
            int index = 0;
            for (NaniteMaintenanceRequirement requirement : activeWork.getMaintenance()) {
                NBTTagCompound fluid = new NBTTagCompound();
                fluid.setString("name", requirement.fluidName());
                fluid.setDouble("rate", requirement.milliBucketsPerSecond() * multiplier);
                tag.setTag("fluid" + index++, fluid);
            }
            tag.setInteger("fluidCount", index);
        }
        for (int slot = 0; slot < NANITE_SLOT_COUNT; slot++) {
            NBTTagCompound nanite = new NBTTagCompound();
            NaniteProfile profile = naniteProfiles[slot];
            if (profile != null) {
                nanite.setInteger("tier", profile.tier());
                nanite.setInteger("parallel", profile.parallel());
                nanite.setString(
                    "branch",
                    profile.branch()
                        .name());
                nanite.setDouble("eu", profile.euMultiplier());
                nanite.setDouble("duration", profile.durationMultiplier());
                nanite.setDouble("bonus", profile.bonusChance());
                double cancellation = NaniteMaintenancePolicy.lossCancellation(profile.tier());
                double rawLoss = profile.branch() == NaniteBranch.STABLE ? cancellation
                    : Math.max(0.30, profile.lossChance());
                nanite.setDouble("rawLoss", rawLoss);
                nanite.setDouble("cancellation", cancellation);
                nanite.setDouble("loss", Math.max(0, rawLoss - cancellation));
            }
            tag.setTag("nanite" + slot, nanite);
        }
        return tag;
    }

    private class CircuitProcessingLogic extends TransactionalProcessingLogic {

        private TreeDiagramParallelHelper helper;
        private NaniteEffects effects;
        private double exactDuration;

        @Override
        public CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
            CircuitRecipeRequirement requirement = recipe.getMetadata(TreeDiagramCircuitMetadata.INSTANCE);
            if (requirement == null) return SimpleCheckRecipeResult.ofFailure("tree_diagram_missing_metadata");
            if (!circuitUnlockState.unlocked(CircuitCatalog.INSTANCE.family(requirement.familyId()))) {
                workStatus = CircuitWorkStatus.LOCKED;
                return SimpleCheckRecipeResult.ofFailure("tree_diagram_locked");
            }
            if (naniteProfiles[0] == null) {
                workStatus = CircuitWorkStatus.NO_MAIN_NANITE;
                return SimpleCheckRecipeResult.ofFailure("tree_diagram_no_main_nanite");
            }
            if (naniteProfiles[0].tier() < requirement.requiredNaniteTier()) {
                workStatus = CircuitWorkStatus.NANITE_TIER;
                return SimpleCheckRecipeResult.ofFailure("tree_diagram_nanite_tier");
            }
            if (wirelessMode && recipe.mEUt > GTValues.V[Math.min(mParallelTier + 1, 14)] * 4) {
                return CheckRecipeResultRegistry.insufficientPower(recipe.mEUt);
            }
            effects = nanitePolicy.select(naniteProfiles, requirement.requiredNaniteTier(), Integer.MAX_VALUE);
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        @Override
        public GTNLParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
            int capacity = Math.min(maxParallel, effects.maxParallel());
            helper = new TreeDiagramParallelHelper(effects, XSTR.XSTR_INSTANCE);
            return helper.setRecipe(recipe)
                .setItemInputs(inputItems)
                .setFluidInputs(inputFluids)
                .setAvailableEUt(GTUtility.mulSafe(availableVoltage, availableAmperage))
                .setMachine(machine, true, true)
                .setRecipeLocked(recipeLockableMachine, isRecipeLocked)
                .setMaxParallel(capacity)
                .setMaxTotalParallel(capacity)
                .setEUtModifier(euModifier)
                .setConsumption(true)
                .setOutputCalculation(true);
        }

        @Override
        public GTNLOverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
            double durationMultiplier = getDurationModifier() * mConfigSpeedBoost;
            double timeReduction = overClockTimeReduction;
            int durationOverclocks = (int) Math.max(
                0,
                Math.floor(Math.log(Math.max(1, recipe.mDuration * durationMultiplier)) / Math.log(timeReduction)));
            return new GTNLOverclockCalculator() {

                @Override
                public double calculateMultiplierUnderOneTick() {
                    return 1;
                }

                @Override
                public void calculateOverclock() {
                    parallel = Math.max(1, currentParallel);
                    super.calculateOverclock();
                }
            }.setRecipeEUt(recipe.mEUt)
                .setEUt(availableVoltage)
                .setAmperage(availableAmperage)
                .setDuration(recipe.mDuration)
                .setDurationModifier(getDurationModifier())
                .setExtraDurationModifier(mConfigSpeedBoost)
                .setEUtDiscount(getEUtDiscount())
                .setAmperageOC(amperageOC)
                .setMaxTierSkips(maxTierSkips)
                .setMaxOverclocks(Math.min(maxOverclocks, durationOverclocks))
                .setDurationDecreasePerOC(timeReduction)
                .setEUtIncreasePerOC(overClockPowerIncrease);
        }

        @Override
        public double calculateDuration(@NotNull GTRecipe recipe, @NotNull GTNLParallelHelper helper,
            @NotNull GTNLOverclockCalculator calculator) {
            exactDuration = recipe.mDuration * calculator.durationModifier
                * calculator.extraDurationModifier
                / Math.pow(calculator.durationDecreasePerOC, calculator.getPerformedOverclocks());
            for (MTEHatchMaintenance hatch : mMaintenanceHatches) {
                if (hatch instanceof IConfigurationMaintenance maintenance && maintenance.isConfiguration()) {
                    exactDuration *= Math.max(0.01, maintenance.getConfigTime() / 100.0);
                    break;
                }
            }
            exactDuration = Math.max(1, exactDuration);
            return Math.ceil(exactDuration);
        }

        @Override
        public CheckRecipeResult onRecipeStart(@NotNull GTRecipe recipe) {
            CircuitRecipeRequirement requirement = recipe.getMetadata(TreeDiagramCircuitMetadata.INSTANCE);
            NaniteEffects participation = nanitePolicy
                .select(naniteProfiles, requirement.requiredNaniteTier(), calculatedParallels);
            try {
                double rate = recipe.mDuration / exactDuration;
                CircuitWorkState work = new CircuitWorkState(
                    requirement.familyId(),
                    requirement.circuitTier(),
                    recipe.mDuration,
                    rate,
                    calculatedEut,
                    computationPolicy.required(requirement.baseComputation(), calculatedParallels, rate),
                    participation.lockedSlots(),
                    maintenancePolicy.requirements(participation),
                    helper.outputRoll(),
                    helper.getItemOutputs(),
                    helper.getFluidOutputs());
                ComputationEffects computation = computationPolicy.evaluate(availableComputation, work.getRequiredCu());
                Charge charge = quoteWork(
                    work,
                    computation,
                    Math.min(1, exactDuration / computation.speedMultiplier()));
                if (!canPayMaintenance(charge)) {
                    workStatus = CircuitWorkStatus.NO_MAINTENANCE;
                    return SimpleCheckRecipeResult.ofFailure("tree_diagram_maintenance");
                }
                if (!canPayEnergy(charge.energy())) {
                    workStatus = CircuitWorkStatus.NO_POWER;
                    return CheckRecipeResultRegistry.insufficientPower(charge.energy());
                }
                preparedWork = work;
                return CheckRecipeResultRegistry.SUCCESSFUL;
            } catch (ArithmeticException e) {
                return CheckRecipeResultRegistry.POWER_OVERFLOW;
            }
        }
    }
}
