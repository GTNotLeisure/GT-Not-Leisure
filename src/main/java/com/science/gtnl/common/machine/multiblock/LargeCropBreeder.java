package com.science.gtnl.common.machine.multiblock;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.onElementPass;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Energy;
import static gregtech.api.enums.HatchElement.ExoticEnergy;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.InputHatch;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.cropsnh.api.ICropCard;
import com.gtnewhorizon.cropsnh.api.ICropMutation;
import com.gtnewhorizon.cropsnh.api.IMachineBreedingRequirement;
import com.gtnewhorizon.cropsnh.farming.SeedStats;
import com.gtnewhorizon.cropsnh.farming.registries.CropRegistry;
import com.gtnewhorizon.cropsnh.farming.requirements.SubSoilRequirement;
import com.gtnewhorizon.cropsnh.init.CropsNHBlockTextures;
import com.gtnewhorizon.cropsnh.init.CropsNHBlocks;
import com.gtnewhorizon.cropsnh.init.CropsNHFluids;
import com.gtnewhorizon.cropsnh.reference.Constants;
import com.gtnewhorizon.cropsnh.reference.Names;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.api.IMultiblockTooltipRules;
import com.science.gtnl.common.gui.modularui.LargeCropBreederGui;
import com.science.gtnl.common.machine.tooltip.GTNLMultiblockTooltipBuilder;
import com.science.gtnl.common.machine.tooltip.StructureTooltipHooks;
import com.science.gtnl.utils.StructureUtils;
import com.science.gtnl.utils.machine.crop.CropBreedingChance;
import com.science.gtnl.utils.machine.crop.CropBreedingPlanner;
import com.science.gtnl.utils.machine.crop.CropBreedingRecipeRules;
import com.science.gtnl.utils.machine.crop.CropBreedingSubstrate;

import bartworks.common.loaders.ItemRegistry;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEExtendedPowerMultiBlockBase;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.ErrorType;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrors;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.VoidProtectionHelper;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

