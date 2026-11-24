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
import com.science.gtnl.utils.machine.bee.BeeSeedBank;
import com.science.gtnl.utils.machine.bee.BeeSpeciesArchive;
import com.science.gtnl.utils.recipes.GTNLOverclockCalculator;

import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IAlleleBeeSpecies;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.modularui2.GTGuiTextures;
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
    public static final double FEMALE_RETENTION_CHANCE = 1.0D;
    public static final int SEED_CYCLE_TICKS = 128;
    public static final int MODE_AUTOMATIC = 0;
    public static final int MODE_DRONE = 1;
    public static final int MODE_PRINCESS = 2;
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
    private static final byte OPERATION_SEED = 4;

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
    @Getter
    private BeeSeedBank seedBank = new BeeSeedBank();
    private int lastSeedRevision = -1;
    private ItemStack activeSeed;
    private String activeSeedSpecies = "";
    private boolean seedForBreeding;

    private Plan breedingPlan = Plan.empty("");
    private List<BreedingStep> breedingChain = new ArrayList<>();
    private boolean breedingPlanDirty = true;

    private byte activeOperation = OPERATION_NONE;
    private List<BreedingAttempt> activeBreedingBatch = new ArrayList<>();
    private final List<ItemStack> pendingBeeOutputs = new ArrayList<>();
    private int legacyPrincessReturns;
    private Set<String> availableDroneSpecies = Set.of();
    private Set<String> availableFemaleSpecies = Set.of();
    private final List<ItemStack> foundationInputs = new ArrayList<>();
    private final Set<Fluid> foundationFluids = new HashSet<>();
    private final Map<String, ItemStack> foundationMatches = new HashMap<>();
    private long lastFoundationScanTick = Long.MIN_VALUE;
    private List<FoundationRequirement> missingFoundations = Collections.emptyList();
    private boolean activeFoundationsChecked;
    private int activeRequiredVoltageTier = BASE_VOLTAGE_TIER;

    @Getter
    private int availableFemaleCount = 0;

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
        if (targetBeeSpecies.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;
        if (getBreedingVoltageTier() < BASE_VOLTAGE_TIER) return insufficientBreedingVoltage(BASE_VOLTAGE_TIER);
        if (machineMode != MODE_AUTOMATIC || seedBank.hasSpecies(targetBeeSpecies)) {
            ItemStack seed = seedBank.createSeed(targetBeeSpecies, getSeedOutputType());
            return seed == null ? SimpleCheckRecipeResult.ofFailure("gtnl.bee_breeder_missing_seed")
                : prepareSeedCycle(seed, false);
        }
        long eutPerAttempt = getEuPerBreedingAttempt();
        if (getMaxInputPower() < eutPerAttempt) return CheckRecipeResultRegistry.insufficientPower(eutPerAttempt);
        List<BreedingStep> ready = breedingChain.stream()
            .filter(this::isPendingBreedingStep)
            .filter(step -> seedBank.hasSpecies(step.parent1) && seedBank.hasSpecies(step.parent2))
            .toList();
        if (ready.isEmpty()) return breedingPlan.getMissingSpecies()
            .isEmpty() ? SimpleCheckRecipeResult.ofFailure("gtnl.bee_breeder_unreachable_target")
                : missingParents(breedingPlan.getFirstMissingSpecies());
        int limit = (int) Math.min(getMaxParallelBreedingSteps(), getMaxInputPower() / eutPerAttempt);
        List<BreedingAttempt> batch = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        List<BreedingStep> blockedFoundations = new ArrayList<>();
        int voltageTier = getBreedingVoltageTier();
        int requiredTier = Integer.MAX_VALUE;
        String missingParent = "";
        ItemStack missingSeed = null;
        for (BreedingStep planned : ready) {
            if (batch.size() >= limit) break;
            requiredTier = Math.min(requiredTier, getSpeciesVoltageTier(planned.result));
            if (voltageTier < getSpeciesVoltageTier(planned.result)) continue;
            BreedingStep step = selectParentStep(planned, parents);
            ParentPair pair = parents.findPair(step.parent1, step.parent2);
            if (pair == null) {
                if (missingSeed == null) missingSeed = createMissingParent(step, parents);
                missingParent = breedingPlan.getFirstMissingSpecies();
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
            double chance = Math.min(100D, step.chance + getMutationChanceBonus());
            boolean succeeded = BeeBreedingHelper.tryMutation(chance);
            ItemStack princess = succeeded ? BeeBreedingHelper.createPrincess(step.result) : null;
            if (succeeded && princess == null) continue;
            boolean consumeFemale = succeeded && !BeeBreedingHelper.tryMutation(FEMALE_RETENTION_CHANCE);
            boolean consumeDrone = succeeded && BeeBreedingHelper.tryMutation(DRONE_CONSUMPTION_CHANCE);
            ItemStack female = pair.female()
                .copy();
            female.stackSize = 1;
            ItemStack consumedDrone = consumeDrone ? pair.drone()
                .copy() : null;
            if (consumedDrone != null) consumedDrone.stackSize = 1;
            boolean internalOffspring = !step.result.equals(targetBeeSpecies);
            batch.add(
                new BreedingAttempt(
                    step,
                    chance,
                    succeeded,
                    female,
                    consumeFemale,
                    consumedDrone,
                    seedBank.ownsInput(pair.female()),
                    seedBank.ownsInput(pair.drone()),
                    succeeded ? princess : null,
                    internalOffspring,
                    pair.femaleSpecies()));
            if (succeeded && !internalOffspring) {
                outputs.add(princess);
            }
            parents.reserve(pair, consumeFemale, consumeDrone);
        }
        if (batch.isEmpty()) {
            if (missingSeed != null) return prepareSeedCycle(missingSeed, true);
            if (requiredTier != Integer.MAX_VALUE && voltageTier < requiredTier)
                return insufficientBreedingVoltage(requiredTier);
            updateMissingFoundations(blockedFoundations);
            if (!missingFoundations.isEmpty())
                return SimpleCheckRecipeResult.ofFailure("gtnl.bee_breeder_missing_foundation");
            return missingParent.isEmpty() ? CheckRecipeResultRegistry.NO_RECIPE : missingParents(missingParent);
        }
        ItemStack[] result = outputs.toArray(new ItemStack[0]);
        if (result.length > 0 && !canOutputAll(result)) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        parents.commit();
        seedBank.updateInputs();
        updateAvailableParents(parents);
        availableFemaleCount = parents.getFemaleCount();
        updateSlots();
        mOutputItems = result.length == 0 ? null : result;
        activeBreedingBatch = batch;
        for (BreedingAttempt attempt : batch) {
            breedingChain.replaceAll(old -> old.result.equals(attempt.step.result) ? attempt.step : old);
        }
        activeOperation = OPERATION_BREEDING;
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

    private ItemStack createMissingParent(BreedingStep step, BeeBreedingInputs parents) {
        if (parents.hasFemale(step.parent1)) return seedBank.createSeed(step.parent2, EnumBeeType.DRONE);
        if (parents.hasFemale(step.parent2)) return seedBank.createSeed(step.parent1, EnumBeeType.DRONE);
        if (parents.hasDrone(step.parent2)) return seedBank.createSeed(step.parent1, EnumBeeType.PRINCESS);
        if (parents.hasDrone(step.parent1)) return seedBank.createSeed(step.parent2, EnumBeeType.PRINCESS);
        return seedBank.createSeed(step.parent1, EnumBeeType.PRINCESS);
    }

    private CheckRecipeResult prepareSeedCycle(ItemStack seed, boolean internal) {
        String species = BeeBreedingHelper.getBeeUID(seed);
        int tier = getSpeciesVoltageTier(species);
        if (getBreedingVoltageTier() < tier) return insufficientBreedingVoltage(tier);
        long eut = getSeedEnergyCostPerTick();
        if (getMaxInputPower() < eut) return CheckRecipeResultRegistry.insufficientPower(eut);
        if (!internal && !canOutputAll(new ItemStack[] { seed })) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
        activeSeed = seed;
        activeSeedSpecies = species;
        seedForBreeding = internal;
        activeOperation = OPERATION_SEED;
        activeRequiredVoltageTier = tier;
        mOutputItems = internal ? null : new ItemStack[] { seed.copy() };
        mMaxProgresstime = SEED_CYCLE_TICKS;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        lEUt = -eut;
        markBaseTileDirty();
        markBreedingChanged();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    public boolean isSeedPreparation() {
        return activeOperation == OPERATION_SEED && seedForBreeding;
    }

    public boolean isSeedOutputCycle() {
        return activeOperation == OPERATION_SEED ? !seedForBreeding
            : machineMode != MODE_AUTOMATIC || seedBank.hasSpecies(targetBeeSpecies);
    }

    public EnumBeeType getSeedOutputType() {
        return activeSeed != null ? BeeBreedingHelper.getBeeRoot()
            .getType(activeSeed) : machineMode == MODE_DRONE ? EnumBeeType.DRONE : EnumBeeType.PRINCESS;
    }

    public String getSeedOutputSpecies() {
        return activeSeed == null ? targetBeeSpecies : activeSeedSpecies;
    }

    public long getSeedEnergyCostPerTick() {
        return BASE_EUT_PER_BREEDING << getGlassUpgrades();
    }

    @Override
    public boolean supportsMachineModeSwitch() {
        return true;
    }

    @Override
    public int nextMachineMode() {
        return (machineMode + 1) % 3;
    }

    @Override
    public String getMachineModeKey() {
        return "gtnl.machine.large_bee_breeder.mode." + machineMode;
    }

    @Override
    public void setMachineMode(int mode) {
        if (mode < MODE_AUTOMATIC || mode > MODE_PRINCESS)
            throw new IllegalArgumentException("Invalid bee breeding mode");
        super.setMachineMode(mode);
        markBaseTileDirty();
        markBreedingChanged();
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

    private boolean isPendingBreedingStep(BreedingStep step) {
        return step.result.equals(targetBeeSpecies) || !speciesArchive.hasSpecies(step.result);
    }

    private BreedingStep selectParentStep(BreedingStep planned, BeeBreedingInputs parents) {
        BreedingStep selected = planned;
        boolean selectedReady = canProvideParents(planned.parent1, planned.parent2, parents)
            && planned.foundations.stream()
                .allMatch(foundation -> findFoundation(foundation) != null);
        for (MutationData mutation : BeeBreedingHelper.getMutationsForUID(planned.result)) {
            if (mutation.result.equals(mutation.parent1) || mutation.result.equals(mutation.parent2)) continue;
            if (!canProvideParents(mutation.parent1, mutation.parent2, parents) || mutation.foundations.stream()
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

    private boolean canProvideParents(String parent1, String parent2, BeeBreedingInputs parents) {
        return parents.findPair(parent1, parent2) != null
            || seedBank.hasSpecies(parent1) && seedBank.hasSpecies(parent2);
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
        if (activeOperation == OPERATION_SEED && activeSeed != null)
            activeRequiredVoltageTier = getSpeciesVoltageTier(activeSeedSpecies);
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
        if (activeOperation == OPERATION_SEED && seedForBreeding && activeSeed != null)
            seedBank.addBreedingInput(activeSeed);
        for (BreedingAttempt attempt : activeBreedingBatch) {
            if (attempt.succeeded) {
                archiveChanged |= speciesArchive.unlockSpecies(attempt.step.result);
                archiveChanged |= seedBank.retain(attempt.offspring);
                if (attempt.internalOffspring) seedBank.addBreedingInput(attempt.offspring);
            }
        }
        activeSeed = null;
        activeSeedSpecies = "";
        seedForBreeding = false;

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

            List<BreedingStep> pending = breedingChain.stream()
                .filter(this::isPendingBreedingStep)
                .toList();
            boolean hasParents = pending.stream()
                .anyMatch(this::hasBreedingParents);
            allTasksBlocked = availableFemaleCount > 0 && !hasParents && activeOperation == OPERATION_NONE;
            missingDroneSpecies = allTasksBlocked && !pending.isEmpty() ? breedingPlan.getFirstMissingSpecies() : "";
            if (allTasksBlocked && missingDroneSpecies.isEmpty() && !pending.isEmpty()) {
                BreedingStep step = pending.get(0);
                missingDroneSpecies = step.parent1 + " + " + step.parent2;
            }
        }

        if (activeOperation == OPERATION_BREEDING) {
            updateMissingFoundations(getActiveBreedingSteps());
        } else if (availableFemaleCount > 0) {
            updateMissingFoundations(
                breedingChain.stream()
                    .filter(step -> isPendingBreedingStep(step) && hasBreedingParents(step))
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
        for (String uid : bees.getSpecies()) if (!seedBank.hasSpecies(uid)) {
            changed |= seedBank.retain(bees.getSpecimen(uid));
        }
        if (lastSeedRevision != seedBank.getRevision()) {
            for (String uid : seedBank.getSpecies()) changed |= speciesArchive.unlockSpecies(uid);
            lastSeedRevision = seedBank.getRevision();
        }
        seedBank.addToInputs(bees);
        updateAvailableParents(bees);
        int femaleCount = bees.getFemaleCount();
        if (availableFemaleCount != femaleCount) {
            availableFemaleCount = femaleCount;
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
        Set<String> drones = inputs.getDroneSpecies(), females = inputs.getFemaleSpecies();
        if (!drones.equals(availableDroneSpecies) || !females.equals(availableFemaleSpecies)) {
            availableDroneSpecies = drones;
            availableFemaleSpecies = females;
            markBreedingChanged();
        }
    }

    public boolean hasBreedingParents(BreedingStep step) {
        return seedBank.hasSpecies(step.parent1) && seedBank.hasSpecies(step.parent2)
            || availableFemaleSpecies.contains(step.parent1) && availableDroneSpecies.contains(step.parent2)
            || availableFemaleSpecies.contains(step.parent2) && availableDroneSpecies.contains(step.parent1);
    }

    public String getFemaleParentSpecies(BreedingStep step) {
        for (BreedingAttempt attempt : activeBreedingBatch) {
            if (attempt.step.result.equals(step.result)) return attempt.femaleSpecies;
        }
        if (availableFemaleSpecies.contains(step.parent1) && availableDroneSpecies.contains(step.parent2))
            return step.parent1;
        if (availableFemaleSpecies.contains(step.parent2)
            && (!availableFemaleSpecies.contains(step.parent1) || availableDroneSpecies.contains(step.parent1)))
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
            if (attempt.consumedFemale) {
                if (attempt.femaleInternal) seedBank.addBreedingInput(attempt.female);
                else pendingBeeOutputs.add(attempt.female.copy());
            }
            if (attempt.consumedDrone != null) {
                if (attempt.droneInternal) seedBank.addBreedingInput(attempt.consumedDrone);
                else pendingBeeOutputs.add(attempt.consumedDrone.copy());
            }
        }
        activeSeed = null;
        activeSeedSpecies = "";
        seedForBreeding = false;
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
            .translateToLocalFormatted("gtnl.bee_breeder.pending", availableFemaleCount);
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
            String female = attempt.femaleSpecies;
            String drone = female.equals(attempt.step.parent1) ? attempt.step.parent2 : attempt.step.parent1;
            steps.add(
                new BreedingStep(
                    female,
                    drone,
                    attempt.step.result,
                    attempt.effectiveChance,
                    attempt.step.foundations));
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
            if (!step.result.equals(targetBeeSpecies) && speciesArchive.hasSpecies(step.result)) count++;
        }
        return count;
    }

    @Override
    @Nonnull
    public MTEMultiBlockBaseGui<LargeBeeBreeder> getGui() {
        return new LargeBeeBreederGui(this).withMachineModeIcons(
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_LPF_METAL,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_LPF_FLUID,
            GTGuiTextures.OVERLAY_BUTTON_MACHINEMODE_LPF_METAL);
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
        NBTTagCompound bankTag = seedBank.toNBT();
        tag.setTag("beeSeedBank", bankTag);
        tag.setInteger("beeSeedMode", machineMode);
        if (!item && activeSeed != null) tag.setTag("beeActiveSeed", activeSeed.writeToNBT(new NBTTagCompound()));
        else tag.removeTag("beeActiveSeed");
        tag.setBoolean("beeSeedForBreeding", !item && seedForBreeding);
        tag.setByte("beeBreederActiveOperation", item ? OPERATION_NONE : activeOperation);
        NBTTagList attempts = new NBTTagList();
        if (!item) for (BreedingAttempt attempt : activeBreedingBatch) attempts.appendTag(attempt.toNBT());
        tag.setTag("beeBreederActiveBatch", attempts);
        List<ItemStack> outputs = new ArrayList<>(pendingBeeOutputs);
        if (item) {
            if (activeOperation == OPERATION_FLUSH && mOutputItems != null) outputs.addAll(Arrays.asList(mOutputItems));
            else for (BreedingAttempt attempt : activeBreedingBatch) {
                NBTTagList held = bankTag.getTagList("inputs", 10);
                if (attempt.consumedFemale) {
                    if (attempt.femaleInternal) held.appendTag(attempt.female.writeToNBT(new NBTTagCompound()));
                    else outputs.add(attempt.female);
                }
                if (attempt.consumedDrone != null) {
                    if (attempt.droneInternal) held.appendTag(attempt.consumedDrone.writeToNBT(new NBTTagCompound()));
                    else outputs.add(attempt.consumedDrone);
                }
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
        availableFemaleCount = 0;
        foundationInputs.clear();
        foundationFluids.clear();
        foundationMatches.clear();
        lastFoundationScanTick = Long.MIN_VALUE;
        missingFoundations = Collections.emptyList();
        activeFoundationsChecked = false;
        speciesArchive = BeeSpeciesArchive.fromNBT(tag.getCompoundTag("dronePool"));
        seedBank = BeeSeedBank.fromNBT(tag.getCompoundTag("beeSeedBank"));
        lastSeedRevision = -1;
        if (!tag.hasKey("beeSeedBank", 10)) {
            for (String uid : speciesArchive.getSpecies()) {
                ItemStack seed = BeeBreedingHelper.createPrincess(uid);
                if (seed != null) seedBank.retain(seed);
            }
        }
        int mode = tag.getInteger("beeSeedMode");
        machineMode = mode >= MODE_AUTOMATIC && mode <= MODE_PRINCESS ? mode : MODE_AUTOMATIC;
        activeSeed = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("beeActiveSeed"));
        activeSeedSpecies = activeSeed == null ? "" : BeeBreedingHelper.getBeeUID(activeSeed);
        seedForBreeding = tag.getBoolean("beeSeedForBreeding");
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
                valid &= BeeBreedingHelper.isFemale(attempt.female);
                valid &= BeeBreedingHelper.getSpeciesByUID(attempt.step.result) != null;
                valid &= !attempt.step.result.equals(attempt.step.parent1)
                    && !attempt.step.result.equals(attempt.step.parent2);
                valid &= BeeBreedingHelper.findRegisteredStep(
                    attempt.step.parent1,
                    attempt.step.parent2,
                    attempt.step.result,
                    attempt.step.chance) != null;
            }
            if (!valid) {
                refundActiveParents();
                clearLoadedOperation();
            } else activeOperation = OPERATION_BREEDING;
        } else if (activeOperation == OPERATION_SEED) {
            if (activeSeed == null || mMaxProgresstime <= 0 || !seedBank.hasSpecies(activeSeedSpecies)) {
                activeSeed = null;
                activeSeedSpecies = "";
                seedForBreeding = false;
                activeOperation = OPERATION_NONE;
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

    private record BreedingAttempt(BreedingStep step, double effectiveChance, boolean succeeded, ItemStack female,
        boolean consumedFemale, ItemStack consumedDrone, boolean femaleInternal, boolean droneInternal,
        ItemStack offspring, boolean internalOffspring, String femaleSpecies) {

        private NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("parent1", step.parent1);
            tag.setString("parent2", step.parent2);
            tag.setString("result", step.result);
            tag.setDouble("baseChance", step.chance);
            tag.setDouble("effectiveChance", effectiveChance);
            tag.setBoolean("succeeded", succeeded);
            tag.setBoolean("consumedFemale", consumedFemale);
            tag.setBoolean("femaleInternal", femaleInternal);
            tag.setBoolean("droneInternal", droneInternal);
            tag.setBoolean("internalOffspring", internalOffspring);
            tag.setString("femaleSpecies", femaleSpecies);
            tag.setTag("female", female.writeToNBT(new NBTTagCompound()));
            if (consumedDrone != null) tag.setTag("consumedDrone", consumedDrone.writeToNBT(new NBTTagCompound()));
            if (offspring != null) tag.setTag("offspring", offspring.writeToNBT(new NBTTagCompound()));
            return tag;
        }

        private static BreedingAttempt fromNBT(NBTTagCompound tag) {
            ItemStack female = ItemStack
                .loadItemStackFromNBT(tag.getCompoundTag(tag.hasKey("female", 10) ? "female" : "queen"));
            if (female == null) return null;
            String parent1 = tag.getString("parent1"), parent2 = tag.getString("parent2"),
                result = tag.getString("result");
            BreedingStep step = BeeBreedingHelper
                .findRegisteredStep(parent1, parent2, result, tag.getDouble("baseChance"));
            if (step == null) step = new BreedingStep(parent1, parent2, result, tag.getDouble("baseChance"));
            boolean succeeded = tag.getBoolean("succeeded");
            ItemStack offspring = ItemStack.loadItemStackFromNBT(tag.getCompoundTag("offspring"));
            if (succeeded && offspring == null) offspring = BeeBreedingHelper.createPrincess(result);
            return new BreedingAttempt(
                step,
                tag.getDouble("effectiveChance"),
                succeeded,
                female,
                !tag.hasKey("consumedFemale", 1) || tag.getBoolean("consumedFemale"),
                ItemStack.loadItemStackFromNBT(tag.getCompoundTag("consumedDrone")),
                tag.getBoolean("femaleInternal"),
                tag.getBoolean("droneInternal"),
                offspring,
                tag.getBoolean("internalOffspring"),
                tag.hasKey("femaleSpecies", 8) ? tag.getString("femaleSpecies") : BeeBreedingHelper.getBeeUID(female));
        }
    }
}
