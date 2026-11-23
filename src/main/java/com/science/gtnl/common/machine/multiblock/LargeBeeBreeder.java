package com.science.gtnl.common.machine.multiblock;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.error;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofChain;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.science.gtnl.ScienceNotLeisure.RESOURCE_ROOT_ID;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_DISTILLATION_TOWER_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainAllGlasses;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.science.gtnl.api.casing.GTNLCasings;
import com.science.gtnl.common.gui.modularui.LargeBeeBreederGui;
import com.science.gtnl.common.machine.multiMachineBase.MultiMachineBase;
import com.science.gtnl.common.machine.tooltip.GTNLMultiblockTooltipBuilder;
import com.science.gtnl.utils.StructureUtils;
import com.science.gtnl.utils.enums.ModsItemlist;
import com.science.gtnl.utils.machine.bee.BeeBreedingHelper;
import com.science.gtnl.utils.machine.bee.BeeBreedingHelper.BreedingStep;
import com.science.gtnl.utils.machine.bee.BeeBreedingHelper.FoundationRequirement;
import com.science.gtnl.utils.machine.bee.BeeBreedingHelper.MutationData;
import com.science.gtnl.utils.machine.bee.BeeBreedingInputs;
import com.science.gtnl.utils.machine.bee.BeeBreedingInputs.ParentPair;
import com.science.gtnl.utils.machine.bee.BeeBreedingPlanner;
import com.science.gtnl.utils.machine.bee.BeeBreedingPlanner.Plan;
import com.science.gtnl.utils.machine.bee.BeeBreedingTiers;
import com.science.gtnl.utils.machine.bee.BeeSpeciesArchive;
import com.science.gtnl.utils.recipes.GTNLOverclockCalculator;

import forestry.api.apiculture.IAlleleBeeSpecies;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.ItemEjectionHelper;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import lombok.Getter;

@IMetaTileEntity.SkipGenerateDescription
@IMetaTileEntity.SkipGenerateName
public class LargeBeeBreeder extends MultiMachineBase<LargeBeeBreeder> {

    public static final double DRONE_CONSUMPTION_CHANCE = 0.1D;
    private static final int BEE_DATA_VERSION = 3;
    private static final long BASE_EUT_PER_BREEDING = 512L;
    private static final int BASE_VOLTAGE_TIER = VoltageIndex.HV;
    private static final int BASE_GLASS_TIER = VoltageIndex.HV;
    private static final int CHANCE_PER_GLASS_TIER = 2;

    private static final int TICKS_PER_BREEDING = 32 * 20;
    private static final int INPUT_SCAN_INTERVAL = 100;
    private static final int FOUNDATION_SCAN_INTERVAL = 200;
    private static final int BASE_PARALLEL_STEPS = 1;
    private static final int ROYAL_JELLY_PER_PARALLEL = 2;

    private static final byte OPERATION_NONE = 0;
    private static final byte OPERATION_BREEDING = 1;
    private static final byte OPERATION_OUTPUT = 2;
    private static final byte OPERATION_FLUSH = 3;

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final String STRUCTURE_FILE_PATH = RESOURCE_ROOT_ID + ":multiblock/large_bee_breeder";
    private static final int HORIZONTAL_OFFSET = 9;
    private static final int VERTICAL_OFFSET = 9;
    private static final int DEPTH_OFFSET = 3;
    private static final String[][] shape = StructureUtils.readStructureFromFile(STRUCTURE_FILE_PATH);

    private static int getParallelForVoltageTier(int voltageTier) {
        int upgrades = Math.max(0, Math.min(voltageTier, VoltageIndex.MAX) - BASE_VOLTAGE_TIER);
        return BASE_PARALLEL_STEPS << upgrades;
    }

    public static int getSpeciesVoltageTier(String species) {
        return BeeBreedingTiers.getVoltageTier(species);
    }

    private IStructureDefinition<LargeBeeBreeder> STRUCTURE_DEFINITION;

    @Getter
    private String targetBeeSpecies = "";

    @Getter
    private BeeSpeciesArchive speciesArchive = new BeeSpeciesArchive();

    private Plan breedingPlan = Plan.empty("");
    private List<BreedingStep> breedingChain = new ArrayList<>();
    private boolean breedingPlanDirty = true;

    private byte activeOperation = OPERATION_NONE;
    private List<BreedingAttempt> activeBreedingBatch = new ArrayList<>();
    private final List<ItemStack> pendingBeeOutputs = new ArrayList<>();
    private int legacyPrincessReturns;
    private Set<String> availableDroneSpecies = Set.of();
    private Set<String> availableQueenSpecies = Set.of();
    private final List<ItemStack> foundationInputs = new ArrayList<>();
    private final Set<Fluid> foundationFluids = new HashSet<>();
    private final Map<String, ItemStack> foundationMatches = new HashMap<>();
    private long lastFoundationScanTick = Long.MIN_VALUE;
    private List<FoundationRequirement> missingFoundations = Collections.emptyList();
    private boolean activeFoundationsChecked;
    private int activeRequiredVoltageTier = BASE_VOLTAGE_TIER;

    @Getter
    private int availableQueenCount = 0;

    @Getter
    private String missingDroneSpecies = "";

    @Getter
    private boolean allTasksBlocked = false;

    @Getter
    private int chainTotalSteps = 0;

    @Getter
    private int chainCompletedSteps = 0;

    @Getter
    private boolean targetInputValid = true;