@IMetaTileEntity.SkipGenerateDescription
public class LargeCropBreeder extends MTEExtendedPowerMultiBlockBase<LargeCropBreeder>
    implements ISurvivalConstructable, IMultiblockTooltipRules {

    // GTNR recognizes this seed tag when its global max-stats option is enabled.
    private static final String ARCHIVE_SEED_MARKER = "gtneverresetArchiveSeed";
    private static final String PIECE = "main";
    private static final int OFFSET_X = 2, OFFSET_Y = 3, OFFSET_Z = 0;
    private static final int MIN_CASINGS = 24;
    private static final int FERTILIZER_PER_PARALLEL = 28800;
    private static final int BASE_EUT = 512;
    private static final int WASH_TICKS = 160;

    private final Map<String, SeedStats> archive = new LinkedHashMap<>();
    private String targetCrop = "";
    private ItemStack activeSeed;
    private String activeCrop = "";
    private int activeChance;
    private int activeParallel;
    private boolean washingMode;
    private int activeWashStat;
    private int lastWashResult;

    private IStructureDefinition<LargeCropBreeder> definition;
    private int casingCount;
    private int archiveRevision;
    private int planRevision;
    private boolean planDirty = true;
    private boolean targetValid = true;
    private CropBreedingPlanner.Plan plan;

    public LargeCropBreeder(int id, String name, String regionalName) {
        super(id, name, regionalName);
    }

    public LargeCropBreeder(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new LargeCropBreeder(mName);
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        if (tile.isServerSide() && mMachine && tick % 20 == 0) {
            startRecipeProcessing();
            try {
                collectSamples();
            } finally {
                endRecipeProcessing();
            }
        }
    }

    @Override
    public IStructureDefinition<LargeCropBreeder> getStructureDefinition() {
        if (definition == null) {
            definition = StructureDefinition.<LargeCropBreeder>builder()
                .addShape(
                    PIECE,
                    transpose(
                        StructureUtils.readStructureFromFile(
                            ScienceNotLeisure.RESOURCE_ROOT_ID + ":multiblock/large_crop_breeder")))
                .addElement(
                    'A',
                    buildHatchAdder(LargeCropBreeder.class)
                        .atLeast(InputBus, OutputBus, InputHatch, Energy.or(ExoticEnergy), Maintenance)
                        .casingIndex(farmCasingTextureIndex())
                        .hint(1)
                        .buildAndChain(
                            onElementPass(machine -> ++machine.casingCount, ofBlock(CropsNHBlocks.blockCasings1, 0))))
                .addElement('G', ofBlock(ItemRegistry.bw_realglas, 0))
                .addElement('D', ofBlock(Blocks.dirt, 0))
                .build();
        }
        return definition;
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece(PIECE, stack, hintsOnly, OFFSET_X, OFFSET_Y, OFFSET_Z);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(PIECE, stack, OFFSET_X, OFFSET_Y, OFFSET_Z, budget, env, false, true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        casingCount = 0;
        if (!checkPiece(PIECE, OFFSET_X, OFFSET_Y, OFFSET_Z, errors)) return;
        checkCasingMin(errors, casingCount, MIN_CASINGS);
        requireHatch(errors, InputBus, mInputBusses.size());
        requireHatch(errors, OutputBus, mOutputBusses.size());
        requireHatch(errors, InputHatch, mInputHatches.size());
        int energy = mEnergyHatches.size() + mExoticEnergyHatches.size();
        if (!StructureTooltipHooks.onCheckHatches(this, 1, Integer.MAX_VALUE, Energy, ExoticEnergy) && energy == 0)
            errors.add(StructureErrors.hatchCount(ErrorType.TOO_FEW, Energy, 0, 1));
        if (shouldCheckMaintenance()) requireHatch(errors, Maintenance, mMaintenanceHatches.size());
    }

    private void requireHatch(List<StructureError> errors, HatchElement hatch, int count) {
        if (!StructureTooltipHooks.onCheckHatches(this, 1, Integer.MAX_VALUE, hatch) && count == 0)
            errors.add(StructureErrors.hatchCount(ErrorType.TOO_FEW, hatch, 0, 1));
    }

    @Override
    public CheckRecipeResult checkProcessing() {
        activeSeed = null;
        activeCrop = "";
        activeWashStat = 0;
        collectSamples();
        if (targetCrop.isEmpty()) return CheckRecipeResultRegistry.NO_RECIPE;
        if (washingMode) return startWashing();
        List<ICropMutation> route = getPlan().steps;
        if (route.isEmpty()) {
            return SimpleCheckRecipeResult.ofFailure("gtnl_crop_no_route");
        }
        ICropMutation mutation = route.get(0);
        long recipeVoltage = CropBreedingRecipeRules.requiredVoltage(mutation.getOutput());
        if (recipeVoltage > getMaxInputVoltage() || BASE_EUT > getMaxInputEu())
            return CheckRecipeResultRegistry.insufficientPower(recipeVoltage);
        ArrayList<ICropCard> parents = new ArrayList<>(mutation.getParents());
        for (ICropCard parent : parents) {
            if (!archive.containsKey(parent.getId())) return CheckRecipeResultRegistry.NO_RECIPE;
        }
        SeedStats stats = archive.get(
            mutation.getOutput()
                .getId());
        if (stats == null) stats = archivedStats(1, 1, 1);
        ItemStack output = mutation.getOutput()
            .getSeedItem(stats);
        if (output == null) return CheckRecipeResultRegistry.NO_RECIPE;
        output.stackSize = 1;
        if (!hasOutputSpace(output)) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;

        ItemStack[] catalysts = getStoredInputs().toArray(new ItemStack[0]);
        int[] consume = breedingConsumption(mutation, parents, catalysts);
        if (!validConsumption(consume, catalysts)) {
            return SimpleCheckRecipeResult.ofFailure("gtnl_crop_requirements");
        }
        int limit = (int) Math.min(getMaxParallelRecipes(), getMaxInputEu() / BASE_EUT);
        FluidStack fertilizer = new FluidStack(CropsNHFluids.enrichedFertilizer, FERTILIZER_PER_PARALLEL * limit);
        limit = Math.min(limit, depleteInputQuantity(fertilizer, true) / FERTILIZER_PER_PARALLEL);
        if (limit < 1) return SimpleCheckRecipeResult.ofFailure("gtnl_crop_fertilizer");
        limit = Math.min(
            limit,
            new VoidProtectionHelper().setMachine(this)
                .setItemOutputs(new ItemStack[] { output })
                .setMaxParallel(limit)
                .build()
                .getMaxParallel());
        if (limit < 1) return CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;

        int parallels = 1;
        if (!consumesPerParallel(mutation, consume)) parallels = limit;
        else if (limit > 1) {
            ItemStack[] remaining = new ItemStack[catalysts.length];
            for (int i = 0; i < catalysts.length; i++) {
                remaining[i] = catalysts[i] == null ? null : catalysts[i].copy();
                if (remaining[i] != null) remaining[i].stackSize -= consume[i];
            }
            while (parallels < limit) {
                int[] additional = breedingConsumption(mutation, parents, remaining);
                if (!validConsumption(additional, remaining)) break;
                for (int i = 0; i < consume.length; i++) {
                    consume[i] += additional[i];
                    if (remaining[i] != null) remaining[i].stackSize -= additional[i];
                }
                parallels++;
            }
        }
        fertilizer.amount = FERTILIZER_PER_PARALLEL * parallels;
        int duration = CropBreedingRecipeRules.baseDuration(mutation.getOutput());
        long eut = BASE_EUT;
        while (duration > 1 && recipeVoltage <= getMaxInputVoltage() / 4 && eut * parallels <= getMaxInputEu() / 4) {
            recipeVoltage *= 4;
            eut *= 4;
            duration = Math.max(1, duration / 2);
        }
        depleteInput(fertilizer);
        for (int i = 0; i < consume.length; i++) if (consume[i] > 0) catalysts[i].stackSize -= consume[i];
        updateSlots();
        activeCrop = mutation.getOutput()
            .getId();
        activeParallel = parallels;
        activeChance = CropBreedingChance.percent(mutation.getOutput());
        int successes = 0;
        for (int i = 0; i < parallels; i++)
            if (getBaseMetaTileEntity().getRandomNumber(100) < activeChance) successes++;
        output.stackSize = successes;
        activeSeed = successes > 0 ? output.copy() : null;
        mOutputItems = seedOutputs(activeSeed);
        mMaxProgresstime = duration;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        lEUt = -eut * parallels;
        markDirty();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    private static boolean consumesPerParallel(ICropMutation mutation, int[] consume) {
        for (int amount : consume) if (amount > 0) return true;
        for (var requirement : mutation.getRequirements())
            if (requirement instanceof IMachineBreedingRequirement) return true;
        return false;
    }

    private int[] breedingConsumption(ICropMutation mutation, ArrayList<ICropCard> parents, ItemStack[] catalysts) {
        int[] reserved = new int[catalysts.length];
        for (CropBreedingSubstrate substrate : CropBreedingSubstrate.forMutation(mutation)) {
            if (substrate.isAutomaticallySatisfied()) continue;
            int slot = 0;
            while (slot < catalysts.length && !substrate.matches(catalysts[slot])) slot++;
            if (slot == catalysts.length) return null;
            reserved[slot] = 1;
        }
        int[] consumed = reserved.clone();
        for (var requirement : mutation.getRequirements()) {
            if (requirement instanceof SubSoilRequirement) continue;
            if (requirement instanceof IMachineBreedingRequirement machineRequirement
                && !machineRequirement.canBreed(parents, getBaseMetaTileEntity(), catalysts, consumed)) return null;
        }
        if (!validConsumption(consumed, catalysts)) return null;
        for (int i = 0; i < consumed.length; i++) consumed[i] -= reserved[i];
        return consumed;
    }

    private int chooseWashStat(SeedStats stats) {
        int[] available = new int[3];
        int count = 0;
        if (stats.getGrowth() < Constants.MAX_SEED_STAT) available[count++] = 1;
        if (stats.getGain() < Constants.MAX_SEED_STAT) available[count++] = 2;
        if (stats.getResistance() < Constants.MAX_SEED_STAT) available[count++] = 3;
        return count == 0 ? 0 : available[getBaseMetaTileEntity().getRandomNumber(count)];
    }

    private CheckRecipeResult startWashing() {
        SeedStats stats = archive.get(targetCrop);
        if (stats == null) return SimpleCheckRecipeResult.ofFailure("gtnl_crop_wash_unarchived");
        int stat = chooseWashStat(stats);
        if (stat == 0) return SimpleCheckRecipeResult.ofFailure("gtnl_crop_wash_max");
        if (getMaxInputVoltage() < BASE_EUT || getMaxInputEu() < BASE_EUT)
            return CheckRecipeResultRegistry.insufficientPower(BASE_EUT);
        activeWashStat = stat;
        activeCrop = targetCrop;
        activeParallel = 1;
        activeChance = 100;
        mOutputItems = new ItemStack[0];
        mMaxProgresstime = WASH_TICKS;
        mEfficiency = 10000;
        mEfficiencyIncrease = 10000;
        lEUt = -BASE_EUT;
        markDirty();
        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    public static ItemStack findSubstrate(CropBreedingSubstrate requirement, List<ItemStack> inputs) {
        for (ItemStack stack : inputs) {
            if (!requirement.matches(stack)) continue;
            ItemStack present = stack.copy();
            present.stackSize = 1;
            return present;
        }
        return null;
    }

    private static boolean validConsumption(int[] consume, ItemStack[] catalysts) {
        if (consume == null || consume.length != catalysts.length) return false;
        for (int i = 0; i < consume.length; i++) {
            if (consume[i] < 0 || consume[i] > 0 && (catalysts[i] == null || consume[i] > catalysts[i].stackSize))
                return false;
        }
        return true;
    }

    @Override
    public boolean onRunningTick(ItemStack stack) {
        if (activeSeed != null && mProgresstime + 1 >= mMaxProgresstime && !hasOutputSpace(activeSeed)) {
            checkRecipeResult = CheckRecipeResultRegistry.ITEM_OUTPUT_FULL;
            return false;
        }
        if (checkRecipeResult == CheckRecipeResultRegistry.ITEM_OUTPUT_FULL)
            checkRecipeResult = CheckRecipeResultRegistry.SUCCESSFUL;

        if (!super.onRunningTick(stack)) return false;
        checkRecipeResult = CheckRecipeResultRegistry.SUCCESSFUL;
        return true;
    }

    private static ItemStack[] seedOutputs(ItemStack seed) {
        if (seed == null) return new ItemStack[0];
        List<ItemStack> result = new ArrayList<>();
        for (int remaining = seed.stackSize; remaining > 0;) {
            ItemStack stack = seed.copy();
            stack.stackSize = Math.min(remaining, stack.getMaxStackSize());
            result.add(stack);
            remaining -= stack.stackSize;
        }
        return result.toArray(new ItemStack[0]);
    }

    private boolean hasOutputSpace(ItemStack output) {
        return !new VoidProtectionHelper().setMachine(this)
            .setItemOutputs(seedOutputs(output))
            .setMaxParallel(1)
            .build()
            .isItemFull();
    }

    @Override
    protected void outputAfterRecipe() {
        if (activeWashStat != 0) {
            SeedStats previous = archive.get(activeCrop);
            if (previous != null) {
                int delta = CropBreedingChance.rollWashDelta(getBaseMetaTileEntity().getRandomNumber(100));
                lastWashResult = activeWashStat * 3 + delta + 1;
                if (delta != 0) {
                    archive.put(
                        activeCrop,
                        archivedStats(
                            previous.getGrowth() + (activeWashStat == 1 ? delta : 0),
                            previous.getGain() + (activeWashStat == 2 ? delta : 0),
                            previous.getResistance() + (activeWashStat == 3 ? delta : 0)));
                    markArchiveChanged(false);
                }
            }
        } else if (activeSeed != null) {
            ICropCard crop = CropRegistry.instance.get(activeSeed, false);
            if (crop != null && !archive.containsKey(crop.getId())) {
                archive.put(crop.getId(), readArchivedStats(activeSeed.getTagCompound()));
                markArchiveChanged(true);
            }
        }
        super.outputAfterRecipe();
        activeWashStat = 0;
        activeSeed = null;
        activeCrop = "";
        markDirty();
    }

    private static SeedStats archivedStats(int growth, int gain, int resistance) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte(Names.NBT.growth, clampStat(growth));
        tag.setByte(Names.NBT.gain, clampStat(gain));
        tag.setByte(Names.NBT.resistance, clampStat(resistance));
        tag.setBoolean(Names.NBT.analyzed, true);
        tag.setBoolean(ARCHIVE_SEED_MARKER, true);
        return SeedStats.readFromNBT(tag);
    }

    private static byte clampStat(int value) {
        return (byte) Math.max(Constants.MIN_SEED_STAT, Math.min(Constants.MAX_SEED_STAT, value));
    }

    private static SeedStats readArchivedStats(NBTTagCompound tag) {
        NBTTagCompound sample = (NBTTagCompound) tag.copy();
        sample.setBoolean(ARCHIVE_SEED_MARKER, true);
        return SeedStats.readFromNBT(sample);
    }

    private void collectSamples() {
        boolean changed = false;
        for (ItemStack stack : getStoredInputs()) {
            if (stack == null || stack.stackSize <= 0) continue;
            ICropCard crop = CropRegistry.instance.get(stack, false);
            if (crop == null || archive.containsKey(crop.getId())) continue;
            if (stack.getTagCompound() == null) continue;
            SeedStats stats = readArchivedStats(stack.getTagCompound());
            if (!stats.isAnalyzed()) continue;
            archive.put(crop.getId(), stats);
            stack.stackSize--;
            changed = true;
        }
        if (changed) {
            updateSlots();
            markArchiveChanged(true);
            markDirty();
        }
    }

    private static int farmCasingTextureIndex() {
        return GTUtility.getCasingTextureIndex(CropsNHBlocks.blockCasings1, 0);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(farmCasingTextureIndex());
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing, TextureFactory.builder()
            .addIcon(
                active ? CropsNHBlockTextures.OVERLAY_FRONT_CROP_BREEDER_ACTIVE
                    : CropsNHBlockTextures.OVERLAY_FRONT_CROP_BREEDER)
            .extFacing()
            .build() };
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        return new GTNLMultiblockTooltipBuilder().addAutoTooltip(this)
            .toolTipFinisher();
    }

    @Override
    public Map<String, Object> getTooltipVars() {
        return Map.of(
            "baseEu",
            BASE_EUT,
            "fertilizer",
            FERTILIZER_PER_PARALLEL,
            "washSeconds",
            WASH_TICKS / 20,
            "washUp",
            CropBreedingChance.washIncreaseChance(),
            "washSame",
            CropBreedingChance.washUnchangedChance(),
            "washDown",
            CropBreedingChance.washDecreaseChance(),
            "firstSeconds",
            CropBreedingRecipeRules.baseDuration(1) / 20);
    }

    @Override
    protected MTEMultiBlockBaseGui<LargeCropBreeder> getGui() {
        return new LargeCropBreederGui(this);
    }

    public void setTargetCrop(String value) {
        String id = value == null ? "" : value.trim();
        targetValid = id.isEmpty() || CropRegistry.instance.get(id) != null;
        if (!targetValid || targetCrop.equals(id)) return;
        targetCrop = id;
        markPlanChanged();
        markDirty();
    }

    public String getTargetCrop() {
        return targetCrop;
    }

    public String getActiveCrop() {
        return mMaxProgresstime > 0 ? activeCrop : "";
    }

    public boolean isTargetValid() {
        return targetValid;
    }

    public int getArchiveRevision() {
        return archiveRevision;
    }

    public int getPlanRevision() {
        return planRevision;
    }

    public int getLastWashResult() {
        return lastWashResult;
    }

    public List<String> getArchivedCrops() {
        return new ArrayList<>(archive.keySet());
    }

    public ItemStack getArchivedSeed(String id) {
        ICropCard crop = CropRegistry.instance.get(id);
        SeedStats stats = archive.get(id);
        return crop == null || stats == null ? null : crop.getSeedItem(stats);
    }

    public CropBreedingPlanner.Plan getPlan() {
        if (planDirty || plan == null) {
            plan = CropBreedingPlanner.plan(targetCrop, archive.keySet());
            planDirty = false;
        }
        return plan;
    }

    @Override
    public void saveNBTData(NBTTagCompound tag) {
        super.saveNBTData(tag);
        writeArchive(tag);
        tag.setString("cropActive", activeCrop);
        tag.setInteger("cropActiveChance", activeChance);
        tag.setInteger("cropActiveParallel", activeParallel);
        tag.setInteger("cropActiveWashStat", activeWashStat);
        if (activeSeed != null) tag.setTag("cropActiveSeed", activeSeed.writeToNBT(new NBTTagCompound()));
        else tag.removeTag("cropActiveSeed");
    }

    @Override
    public void loadNBTData(NBTTagCompound tag) {
        super.loadNBTData(tag);
        targetCrop = tag.getString("cropTarget");
        washingMode = tag.getBoolean("cropWashingMode");
        archive.clear();
        NBTTagList samples = tag.getTagList("cropArchive", 10);
        for (int i = 0; i < samples.tagCount(); i++) {
            NBTTagCompound sample = samples.getCompoundTagAt(i);
            String id = sample.getString("id");
            if (CropRegistry.instance.get(id) != null) archive.put(id, readArchivedStats(sample));
        }
        activeCrop = tag.getString("cropActive");
        activeParallel = Math.max(1, tag.getInteger("cropActiveParallel"));
        activeWashStat = tag.getInteger("cropActiveWashStat");
        if (activeWashStat < 1 || activeWashStat > 3) activeWashStat = 0;
        activeChance = tag.hasKey("cropActiveChance") ? tag.getInteger("cropActiveChance")
            : CropBreedingChance.percent(CropRegistry.instance.get(activeCrop));
        activeSeed = tag.hasKey("cropActiveSeed") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("cropActiveSeed"))
            : null;
        markArchiveChanged(true);
    }

    @Override
    public void setItemNBT(NBTTagCompound tag) {
        writeArchive(tag);
        super.setItemNBT(tag);
    }

    private void writeArchive(NBTTagCompound tag) {
        tag.setString("cropTarget", targetCrop);
        tag.setBoolean("cropWashingMode", washingMode);
        NBTTagList samples = new NBTTagList();
        archive.forEach((id, stats) -> {
            NBTTagCompound sample = stats.writeToNBT(new NBTTagCompound());
            sample.setString("id", id);
            samples.appendTag(sample);
        });
        tag.setTag("cropArchive", samples);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return null;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1 << Math.max(0, Math.min(14, (GTUtility.getTier(getMaxInputVoltage()) - VoltageIndex.HV) / 2));
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

    @Override
    public boolean doesBindPlayerInventory() {
        return true;
    }

    public int getSuccessChance() {
        if (!getActiveCrop().isEmpty()) return activeChance;
        List<ICropMutation> steps = getPlan().steps;
        return steps.isEmpty() ? 0
            : CropBreedingChance.percent(
                steps.get(0)
                    .getOutput());
    }

    public int getCurrentParallel() {
        return mMaxProgresstime > 0 ? activeParallel : washingMode ? 1 : getMaxParallelRecipes();
    }

    public boolean isWashingMode() {
        return washingMode;
    }

    public boolean showsWashingOperation() {
        return mMaxProgresstime > 0 ? activeWashStat != 0 : washingMode;
    }

    public void toggleWashingMode() {
        washingMode = !washingMode;
        markDirty();
    }

    private void markArchiveChanged(boolean speciesChanged) {
        archiveRevision++;
        if (speciesChanged) markPlanChanged();
    }

    private void markPlanChanged() {
        planDirty = true;
        planRevision++;
    }
}