    @Getter
    private int breedingRevision;

    public LargeBeeBreeder(String aName) {
        super(aName);
    }

    public LargeBeeBreeder(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new LargeBeeBreeder(this.mName);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        if (aBaseMetaTileEntity.isServerSide() && mMachine && isFoundationScanDue()) {
            startRecipeProcessing();
            try {
                refreshFoundationInputs(getStoredInputs());
            } finally {
                endRecipeProcessing();
            }
            refreshBreedingPlan();
        }
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity.isServerSide() && aTick % INPUT_SCAN_INTERVAL == 0) {
            if (mMachine) {
                startRecipeProcessing();
                try {
                    scanInputBuses();
                } finally {
                    endRecipeProcessing();
                }
            }
            refreshBreedingPlan();
        }
    }

    @Override
    public IAlignmentLimits getInitialAlignmentLimits() {
        return (direction, rotation, flip) -> direction == ForgeDirection.UP;
    }

    @Override
    public IStructureDefinition<LargeBeeBreeder> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<LargeBeeBreeder>builder()
                .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(shape))
                .addElement('A', chainAllGlasses(-1, (te, t) -> te.mGlassTier = t, te -> te.mGlassTier))
                .addElement('B', ofStructureBlock(ModsItemlist.ChiselYellowHempcrete))
                .addElement('C', ofStructureBlock(ModsItemlist.ChiselBrownHempcrete))
                .addElement('D', ofStructureBlock(ModsItemlist.ChiselBlackHempcrete))
                .addElement('E', ofStructureBlock(ModsItemlist.ChiselOrangeWoolenClay))
                .addElement('F', ofStructureBlock(ModsItemlist.ChiselBrownWoolenClay))
                .addElement('G', ofStructureBlock(ModsItemlist.EtFuturumRequiemOrangeConcrete))
                .addElement(
                    'H',
                    ofChain(
                        buildHatchAdder(LargeBeeBreeder.class)
                            .atLeast(InputBus, OutputBus, InputHatch, Energy.or(ExoticEnergy), Maintenance)
                            .casingIndex(getCasingTextureID())
                            .hint(1)
                            .buildAndChain(),
                        onElementPass(
                            x -> ++x.mCountCasing,
                            ofStructureBlock(ModsItemlist.EtFuturumRequiemYellowConcrete))))
                .addElement('I', ofStructureBlock(ModsItemlist.EtFuturumRequiemBrownConcrete))
                .addElement('J', ofStructureBlock(ModsItemlist.ExtraUtilitiesLightBlueLapisCaelestis))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private IStructureElement<LargeBeeBreeder> ofStructureBlock(ModsItemlist material) {
        ItemStack stack = material.get(1);
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return error();
        return ofBlock(Block.getBlockFromItem(stack.getItem()), stack.getItemDamage());
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        this.buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (this.mMachine) return -1;
        return this.survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            HORIZONTAL_OFFSET,
            VERTICAL_OFFSET,
            DEPTH_OFFSET,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack, List<StructureError> errors) {
        mCountCasing = 0;
        mGlassTier = -1;
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;
        setupParameters();
        checkHatch(errors);
        checkCasingMin(errors, mCountCasing, 10);
        checkHatchMin(errors, InputBus, 1);
        checkHatchMin(errors, OutputBus, 1);
        checkHatchMin(errors, Energy.or(ExoticEnergy), 1);
        if (shouldCheckMaintenance()) checkHatchMin(errors, Maintenance, 1);

        markBreedingChanged();
    }

    @Override
    public int checkEnergyHatchTier() {
        int tier = VoltageIndex.ULV;
        for (MTEHatch hatch : GTUtility.validMTEList(mEnergyHatches)) {
            tier = Math.max(tier, (int) hatch.getInputTier());
        }
        for (MTEHatch hatch : GTUtility.validMTEList(mExoticEnergyHatches)) {
            tier = Math.max(tier, (int) hatch.getInputTier());
        }
        return tier;
    }

    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        List<ItemStack> inputs = getStoredInputs();
        BeeBreedingInputs parents = scanInputBuses(inputs);
        activeOperation = OPERATION_NONE;
        activeBreedingBatch.clear();
        activeFoundationsChecked = false;
        mOutputItems = null;
        queueLegacyPrincessOutputs();
        if (!pendingBeeOutputs.isEmpty()) return preparePendingBeeOutputs();
        refreshBreedingPlan();
        if (targetBeeSpecies.isEmpty() || parents.getQueenCount() == 0) return CheckRecipeResultRegistry.NO_RECIPE;
        if (getBreedingVoltageTier() < BASE_VOLTAGE_TIER) return insufficientBreedingVoltage(BASE_VOLTAGE_TIER);
        long eutPerAttempt = getEuPerBreedingAttempt();
        if (getMaxInputPower() < eutPerAttempt) return CheckRecipeResultRegistry.insufficientPower(eutPerAttempt);
        boolean reproduction = speciesArchive.hasSpecies(targetBeeSpecies);
        List<BreedingStep> ready = reproduction
            ? List.of(new BreedingStep(targetBeeSpecies, targetBeeSpecies, targetBeeSpecies, 100D))
            : breedingChain.stream()
                .filter(step -> !speciesArchive.hasSpecies(step.result))
                .toList();
        if (ready.isEmpty()) return missingParents(breedingPlan.getFirstMissingSpecies());
        int limit = (int) Math.min(getMaxParallelBreedingSteps(), getMaxInputPower() / eutPerAttempt);
        List<BreedingAttempt> batch = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        List<BreedingStep> blockedFoundations = new ArrayList<>();
        int voltageTier = getBreedingVoltageTier();
        int requiredTier = Integer.MAX_VALUE;
        String missingParent = "";
        for (BreedingStep planned : ready) {
            if (batch.size() >= limit) break;
            requiredTier = Math.min(requiredTier, getSpeciesVoltageTier(planned.result));
            if (voltageTier < getSpeciesVoltageTier(planned.result)) continue;
            BreedingStep step = selectParentStep(planned, parents, reproduction);
            ParentPair pair = parents.findPair(step.parent1, step.parent2);
            if (pair == null) {
                missingParent = step.parent1 + " + " + step.parent2;
                continue;
            }
            boolean foundationReady = true;
            for (FoundationRequirement foundation : step.foundations) {
                if (findFoundation(foundation) == null) foundationReady = false;
            }
            if (!foundationReady) {
                blockedFoundations.add(step);
                continue;
            }
            ItemStack princess = BeeBreedingHelper.createPrincess(step.result);
            ItemStack childDrone = BeeBreedingHelper.createDrone(step.result);
            if (princess == null || childDrone == null) continue;
            double chance = Math.min(100D, step.chance + getMutationChanceBonus());
            boolean succeeded = reproduction || BeeBreedingHelper.tryMutation(chance);
            boolean consumeDrone = succeeded && BeeBreedingHelper.tryMutation(DRONE_CONSUMPTION_CHANCE);
            ItemStack queen = pair.queen()
                .copy();
            queen.stackSize = 1;
            ItemStack consumedDrone = consumeDrone ? pair.drone()
                .copy() : null;
            if (consumedDrone != null) consumedDrone.stackSize = 1;
            batch.add(new BreedingAttempt(step, chance, succeeded, queen, consumedDrone));
            if (succeeded) {
                outputs.add(princess);
                outputs.add(childDrone);
            } else outputs.add(queen.copy());
            parents.reserve(pair, consumeDrone);
        }
        if (batch.isEmpty()) {
            if (requiredTier != Integer.MAX_VALUE && voltageTier < requiredTier)
                return insufficientBreedingVoltage(requiredTier);
            updateMissingFoundations(blockedFoundations);
            if (!missingFoundations.isEmpty())
                return SimpleCheckRecipeResult.ofFailure("gtnl.bee_breeder_missing_foundation");
            return missingParents(missingParent.isEmpty() ? targetBeeSpecies : missingParent);
        }
        ItemStack[] result = outputs.toArray(new ItemStack[0]);
        if (!canOutputAll(result)) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        parents.commit();
        updateAvailableParents(parents);
        availableQueenCount = parents.getQueenCount();
        updateSlots();
        mOutputItems = result;
        activeBreedingBatch = batch;
        for (BreedingAttempt attempt : batch) {
            breedingChain.replaceAll(old -> old.result.equals(attempt.step.result) ? attempt.step : old);
        }
        activeOperation = reproduction ? OPERATION_OUTPUT : OPERATION_BREEDING;
        activeFoundationsChecked = true;
        updateMissingFoundations(
            batch.stream()
                .map(attempt -> attempt.step)
                .toList());
        allTasksBlocked = false;
        missingDroneSpecies = "";
        prepareBreedingCycle(batch.size(), inputs);
        markBaseTileDirty();
        markBreedingChanged();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private void queueLegacyPrincessOutputs() {
        if (legacyPrincessReturns <= 0 || !pendingBeeOutputs.isEmpty()) return;
        String species = speciesArchive.hasSpecies(targetBeeSpecies) ? targetBeeSpecies
            : speciesArchive.getSpecies()
                .stream()
                .findFirst()
                .orElse("");
        ItemStack princess = BeeBreedingHelper.createPrincess(species);
        if (princess == null) return;
        princess.stackSize = Math.min(64, legacyPrincessReturns);
        pendingBeeOutputs.add(princess);
        legacyPrincessReturns -= princess.stackSize;
        markBaseTileDirty();
        markBreedingChanged();
    }

    private CheckRecipeResult preparePendingBeeOutputs() {
        ItemEjectionHelper capacity = new ItemEjectionHelper(this);
        List<ItemStack> outputs = new ArrayList<>();
        for (var iterator = pendingBeeOutputs.iterator(); iterator.hasNext();) {
            ItemStack pending = iterator.next();
            ItemStack output = pending.copy();
            int amount = capacity.ejectStack(output);
            if (amount == 0) continue;
            output.stackSize = amount;
            outputs.add(output);
            pending.stackSize -= amount;
            if (pending.stackSize == 0) iterator.remove();
        }
        if (outputs.isEmpty()) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        mOutputItems = outputs.toArray(new ItemStack[0]);
        activeOperation = OPERATION_FLUSH;
        mMaxProgresstime = 1;
        mEfficiency = 10000;
        lEUt = 0;
        markBaseTileDirty();
        markBreedingChanged();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private BreedingStep selectParentStep(BreedingStep planned, BeeBreedingInputs parents, boolean reproduction) {
        if (reproduction) return planned;
        BreedingStep selected = planned;
        boolean selectedReady = parents.findPair(planned.parent1, planned.parent2) != null
            && planned.foundations.stream()
                .allMatch(foundation -> findFoundation(foundation) != null);
        for (MutationData mutation : BeeBreedingHelper.getMutationsForUID(planned.result)) {
            if (parents.findPair(mutation.parent1, mutation.parent2) == null || mutation.foundations.stream()
                .anyMatch(foundation -> findFoundation(foundation) == null)) continue;
            if (!selectedReady || mutation.chance > selected.chance) {
                selected = new BreedingStep(
                    mutation.parent1,
                    mutation.parent2,
                    mutation.result,
                    mutation.chance,
                    mutation.foundations);
                selectedReady = true;
            }
        }
        return selected;
    }

    private CheckRecipeResult missingParents(String species) {
        allTasksBlocked = true;
        missingDroneSpecies = species;
        markBreedingChanged();
        return SimpleCheckRecipeResult.ofFailure("gtnl.bee_breeder_missing_parents");
    }

    private CheckRecipeResult insufficientBreedingVoltage(int requiredTier) {
        allTasksBlocked = true;
        missingDroneSpecies = "";
        return CheckRecipeResultRegistry.insufficientVoltage(GTValues.V[requiredTier]);
    }

    private void prepareBreedingCycle(long eut) {
        updateActiveVoltageTier();
        mMaxProgresstime = createBreedingOverclockCalculator().getDuration();
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        lEUt = -eut;
    }

    private void prepareBreedingCycle(int parallels, List<ItemStack> inputs) {
        long eut = getEnergyCostPerTick(parallels);
        prepareBreedingCycle(eut);
        if (mMaxProgresstime <= 1 || eut > Math.max(0L, getMaxInputPower()) / 4) return;

        ItemStack royalJelly = ModsItemlist.ForestryRoyalJelly.get(1);
        if (royalJelly == null) return;
        int required = parallels * ROYAL_JELLY_PER_PARALLEL;
        int available = 0;
        List<ItemStack> jellyStacks = new ArrayList<>();
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ItemStack input : inputs) {
            if (input == null || input.stackSize <= 0
                || !seen.add(input)
                || !GTUtility.areStacksEqual(input, royalJelly)) continue;
            jellyStacks.add(input);
            available += Math.min(input.stackSize, required - available);
            if (available == required) break;
        }
        if (available < required) return;

        for (ItemStack jelly : jellyStacks) {
            int consumed = Math.min(jelly.stackSize, required);
            jelly.stackSize -= consumed;
            required -= consumed;
            if (required == 0) break;
        }
        mMaxProgresstime = createBreedingOverclockCalculator(1).getDuration();
        lEUt = -(eut * 4);
        updateSlots();
        markBaseTileDirty();
    }

    private void updateActiveVoltageTier() {
        activeRequiredVoltageTier = BASE_VOLTAGE_TIER;
        if (activeOperation == OPERATION_OUTPUT) {
            String outputSpecies = mOutputItems != null && mOutputItems.length > 0
                ? BeeBreedingHelper.getBeeUID(mOutputItems[0])
                : targetBeeSpecies;
            activeRequiredVoltageTier = getSpeciesVoltageTier(outputSpecies);
        }
        for (BreedingAttempt attempt : activeBreedingBatch) {
            activeRequiredVoltageTier = Math.max(activeRequiredVoltageTier, getSpeciesVoltageTier(attempt.step.result));
        }
    }

    @Override
    public boolean onRunningTick(ItemStack stack) {
        if (activeOperation == OPERATION_FLUSH) return true;
        if (getBreedingVoltageTier() < activeRequiredVoltageTier) {
            checkRecipeResult = insufficientBreedingVoltage(activeRequiredVoltageTier);
            return false;
        }
        if (activeOperation == OPERATION_BREEDING) {
            if (!activeFoundationsChecked) {
                List<BreedingStep> active = new ArrayList<>(activeBreedingBatch.size());
                for (BreedingAttempt attempt : activeBreedingBatch) active.add(attempt.step);
                updateMissingFoundations(active);
                activeFoundationsChecked = true;
            }
            if (!missingFoundations.isEmpty()) {
                checkRecipeResult = SimpleCheckRecipeResult.ofFailure("gtnl.bee_breeder_missing_foundation");
                return false;
            }
        }
        checkRecipeResult = CheckRecipeResultRegistry.SUCCESSFUL;
        allTasksBlocked = false;
        return super.onRunningTick(stack);
    }

    @Override
    public void outputAfterRecipe() {
        super.outputAfterRecipe();
        settleActiveOperation();
        markBaseTileDirty();
    }

    private void settleActiveOperation() {
        if (activeOperation == OPERATION_NONE) return;

        boolean archiveChanged = false;
        for (BreedingAttempt attempt : activeBreedingBatch) {
            if (attempt.succeeded) archiveChanged |= speciesArchive.unlockSpecies(attempt.step.result);
        }

        activeOperation = OPERATION_NONE;
        activeBreedingBatch.clear();
        if (archiveChanged) invalidateBreedingPlan();
        markBreedingChanged();
    }

    public void setTargetBeeSpecies(String species) {

        String candidate = species == null ? "" : species.trim();
        if (!candidate.isEmpty()) {
            IAlleleBeeSpecies resolved = BeeBreedingHelper.getSpeciesByUID(candidate);
            if (resolved == null) {
                resolved = BeeBreedingHelper.getSpeciesByName(candidate);
            }
            targetInputValid = resolved != null;
            if (resolved != null) {

                String resolvedUID = resolved.getUID();
                if (!resolvedUID.equals(targetBeeSpecies)) {
                    this.targetBeeSpecies = resolvedUID;
                    invalidateBreedingPlan();
                    markBaseTileDirty();
                }
                return;
            }
            markBreedingChanged();
            return;
        }
        targetInputValid = true;
        if (!targetBeeSpecies.isEmpty()) {
            this.targetBeeSpecies = "";
            invalidateBreedingPlan();
            markBaseTileDirty();
        }
    }

    private void refreshBreedingPlan() {
        boolean previousBlocked = allTasksBlocked;
        String previousMissing = missingDroneSpecies;
        int previousCompleted = chainCompletedSteps;

        if (targetBeeSpecies == null || targetBeeSpecies.isEmpty()) {
            if (breedingPlanDirty || !breedingChain.isEmpty()) markBreedingChanged();
            breedingPlan = Plan.empty("");
            breedingChain = new ArrayList<>();
            breedingPlanDirty = false;
            allTasksBlocked = false;
            missingDroneSpecies = "";
        } else {
            if (breedingPlanDirty) {
                breedingPlan = BeeBreedingPlanner.plan(targetBeeSpecies, speciesArchive.getSpecies());
                breedingChain = new ArrayList<>(breedingPlan.getSteps());
                breedingPlanDirty = false;
                markBreedingChanged();
            }

            boolean targetUnlocked = speciesArchive.hasSpecies(targetBeeSpecies);
            boolean hasParents = targetUnlocked
                ? availableQueenSpecies.contains(targetBeeSpecies) && availableDroneSpecies.contains(targetBeeSpecies)
                : breedingChain.stream()
                    .anyMatch(step -> !speciesArchive.hasSpecies(step.result) && hasBreedingParents(step));
            allTasksBlocked = availableQueenCount > 0 && !hasParents && activeOperation == OPERATION_NONE;
            missingDroneSpecies = allTasksBlocked ? breedingPlan.getFirstMissingSpecies() : "";
            if (allTasksBlocked && missingDroneSpecies.isEmpty()) missingDroneSpecies = targetBeeSpecies;
        }

        if (activeOperation == OPERATION_BREEDING) {
            updateMissingFoundations(getActiveBreedingSteps());
        } else if (availableQueenCount > 0 && !speciesArchive.hasSpecies(targetBeeSpecies)) {
            updateMissingFoundations(
                breedingChain.stream()
                    .filter(step -> !speciesArchive.hasSpecies(step.result) && hasBreedingParents(step))
                    .toList());
        } else {
            updateMissingFoundations(Collections.emptyList());
        }
        allTasksBlocked |= !missingFoundations.isEmpty();
        updateChainDisplayInfo();
        if (previousBlocked != allTasksBlocked || !Objects.equals(previousMissing, missingDroneSpecies)
            || previousCompleted != chainCompletedSteps) {
            markBreedingChanged();
        }
    }

    private void invalidateBreedingPlan() {
        breedingPlanDirty = true;
        markBreedingChanged();
    }

    public BeeBreedingInputs scanInputBuses() {
        return scanInputBuses(getStoredInputs());
    }

    public BeeBreedingInputs scanInputBuses(List<ItemStack> inputs) {
        BeeBreedingInputs bees = new BeeBreedingInputs(inputs);
        boolean changed = false;
        for (String uid : bees.getSpecies()) changed |= speciesArchive.unlockSpecies(uid);
        updateAvailableParents(bees);
        int queenCount = bees.getQueenCount();
        if (availableQueenCount != queenCount) {
            availableQueenCount = queenCount;
            markBreedingChanged();
        }
        if (changed) {
            invalidateBreedingPlan();
            markBaseTileDirty();
        }
        if (isFoundationScanDue()) refreshFoundationInputs(inputs);
        return bees;
    }

    private void updateAvailableParents(BeeBreedingInputs inputs) {
        Set<String> drones = inputs.getDroneSpecies(), queens = inputs.getQueenSpecies();
        if (!drones.equals(availableDroneSpecies) || !queens.equals(availableQueenSpecies)) {
            availableDroneSpecies = drones;
            availableQueenSpecies = queens;
            markBreedingChanged();
        }
    }

    public boolean hasBreedingParents(BreedingStep step) {
        return availableQueenSpecies.contains(step.parent1) && availableDroneSpecies.contains(step.parent2)
            || availableQueenSpecies.contains(step.parent2) && availableDroneSpecies.contains(step.parent1);
    }

    public String getQueenParentSpecies(BreedingStep step) {
        for (BreedingAttempt attempt : activeBreedingBatch) {
            if (attempt.step.result.equals(step.result)) return BeeBreedingHelper.getBeeUID(attempt.queen);
        }
        if (availableQueenSpecies.contains(step.parent1) && availableDroneSpecies.contains(step.parent2))
            return step.parent1;
        if (availableQueenSpecies.contains(step.parent2)
            && (!availableQueenSpecies.contains(step.parent1) || availableDroneSpecies.contains(step.parent1)))
            return step.parent2;
        return step.parent1;
    }

    @Override
    public boolean protectsExcessItem() {
        return true;
    }

    @Override
    public boolean addItemOutputs(ItemStack[] outputItems) {
        ItemEjectionHelper ejection = new ItemEjectionHelper(this);
        if (ejection.ejectItems(Arrays.asList(outputItems), 1) == 1) ejection.commit();
        else for (ItemStack output : outputItems) {
            if (output != null && output.stackSize > 0) pendingBeeOutputs.add(output.copy());
        }
        markBaseTileDirty();
        return true;
    }

    private void refundActiveParents() {
        if (activeOperation == OPERATION_FLUSH) {
            if (mOutputItems != null) for (ItemStack output : mOutputItems) {
                if (output != null) pendingBeeOutputs.add(output.copy());
            }
        } else for (BreedingAttempt attempt : activeBreedingBatch) {
            pendingBeeOutputs.add(attempt.queen.copy());
            if (attempt.consumedDrone != null) pendingBeeOutputs.add(attempt.consumedDrone.copy());
        }
        activeBreedingBatch.clear();
        activeOperation = OPERATION_NONE;
    }

    @Override
    public void stopMachine(ShutDownReason reason) {
        refundActiveParents();
        super.stopMachine(reason);
        markBaseTileDirty();
        markBreedingChanged();
    }

    public int getPendingBeeOutputCount() {
        long count = legacyPrincessReturns;
        for (ItemStack output : pendingBeeOutputs) count += output.stackSize;
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    private boolean isFoundationScanDue() {
        return lastFoundationScanTick == Long.MIN_VALUE
            || mTotalRunTime - lastFoundationScanTick >= FOUNDATION_SCAN_INTERVAL;
    }

    private void refreshFoundationInputs(List<ItemStack> inputs) {
        foundationInputs.clear();
        for (ItemStack input : inputs) {
            if (input == null || input.stackSize <= 0) continue;
            ItemStack copy = input.copy();
            copy.stackSize = 1;
            foundationInputs.add(copy);
        }
        foundationFluids.clear();
        for (FluidStack fluid : getStoredFluids()) {
            if (fluid != null && fluid.amount >= FluidContainerRegistry.BUCKET_VOLUME && fluid.getFluid() != null) {
                foundationFluids.add(fluid.getFluid());
            }
        }
        foundationMatches.clear();
        lastFoundationScanTick = mTotalRunTime;
        activeFoundationsChecked = false;
        markBreedingChanged();
    }

    public ItemStack findFoundation(FoundationRequirement requirement) {
        String key = requirement.key();
        if (!foundationMatches.containsKey(key)) {
            ItemStack match = null;
            for (ItemStack input : foundationInputs) {
                if (requirement.matches(input)) {
                    match = input;
                    break;
                }
            }
            if (match == null) match = requirement.findFluidFoundation(foundationFluids);
            foundationMatches.put(key, match);
        }
        return foundationMatches.get(key);
    }

    private void updateMissingFoundations(List<BreedingStep> steps) {
        Map<String, FoundationRequirement> missing = new LinkedHashMap<>();
        for (BreedingStep step : steps) {
            for (FoundationRequirement requirement : step.foundations) {
                if (findFoundation(requirement) == null) missing.putIfAbsent(requirement.key(), requirement);
            }
        }
        List<FoundationRequirement> next = new ArrayList<>(missing.values());
        if (!missingFoundations.equals(next)) {
            missingFoundations = next;
            markBreedingChanged();
        }
    }

    public List<FoundationRequirement> getMissingFoundations() {
        return Collections.unmodifiableList(missingFoundations);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_DISTILLATION_TOWER_ACTIVE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_DISTILLATION_TOWER_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_DISTILLATION_TOWER)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_DISTILLATION_TOWER_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()) };
    }

    @Override
    public int getCasingTextureID() {
        return GTNLCasings.YellowConcrete.getTextureId();
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        return new GTNLMultiblockTooltipBuilder().addAutoTooltip(this)
            .toolTipFinisher();
    }

    @Override
    public String getLocalNameKey() {
        return "gtnl.machine.large_bee_breeder.name";
    }

    @Override
    public TooltipStats getTooltipStats() {
        return new TooltipStats(BASE_PARALLEL_STEPS, Double.NaN, Double.NaN, false, false);
    }

    @Override
    public TooltipStats getTooltipStatsAtTier(TieredPart part, int tier) {
        if (part == TieredPart.VOLTAGE) {
            return new TooltipStats(getParallelForVoltageTier(tier), Double.NaN, Double.NaN, false, false);
        }
        return getTooltipStats();
    }

    @Override
    public Map<String, Object> getTooltipVars() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("attemptSeconds", TICKS_PER_BREEDING / 20D);
        values.put("baseEu", BASE_EUT_PER_BREEDING);
        values.put("glassChance", CHANCE_PER_GLASS_TIER);
        return values;
    }

    @Override
    public String[] getInfoData() {
        String[] superInfo = super.getInfoData();
        String[] info = new String[superInfo.length + 5];
        System.arraycopy(superInfo, 0, info, 0, superInfo.length);

        info[superInfo.length] = StatCollector.translateToLocalFormatted(
            "gtnl.bee_breeder.current_target",
            targetBeeSpecies.isEmpty() ? StatCollector.translateToLocal("gtnl.bee_breeder.unset") : targetBeeSpecies);
        info[superInfo.length + 1] = StatCollector.translateToLocalFormatted(
            "gtnl.bee_breeder.archive_size",
            speciesArchive.getSpecies()
                .size());
        info[superInfo.length + 2] = StatCollector
            .translateToLocalFormatted("gtnl.bee_breeder.chain_progress", countCompletedSteps(), breedingChain.size());
        info[superInfo.length + 3] = StatCollector
            .translateToLocalFormatted("gtnl.bee_breeder.pending", availableQueenCount);
        info[superInfo.length + 4] = StatCollector.translateToLocalFormatted(
            "gtnl.bee_breeder.energy",
            NumberFormatUtil.formatNumber(getEnergyCostPerTick(1)),
            getMutationChanceBonus());

        return info;
    }

    public List<BreedingStep> getBreedingSteps() {
        return Collections.unmodifiableList(breedingChain);
    }

    public List<BreedingStep> getActiveBreedingSteps() {
        if (activeOperation != OPERATION_BREEDING) return Collections.emptyList();
        List<BreedingStep> steps = new ArrayList<>(activeBreedingBatch.size());
        for (BreedingAttempt attempt : activeBreedingBatch) {
            String queen = BeeBreedingHelper.getBeeUID(attempt.queen);
            String drone = queen.equals(attempt.step.parent1) ? attempt.step.parent2 : attempt.step.parent1;
            steps.add(
                new BreedingStep(queen, drone, attempt.step.result, attempt.effectiveChance, attempt.step.foundations));
        }
        return steps;
    }

    private void updateChainDisplayInfo() {
        chainTotalSteps = breedingChain.size();
        chainCompletedSteps = countCompletedSteps();
    }

    private int countCompletedSteps() {
        int count = 0;
        for (BreedingStep step : breedingChain) {
            if (speciesArchive.hasSpecies(step.result)) count++;
        }
        return count;
    }

    @Override
    @Nonnull
    public MTEMultiBlockBaseGui<LargeBeeBreeder> getGui() {
        return new LargeBeeBreederGui(this);
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        writeBeeState(tag, false);
    }

    private void writeBeeState(NBTTagCompound tag, boolean item) {
        tag.setInteger("beeBreederDataVersion", BEE_DATA_VERSION);
        tag.setString("targetBeeSpecies", targetBeeSpecies);
        tag.setInteger("legacyPrincessInputs", legacyPrincessReturns);
        tag.setInteger("pendingPrincessOutputs", legacyPrincessReturns);
        tag.setTag("dronePool", speciesArchive.toNBT());
        tag.setByte("beeBreederActiveOperation", item ? OPERATION_NONE : activeOperation);
        NBTTagList attempts = new NBTTagList();
        if (!item) for (BreedingAttempt attempt : activeBreedingBatch) attempts.appendTag(attempt.toNBT());
        tag.setTag("beeBreederActiveBatch", attempts);
        List<ItemStack> outputs = new ArrayList<>(pendingBeeOutputs);
        if (item) {
            if (activeOperation == OPERATION_FLUSH && mOutputItems != null) outputs.addAll(Arrays.asList(mOutputItems));
            else for (BreedingAttempt attempt : activeBreedingBatch) {
                outputs.add(attempt.queen);
                if (attempt.consumedDrone != null) outputs.add(attempt.consumedDrone);
            }
        }
        NBTTagList pending = new NBTTagList();
        for (ItemStack output : outputs) {
            if (output != null && output.stackSize > 0) pending.appendTag(output.writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("beeBreederPendingOutputs", pending);
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        targetBeeSpecies = tag.getString("targetBeeSpecies");
        boolean currentVersion = tag.getInteger("beeBreederDataVersion") >= BEE_DATA_VERSION;
        legacyPrincessReturns = Math
            .max(0, tag.getInteger(currentVersion ? "legacyPrincessInputs" : "pendingPrincessOutputs"));
        availableQueenCount = 0;
        foundationInputs.clear();
        foundationFluids.clear();
        foundationMatches.clear();
        lastFoundationScanTick = Long.MIN_VALUE;
        missingFoundations = Collections.emptyList();
        activeFoundationsChecked = false;
        speciesArchive = BeeSpeciesArchive.fromNBT(tag.getCompoundTag("dronePool"));
        pendingBeeOutputs.clear();
        NBTTagList pending = tag.getTagList("beeBreederPendingOutputs", 10);
        for (int i = 0; i < pending.tagCount(); i++) {
            ItemStack output = ItemStack.loadItemStackFromNBT(pending.getCompoundTagAt(i));
            if (output != null) pendingBeeOutputs.add(output);
        }
        activeOperation = tag.getByte("beeBreederActiveOperation");
        activeBreedingBatch = new ArrayList<>();
        NBTTagList attempts = tag.getTagList("beeBreederActiveBatch", 10);
        for (int i = 0; i < attempts.tagCount(); i++) {
            BreedingAttempt attempt = BreedingAttempt.fromNBT(attempts.getCompoundTagAt(i));
            if (attempt != null) activeBreedingBatch.add(attempt);
        }
        if (!currentVersion) {
            if (activeOperation == OPERATION_OUTPUT && mOutputItems != null) {
                for (ItemStack output : mOutputItems) {
                    if (output != null && output.stackSize > 0) pendingBeeOutputs.add(output);
                }
                legacyPrincessReturns = Math.max(0, legacyPrincessReturns - 1);
            }
            activeOperation = OPERATION_NONE;
            activeBreedingBatch.clear();
            clearLoadedOperation();
        } else if (activeOperation == OPERATION_BREEDING || activeOperation == OPERATION_OUTPUT) {
            boolean valid = !activeBreedingBatch.isEmpty() && mMaxProgresstime > 0;
            for (BreedingAttempt attempt : activeBreedingBatch) {
                valid &= BeeBreedingHelper.isQueen(attempt.queen);
                valid &= BeeBreedingHelper.getSpeciesByUID(attempt.step.result) != null;
                if (activeOperation == OPERATION_BREEDING) valid &= BeeBreedingHelper.findRegisteredStep(
                    attempt.step.parent1,
                    attempt.step.parent2,
                    attempt.step.result,
                    attempt.step.chance) != null;
            }
            if (!valid) {
                refundActiveParents();
                clearLoadedOperation();
            }
        } else if (activeOperation == OPERATION_FLUSH && mMaxProgresstime <= 0) {
            refundActiveParents();
            clearLoadedOperation();
        }
        updateActiveVoltageTier();
        breedingPlan = Plan.empty(targetBeeSpecies);
        breedingPlanDirty = true;
        breedingChain.clear();
        markBreedingChanged();
    }

    private void clearLoadedOperation() {
        mOutputItems = null;
        mProgresstime = 0;
        mMaxProgresstime = 0;
        lEUt = 0;
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        super.setItemNBT(tag);
        writeBeeState(tag, true);
    }

    @Override
    public int getMaxParallelRecipes() {
        return getMaxParallelBreedingSteps();
    }

    public int getBreedingVoltageTier() {
        return checkEnergyHatchTier();
    }

    public int getMaxParallelBreedingSteps() {
        return getParallelForVoltageTier(getBreedingVoltageTier());
    }

    public long getEuPerBreedingAttempt() {
        return getEnergyCostPerTick(1);
    }

    private long getEnergyCostPerTick(int parallelAttempts) {
        long eutPerAttempt = createBreedingOverclockCalculator().getConsumption() << getGlassUpgrades();
        return eutPerAttempt * Math.max(0, parallelAttempts);
    }

    private GTNLOverclockCalculator createBreedingOverclockCalculator() {
        return createBreedingOverclockCalculator(0);
    }

    private GTNLOverclockCalculator createBreedingOverclockCalculator(int extraOverclocks) {
        int voltageUpgrades = Math.max(0, Math.min(getBreedingVoltageTier(), VoltageIndex.MAX) - BASE_VOLTAGE_TIER);
        long availablePower = Math.max(0L, getMaxInputPower()) >> getGlassUpgrades();
        while (voltageUpgrades > 0 && (BASE_EUT_PER_BREEDING << (2 * voltageUpgrades)) > availablePower) {
            voltageUpgrades--;
        }
        voltageUpgrades += extraOverclocks;
        return new GTNLOverclockCalculator().setRecipeEUt(BASE_EUT_PER_BREEDING)
            .setEUt(BASE_EUT_PER_BREEDING << (2 * voltageUpgrades))
            .setDuration(TICKS_PER_BREEDING)
            .setEUtIncreasePerOC(4)
            .setDurationDecreasePerOC(2)
            .calculate();
    }

    private int getGlassUpgrades() {
        return Math.max(0, Math.min(mGlassTier, VoltageIndex.MAX) - BASE_GLASS_TIER);
    }

    public int getMutationChanceBonus() {
        return getGlassUpgrades() * CHANCE_PER_GLASS_TIER;
    }

    @Override
    public boolean doesBindPlayerInventory() {
        return true;
    }

    @Override
    public boolean supportsVoidProtection() {
        return true;
    }

    @Override
    public boolean supportsInputSeparation() {
        return false;
    }

    @Override
    public boolean supportsBatchMode() {
        return false;
    }

    @Override
    public boolean supportsSingleRecipeLocking() {
        return false;
    }

    private void markBaseTileDirty() {
        IGregTechTileEntity baseTile = getBaseMetaTileEntity();
        if (baseTile != null) baseTile.markDirty();
    }

    private void markBreedingChanged() {
        breedingRevision++;
    }

    private record BreedingAttempt(BreedingStep step, double effectiveChance, boolean succeeded, ItemStack queen,
        ItemStack consumedDrone) {

        private NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("parent1", step.parent1);
            tag.setString("parent2", step.parent2);
            tag.setString("result", step.result);
            tag.setDouble("baseChance", step.chance);
            tag.setDouble("effectiveChance", effectiveChance);
            tag.setBoolean("succeeded", succeeded);
            tag.setTag("queen", queen.writeToNBT(new NBTTagCompound()));
            if (consumedDrone != null) tag.setTag("consumedDrone", consumedDrone.writeToNBT(new NBTTagCompound()));
            return tag;
        }

        private static BreedingAttempt fromNBT(NBTTagCompound tag) {
            ItemStack queen = ItemStack
                .loadItemStackFromNBT(tag.getCompoundTag(tag.hasKey("queen", 10) ? "queen" : "female"));
            if (queen == null) return null;
            String parent1 = tag.getString("parent1"), parent2 = tag.getString("parent2"),
                result = tag.getString("result");
            BreedingStep step = BeeBreedingHelper
                .findRegisteredStep(parent1, parent2, result, tag.getDouble("baseChance"));
            if (step == null) step = new BreedingStep(parent1, parent2, result, tag.getDouble("baseChance"));
            return new BreedingAttempt(
                step,
                tag.getDouble("effectiveChance"),
                tag.getBoolean("succeeded"),
                queen,
                ItemStack.loadItemStackFromNBT(tag.getCompoundTag("consumedDrone")));
        }
    }
}
