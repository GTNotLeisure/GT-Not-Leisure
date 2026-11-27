package com.science.gtnl.common.machine.multiblock;

import static com.science.gtnl.ScienceNotLeisure.RESOURCE_ROOT_ID;
import static com.science.gtnl.common.recipe.gtnl.ShapedArcaneCraftingRecipes.ARCANE_RESEARCH;
import static com.science.gtnl.common.recipe.gtnl.ShapedArcaneCraftingRecipes.ARCANE_VIS;
import static thaumcraft.common.config.ConfigBlocks.blockCosmeticOpaque;
import static thaumcraft.common.config.ConfigBlocks.blockCosmeticSolid;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.mojang.authlib.GameProfile;
import com.science.gtnl.api.casing.GTNLCasings;
import com.science.gtnl.common.gui.modularui.multiblock.SmallArcaneAssemblerGui;
import com.science.gtnl.common.machine.multiMachineBase.MultiMachineBase;
import com.science.gtnl.common.machine.tooltip.GTNLMultiblockTooltipBuilder;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.common.recipe.thaumcraft.OwnerResearchCache;
import com.science.gtnl.utils.StructureUtils;
import com.science.gtnl.utils.recipes.GTNLOverclockCalculator;
import com.science.gtnl.utils.recipes.GTNLParallelHelper;
import com.science.gtnl.utils.recipes.TransactionalProcessingLogic;

import gregtech.api.casing.Casings;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.enums.VoltageIndex;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.logic.ProcessingLogic;
import gregtech.api.metatileentity.GregTechTileClientEvents;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.visnet.TileVisNode;
import thaumcraft.api.visnet.VisNetHandler;
import thaumcraft.common.Thaumcraft;
import thaumcraft.common.items.wands.ItemWandCasting;
import thaumcraft.common.lib.FakeThaumcraftPlayer;
import thaumcraft.common.tiles.TileVisRelay;

@IMetaTileEntity.SkipGenerateDescription
public class SmallArcaneAssembler extends MultiMachineBase<SmallArcaneAssembler> implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "main";
    private static final String STRUCTURE_FILE_PATH = RESOURCE_ROOT_ID + ":multiblock/small_arcane_assembler";
    private static final String[][] SHAPE = StructureUtils.readStructureFromFile(STRUCTURE_FILE_PATH);

    private static final int HORIZONTAL_OFFSET = 2;
    private static final int VERTICAL_OFFSET = 3;
    private static final int DEPTH_OFFSET = 0;
    private static final int VIS_NODE_SCAN_INTERVAL = 200;
    private static final long WAND_CHARGE_EUT = 32;
    private static final int WAND_CHARGE_INTERVAL = 128;
    private static final int MAX_CHARGE_OVERCLOCKS = 13;
    public static final int CHARGE_STATE_STOPPED = 0, CHARGE_STATE_CHARGING = 1, CHARGE_STATE_PAUSED = 2,
        CHARGE_STATE_FULL = 3, CHARGE_STATE_NO_POWER = 4;
    private static final int WAND_CHARGE_ASPECT_COUNT = 6;
    private static final int WAND_CHARGE_MIN_VIS = 2;
    private static final int WAND_CHARGE_MAX_VIS = 8;
    public static final int CENTIVIS_PER_VIS = 100;
    public static final int VIS_BUFFER_CAPACITY_CV = 16384 * CENTIVIS_PER_VIS;
    private static final int MAX_SCALED_VIS = Integer.MAX_VALUE / CENTIVIS_PER_VIS;
    private static final int CV_BOOST_REFERENCE = 30;
    private static final double MAX_CV_CHARGE_BONUS = 2.00;
    private static final int OVERFLOW_TRANSFER_DENOMINATOR = 60;
    private static final int BASE_OVERFLOW_TRANSFER_NUMERATOR = 15;
    private static final int VIS_EFFECT_RANGE = 8;
    public static final List<Aspect> PRIMAL_ASPECTS = Aspect.getPrimalAspects();

    private final OwnerResearchCache researchCache = new OwnerResearchCache();
    private final int[] bufferedCV = new int[6];
    private int chargeState;
    private int lastVisNodeCount;
    private int lastDrainedCV;
    private int lastDrainTargetCV;
    private final ObjectArrayList<ChunkCoordinates> visNodeCoordinates = new ObjectArrayList<>();
    private long nextVisNodeScanTick;
    private int chargeCycleTicksRemaining;
    private final int[] cycleChargeCV = new int[6];
    private final int[] overflowTransferNumerators = new int[6];
    private final int[] overflowTransferRemainders = new int[6];
    private Aspect visEffectAspect;
    private boolean visConnectionActive;
    private final ObjectArrayList<ChunkCoordinates> clientVisEffectNodes = new ObjectArrayList<>();
    private final Map<ChunkCoordinates, Object> visConnectionBeams = new Object2ObjectOpenHashMap<>();
    private long nextVisEffectNodeSearchTick;
    private FakeThaumcraftPlayer ownerPlayer;

    public SmallArcaneAssembler(int id, String name, String nameRegional) {
        super(id, name, nameRegional);
    }

    public SmallArcaneAssembler(String name) {
        super(name);
    }

    @Override
    public IStructureDefinition<SmallArcaneAssembler> getStructureDefinition() {
        return StructureDefinition.<SmallArcaneAssembler>builder()
            .addShape(STRUCTURE_PIECE_MAIN, StructureUtility.transpose(SHAPE))
            .addElement(
                'A',
                StructureUtility.ofChain(
                    GTStructureUtility.buildHatchAdder(SmallArcaneAssembler.class)
                        .atLeast(
                            HatchElement.InputBus,
                            HatchElement.OutputBus,
                            HatchElement.Energy.or(HatchElement.MultiAmpEnergy),
                            HatchElement.Maintenance)
                        .casingIndex(getCasingTextureID())
                        .hint(1)
                        .build(),
                    StructureUtility.onElementPass(machine -> ++machine.mCountCasing, Casings.MagicCasing.asElement())))
            .addElement('B', StructureUtility.ofBlock(blockCosmeticSolid, 7))
            .addElement('C', Casings.AlchemicalConstructTiered.asElement())
            .addElement('D', StructureUtility.ofBlock(blockCosmeticOpaque, 2))
            .build();
    }

    @Override
    public void checkMachine(IGregTechTileEntity baseMetaTileEntity, ItemStack stack, List<StructureError> errors) {
        if (!checkPiece(STRUCTURE_PIECE_MAIN, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET, errors)) return;

        setupParameters();
        checkHatch(errors);
        checkCasingMin(errors, mCountCasing, 4);
    }

    @Override
    public void saveNBTData(NBTTagCompound nbt) {
        researchCache.writeToNBT(nbt);
        nbt.setInteger("WandChargeTicks", chargeCycleTicksRemaining);
        nbt.setIntArray("WandCycleCharge", cycleChargeCV);
        nbt.setIntArray("WandOverflowNumerators", overflowTransferNumerators);
        nbt.setIntArray("WandOverflowRemainders", overflowTransferRemainders);
        nbt.setIntArray("VisBuffer", bufferedCV);
        nbt.setBoolean("VisConnectionActive", visConnectionActive);
        if (visEffectAspect != null) nbt.setString("VisConnectionAspect", visEffectAspect.getTag());
        super.saveNBTData(nbt);
    }

    @Override
    public void loadNBTData(NBTTagCompound nbt) {
        researchCache.readFromNBT(nbt);
        chargeCycleTicksRemaining = Math.min(WAND_CHARGE_INTERVAL, Math.max(0, nbt.getInteger("WandChargeTicks")));
        loadIntArray(nbt.getIntArray("WandCycleCharge"), cycleChargeCV);
        loadIntArray(nbt.getIntArray("WandOverflowNumerators"), overflowTransferNumerators);
        loadIntArray(nbt.getIntArray("WandOverflowRemainders"), overflowTransferRemainders);
        loadIntArray(nbt.getIntArray("VisBuffer"), bufferedCV);
        for (int i = 0; i < bufferedCV.length; i++) {
            bufferedCV[i] = Math.min(VIS_BUFFER_CAPACITY_CV, Math.max(0, bufferedCV[i]));
        }
        visConnectionActive = nbt.getBoolean("VisConnectionActive");
        visEffectAspect = nbt.hasKey("VisConnectionAspect") ? Aspect.getAspect(nbt.getString("VisConnectionAspect"))
            : null;
        if (visEffectAspect == null) visConnectionActive = false;
        super.loadNBTData(nbt);
    }

    private void loadIntArray(int[] source, int[] target) {
        for (int i = 0; i < target.length; i++) {
            target[i] = i < source.length ? source[i] : 0;
        }
    }

    @Override
    public void onFirstTick(IGregTechTileEntity baseMetaTileEntity) {
        super.onFirstTick(baseMetaTileEntity);
        if (baseMetaTileEntity.isServerSide() && visConnectionActive) {
            baseMetaTileEntity.sendBlockEvent(GregTechTileClientEvents.CHANGE_CUSTOM_DATA, getUpdateData());
        }
    }

    @Override
    public void onPostTick(IGregTechTileEntity baseMetaTileEntity, long tick) {
        super.onPostTick(baseMetaTileEntity, tick);

        if (baseMetaTileEntity.isClientSide()) {
            updateClientVisConnectionBeam(baseMetaTileEntity, tick);
            return;
        }

        if (mMachine && baseMetaTileEntity.isAllowedToWork()) {
            chargeVis(baseMetaTileEntity);
        } else {
            chargeState = CHARGE_STATE_STOPPED;
            if (visConnectionActive) stopVisConnectionEffect(baseMetaTileEntity);
        }
    }

    private void chargeVis(IGregTechTileEntity baseMetaTileEntity) {
        ItemStack wandStack = getArcaneWand();
        boolean changed = refillWandFromBuffer(wandStack);
        chargeState = currentChargeState(wandStack);
        if (chargeState != CHARGE_STATE_CHARGING) {
            if (visConnectionActive) stopVisConnectionEffect(baseMetaTileEntity);
            if (changed) baseMetaTileEntity.markDirty();
            return;
        }
        if (chargeCycleTicksRemaining <= 0) beginChargeCycle(baseMetaTileEntity);
        if (chargeCycleTicksRemaining > 0) {
            changed |= transferPendingCharge(baseMetaTileEntity, wandStack);
            chargeCycleTicksRemaining--;
            if (chargeCycleTicksRemaining <= 0) clearChargeCycle();
        }
        if (changed) baseMetaTileEntity.markDirty();
    }

    private int currentChargeState(ItemStack wandStack) {
        if (mMaxProgresstime > 0) return CHARGE_STATE_PAUSED;
        if (!hasVisRoom(wandStack)) return CHARGE_STATE_FULL;
        if (!drainEnergyInput(WAND_CHARGE_EUT << 2 * chargeOverclocks())) return CHARGE_STATE_NO_POWER;
        return CHARGE_STATE_CHARGING;
    }

    public int chargeOverclocks() {
        return Math.min(MAX_CHARGE_OVERCLOCKS, Math.max(0, mEnergyHatchTier - VoltageIndex.LV));
    }

    private boolean refillWandFromBuffer(ItemStack wandStack) {
        if (wandStack == null) return false;
        ItemWandCasting wand = (ItemWandCasting) wandStack.getItem();
        boolean moved = false;
        for (int i = 0; i < bufferedCV.length; i++) {
            Aspect aspect = PRIMAL_ASPECTS.get(i);
            int amount = Math.min(bufferedCV[i], wandRoom(wandStack, aspect));
            if (amount <= 0) continue;
            wand.addRealVis(wandStack, aspect, amount, true);
            bufferedCV[i] -= amount;
            moved = true;
        }
        return moved;
    }

    private boolean hasVisRoom(ItemStack wandStack) {
        for (int i = 0; i < PRIMAL_ASPECTS.size(); i++) {
            if (visRoom(wandStack, i) > 0) return true;
        }
        return false;
    }

    private int wandRoom(ItemStack wandStack, Aspect aspect) {
        if (wandStack == null) return 0;
        ItemWandCasting wand = (ItemWandCasting) wandStack.getItem();
        return Math.max(0, wand.getMaxVis(wandStack) - wand.getVis(wandStack, aspect));
    }

    private int visRoom(ItemStack wandStack, int aspectIndex) {
        return wandRoom(wandStack, PRIMAL_ASPECTS.get(aspectIndex)) + VIS_BUFFER_CAPACITY_CV - bufferedCV[aspectIndex];
    }

    private int storeVis(ItemStack wandStack, int aspectIndex, int amount) {
        int toWand = Math.min(amount, wandRoom(wandStack, PRIMAL_ASPECTS.get(aspectIndex)));
        if (toWand > 0) {
            ((ItemWandCasting) wandStack.getItem())
                .addRealVis(wandStack, PRIMAL_ASPECTS.get(aspectIndex), toWand, true);
        }
        int toBuffer = Math.max(0, Math.min(amount - toWand, VIS_BUFFER_CAPACITY_CV - bufferedCV[aspectIndex]));
        bufferedCV[aspectIndex] += toBuffer;
        return toWand + toBuffer;
    }

    private void beginChargeCycle(IGregTechTileEntity baseMetaTileEntity) {
        clearChargeCycle();
        ObjectArrayList<Aspect> selectedAspects = new ObjectArrayList<>(PRIMAL_ASPECTS);
        List<TileVisNode> visNodes = getNearbyVisNodes(baseMetaTileEntity);
        boolean connectionEffectStarted = false;
        int aspectCount = Math.min(WAND_CHARGE_ASPECT_COUNT, selectedAspects.size());
        lastVisNodeCount = visNodes.size();
        lastDrainedCV = 0;
        lastDrainTargetCV = aspectCount * CV_BOOST_REFERENCE;
        for (int i = 0; i < aspectCount; i++) {
            Aspect aspect = selectedAspects.remove(baseMetaTileEntity.getWorld().rand.nextInt(selectedAspects.size()));
            int drainedCV = drainVisWithoutParticles(visNodes, aspect, CV_BOOST_REFERENCE);
            lastDrainedCV += drainedCV;
            if (drainedCV > 0 && !connectionEffectStarted) {
                connectionEffectStarted = beginVisConnectionEffect(baseMetaTileEntity, aspect);
            }
            double cvPercentage = Math.min(1.0, (double) drainedCV / CV_BOOST_REFERENCE);
            int baseVis = WAND_CHARGE_MIN_VIS
                + baseMetaTileEntity.getWorld().rand.nextInt(WAND_CHARGE_MAX_VIS - WAND_CHARGE_MIN_VIS + 1);
            int aspectIndex = PRIMAL_ASPECTS.indexOf(aspect);
            cycleChargeCV[aspectIndex] = (int) Math.round(
                baseVis * CENTIVIS_PER_VIS * (1.0 + MAX_CV_CHARGE_BONUS * cvPercentage) * (1 << chargeOverclocks()));
            overflowTransferNumerators[aspectIndex] = BASE_OVERFLOW_TRANSFER_NUMERATOR + drainedCV;
        }
        if (!connectionEffectStarted && visConnectionActive) stopVisConnectionEffect(baseMetaTileEntity);

        chargeCycleTicksRemaining = WAND_CHARGE_INTERVAL;
    }

    private List<TileVisNode> getNearbyVisNodes(IGregTechTileEntity baseMetaTileEntity) {
        long worldTime = baseMetaTileEntity.getWorld()
            .getTotalWorldTime();
        if (worldTime >= nextVisNodeScanTick) {
            scanVisNodeCoordinates(baseMetaTileEntity);
            nextVisNodeScanTick = worldTime + VIS_NODE_SCAN_INTERVAL;
        }

        World world = baseMetaTileEntity.getWorld();
        List<TileVisNode> nodes = new ObjectArrayList<>();
        for (ChunkCoordinates coordinates : visNodeCoordinates) {
            if (!world.blockExists(coordinates.posX, coordinates.posY, coordinates.posZ)) continue;
            TileEntity tileEntity = world.getTileEntity(coordinates.posX, coordinates.posY, coordinates.posZ);
            if (isValidVisEffectNode(tileEntity)) nodes.add((TileVisNode) tileEntity);
        }

        nodes.sort((first, second) -> {
            boolean firstIsRelay = first instanceof TileVisRelay;
            boolean secondIsRelay = second instanceof TileVisRelay;
            if (firstIsRelay != secondIsRelay) return firstIsRelay ? -1 : 1;
            return Double.compare(
                first.getDistanceFrom(
                    baseMetaTileEntity.getXCoord(),
                    baseMetaTileEntity.getYCoord(),
                    baseMetaTileEntity.getZCoord()),
                second.getDistanceFrom(
                    baseMetaTileEntity.getXCoord(),
                    baseMetaTileEntity.getYCoord(),
                    baseMetaTileEntity.getZCoord()));
        });
        return nodes;
    }

    private void scanVisNodeCoordinates(IGregTechTileEntity baseMetaTileEntity) {
        visNodeCoordinates.clear();
        World world = baseMetaTileEntity.getWorld();
        int centerX = baseMetaTileEntity.getXCoord();
        int centerY = baseMetaTileEntity.getYCoord();
        int centerZ = baseMetaTileEntity.getZCoord();
        for (int x = -VIS_EFFECT_RANGE; x <= VIS_EFFECT_RANGE; x++) {
            for (int z = -VIS_EFFECT_RANGE; z <= VIS_EFFECT_RANGE; z++) {
                if (!world.blockExists(centerX + x, centerY, centerZ + z)) continue;
                for (int y = -VIS_EFFECT_RANGE; y <= VIS_EFFECT_RANGE; y++) {
                    TileEntity tileEntity = world.getTileEntity(centerX + x, centerY + y, centerZ + z);
                    if (tileEntity instanceof TileVisNode node
                        && node.getDistanceFrom(centerX, centerY, centerZ) <= VIS_EFFECT_RANGE * VIS_EFFECT_RANGE) {
                        visNodeCoordinates.add(new ChunkCoordinates(node.xCoord, node.yCoord, node.zCoord));
                    }
                }
            }
        }
    }

    private int drainVisWithoutParticles(List<TileVisNode> nodes, Aspect aspect, int amount) {
        int drained = 0;
        for (TileVisNode node : nodes) {
            drained += node.consumeVis(aspect, amount - drained);
            if (drained >= amount) break;
        }
        return drained;
    }

    private boolean transferPendingCharge(IGregTechTileEntity baseMetaTileEntity, ItemStack wandStack) {
        boolean charged = false;
        int elapsedTicks = WAND_CHARGE_INTERVAL - chargeCycleTicksRemaining + 1;
        for (int i = 0; i < cycleChargeCV.length; i++) {
            if (cycleChargeCV[i] <= 0) continue;

            int amount = (int) ((long) cycleChargeCV[i] * elapsedTicks / WAND_CHARGE_INTERVAL
                - (long) cycleChargeCV[i] * (elapsedTicks - 1) / WAND_CHARGE_INTERVAL);
            if (amount <= 0) continue;
            int toWand = Math.min(amount, wandRoom(wandStack, PRIMAL_ASPECTS.get(i)));
            if (toWand > 0) {
                storeVis(wandStack, i, toWand);
                charged = true;
            }
            int overflowCV = amount - toWand;
            if (overflowCV <= 0) continue;

            int transferredCV = scaleOverflow(i, overflowCV);
            int keptCV = overflowCV - transferredCV;
            int toBuffer = Math.min(keptCV, VIS_BUFFER_CAPACITY_CV - bufferedCV[i]);
            bufferedCV[i] += toBuffer;
            int pool = transferredCV + scaleOverflow(i, keptCV - toBuffer);
            int distributed = distributeOverflowVis(baseMetaTileEntity, wandStack, pool, i);
            int returned = Math.min(pool - distributed, VIS_BUFFER_CAPACITY_CV - bufferedCV[i]);
            bufferedCV[i] += returned;
            if (toBuffer + distributed + returned > 0) charged = true;
        }
        return charged;
    }

    private int scaleOverflow(int aspectIndex, int overflowCV) {
        if (overflowCV <= 0) return 0;
        int scaled = overflowCV * overflowTransferNumerators[aspectIndex] + overflowTransferRemainders[aspectIndex];
        overflowTransferRemainders[aspectIndex] = scaled % OVERFLOW_TRANSFER_DENOMINATOR;
        return scaled / OVERFLOW_TRANSFER_DENOMINATOR;
    }

    private void clearChargeCycle() {
        chargeCycleTicksRemaining = 0;
        for (int i = 0; i < cycleChargeCV.length; i++) {
            cycleChargeCV[i] = 0;
            overflowTransferNumerators[i] = 0;
            overflowTransferRemainders[i] = 0;
        }
    }

    private boolean beginVisConnectionEffect(IGregTechTileEntity baseMetaTileEntity, Aspect aspect) {
        int color = PRIMAL_ASPECTS.indexOf(aspect);
        if (color < 0) return false;

        visEffectAspect = aspect;
        visConnectionActive = true;
        baseMetaTileEntity.sendBlockEvent(GregTechTileClientEvents.CHANGE_CUSTOM_DATA, getUpdateData());
        return true;
    }

    private void stopVisConnectionEffect(IGregTechTileEntity baseMetaTileEntity) {
        visConnectionActive = false;
        visEffectAspect = null;
        baseMetaTileEntity.sendBlockEvent(GregTechTileClientEvents.CHANGE_CUSTOM_DATA, (byte) 0);
    }

    @Override
    public void onValueUpdate(byte value) {
        int color = Byte.toUnsignedInt(value) - 1;
        if (color >= 0 && color < PRIMAL_ASPECTS.size()) {
            boolean wasActive = visConnectionActive;
            visEffectAspect = PRIMAL_ASPECTS.get(color);
            visConnectionActive = true;
            if (!wasActive) {
                clientVisEffectNodes.clear();
                visConnectionBeams.clear();
                nextVisEffectNodeSearchTick = 0;
                nextVisNodeScanTick = 0;
            }
        } else {
            visConnectionActive = false;
            visEffectAspect = null;
            clientVisEffectNodes.clear();
            visConnectionBeams.clear();
        }
    }

    @Override
    public byte getUpdateData() {
        if (!visConnectionActive || visEffectAspect == null) return 0;
        return (byte) (PRIMAL_ASPECTS.indexOf(visEffectAspect) + 1);
    }

    private void updateClientVisConnectionBeam(IGregTechTileEntity baseMetaTileEntity, long tick) {
        if (!visConnectionActive || visEffectAspect == null) {
            clientVisEffectNodes.clear();
            visConnectionBeams.clear();
            return;
        }

        if (tick >= nextVisEffectNodeSearchTick) {
            refreshClientVisEffectNodes(baseMetaTileEntity);
            nextVisEffectNodeSearchTick = tick + 20;
        }

        int color = visEffectAspect.getColor();
        float red = (float) (color >> 16 & 255) / 255.0F;
        float green = (float) (color >> 8 & 255) / 255.0F;
        float blue = (float) (color & 255) / 255.0F;
        for (ChunkCoordinates coordinates : clientVisEffectNodes) {
            TileEntity tileEntity = baseMetaTileEntity.getWorld()
                .getTileEntity(coordinates.posX, coordinates.posY, coordinates.posZ);
            if (!(tileEntity instanceof TileVisNode node) || node.isInvalid()) continue;

            Object beam = Thaumcraft.proxy.beamPower(
                baseMetaTileEntity.getWorld(),
                node.xCoord + 0.5,
                node.yCoord + 0.5,
                node.zCoord + 0.5,
                baseMetaTileEntity.getXCoord() + 0.5,
                baseMetaTileEntity.getYCoord() + 0.5,
                baseMetaTileEntity.getZCoord() + 0.5,
                red,
                green,
                blue,
                true,
                visConnectionBeams.get(coordinates));
            visConnectionBeams.put(coordinates, beam);
        }
    }

    private void refreshClientVisEffectNodes(IGregTechTileEntity baseMetaTileEntity) {
        ObjectArrayList<ChunkCoordinates> updatedNodes = new ObjectArrayList<>();
        for (TileVisNode node : getNearbyVisNodes(baseMetaTileEntity)) {
            updatedNodes.add(new ChunkCoordinates(node.xCoord, node.yCoord, node.zCoord));
        }
        clientVisEffectNodes.clear();
        clientVisEffectNodes.addAll(updatedNodes);
        visConnectionBeams.keySet()
            .retainAll(updatedNodes);
    }

    private boolean isValidVisEffectNode(TileEntity tileEntity) {
        return tileEntity instanceof TileVisNode node
            && (node.isSource() || VisNetHandler.isNodeValid(node.getParent()));
    }

    private int distributeOverflowVis(IGregTechTileEntity baseMetaTileEntity, ItemStack wandStack, int overflowPool,
        int sourceIndex) {
        if (overflowPool <= 0) return 0;

        IntArrayList targets = new IntArrayList();
        IntArrayList rooms = new IntArrayList();
        long totalRoom = 0;
        for (int i = 0; i < PRIMAL_ASPECTS.size(); i++) {
            if (i == sourceIndex) continue;
            int room = visRoom(wandStack, i);
            if (room <= 0) continue;

            targets.add(i);
            rooms.add(room);
            totalRoom += room;
        }
        if (targets.isEmpty()) return 0;

        int distributable = (int) Math.min(overflowPool, totalRoom);
        int[] shares = new int[targets.size()];
        int distributed = 0;
        for (int i = 0; i < targets.size(); i++) {
            shares[i] = (int) ((long) distributable * rooms.getInt(i) / totalRoom);
            distributed += shares[i];
        }

        int remainder = distributable - distributed;
        int start = baseMetaTileEntity.getWorld().rand.nextInt(targets.size());
        for (int i = 0; i < remainder; i++) {
            shares[(start + i) % targets.size()]++;
        }

        int stored = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (shares[i] > 0) stored += storeVis(wandStack, targets.getInt(i), shares[i]);
        }
        return stored;
    }

    public int getBufferedVisForGui(int aspectIndex) {
        return bufferedCV[aspectIndex];
    }

    public int getWandVisForGui(int aspectIndex) {
        ItemStack wandStack = getArcaneWand();
        if (wandStack == null) return 0;
        return ((ItemWandCasting) wandStack.getItem()).getVis(wandStack, PRIMAL_ASPECTS.get(aspectIndex));
    }

    public int getChargeStateForGui() {
        return chargeState;
    }

    public int getVisNodeCountForGui() {
        return lastVisNodeCount;
    }

    public int getDrainedCVForGui() {
        return lastDrainedCV;
    }

    public int getDrainTargetCVForGui() {
        return lastDrainTargetCV;
    }

    public int getWandMaxVisForGui() {
        ItemStack wandStack = getArcaneWand();
        return wandStack == null ? 0 : ((ItemWandCasting) wandStack.getItem()).getMaxVis(wandStack);
    }

    private boolean isResearchComplete(String research) {
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        String ownerName = baseMetaTileEntity == null ? null : baseMetaTileEntity.getOwnerName();
        return researchCache.isComplete(ownerName, research);
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stack, hintsOnly, HORIZONTAL_OFFSET, VERTICAL_OFFSET, DEPTH_OFFSET);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int elementBudget, ISurvivalBuildEnvironment environment) {
        if (mMachine) return -1;
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stack,
            HORIZONTAL_OFFSET,
            VERTICAL_OFFSET,
            DEPTH_OFFSET,
            elementBudget,
            environment,
            false,
            true);
    }

    @Override
    public RecipeMap<?> getRecipeMap() {
        return GTNLRecipeMaps.IndustrialShapedArcaneCraftingRecipes;
    }

    @Override
    public ProcessingLogic createProcessingLogic() {
        return new TransactionalProcessingLogic() {

            @NotNull
            @Override
            public CheckRecipeResult validateRecipe(@NotNull GTRecipe recipe) {
                CheckRecipeResult baseResult = super.validateRecipe(recipe);
                if (!baseResult.wasSuccessful()) return baseResult;

                String research = recipe.getMetadata(ARCANE_RESEARCH);
                AspectList requiredVis = recipe.getMetadata(ARCANE_VIS);
                if (research == null || requiredVis == null) return CheckRecipeResultRegistry.NO_RECIPE;

                if (!isResearchComplete(research)) {
                    return SimpleCheckRecipeResult.ofFailure("gtnl.missing_arcane_research");
                }
                if (getArcaneWand() == null) {
                    return SimpleCheckRecipeResult.ofFailure("gtnl.missing_arcane_wand");
                }
                if (getAffordableCrafts(requiredVis, 1) <= 0) {
                    return SimpleCheckRecipeResult.ofFailure("gtnl.insufficient_vis");
                }

                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @NotNull
            @Override
            public GTNLParallelHelper createParallelHelper(@NotNull GTRecipe recipe) {
                int limit = (int) Math.min(Integer.MAX_VALUE, (long) getTrueParallel() * getMaxBatchSize());
                int affordable = getAffordableCrafts(recipe.getMetadata(ARCANE_VIS), limit);
                return super.createParallelHelper(recipe).setMaxTotalParallel(affordable);
            }

            @NotNull
            @Override
            public CheckRecipeResult onRecipeStart(@NotNull GTRecipe recipe) {
                AspectList requiredVis = recipe.getMetadata(ARCANE_VIS);
                if (requiredVis == null) return CheckRecipeResultRegistry.NO_RECIPE;
                if (getArcaneWand() == null) {
                    return SimpleCheckRecipeResult.ofFailure("gtnl.missing_arcane_wand");
                }
                FakeThaumcraftPlayer player = getOwnerPlayer();
                if (player == null || !consumeVis(scaleAspects(requiredVis, calculatedParallels), player, true)) {
                    return SimpleCheckRecipeResult.ofFailure("gtnl.insufficient_vis");
                }
                return CheckRecipeResultRegistry.SUCCESSFUL;
            }

            @NotNull
            @Override
            public GTNLOverclockCalculator createOverclockCalculator(@NotNull GTRecipe recipe) {
                return super.createOverclockCalculator(recipe).setExtraDurationModifier(mConfigSpeedBoost)
                    .setHeatOC(getHeatOC())
                    .setMachineHeat(getMachineHeat())
                    .setHeatDiscount(getHeatDiscount())
                    .setAmperageOC(getAmperageOC())
                    .setEUtDiscount(getEUtDiscount())
                    .setDurationModifier(getDurationModifier())
                    .setPerfectOC(getPerfectOC())
                    .setMaxTierSkips(getMaxTierSkip())
                    .setMaxOverclocks(getMaxOverclocks());
            }
        }.setMaxParallelSupplier(this::getTrueParallel);
    }

    private ItemStack getArcaneWand() {
        ItemStack wand = getControllerSlot();
        return wand != null && wand.getItem() instanceof ItemWandCasting ? wand : null;
    }

    private int getAffordableCrafts(AspectList requiredVis, int limit) {
        if (requiredVis == null || limit <= 0) return 0;

        for (Aspect aspect : requiredVis.getAspects()) {
            if (aspect == null) continue;
            int amount = requiredVis.getAmount(aspect);
            if (amount < 0) return 0;
            if (amount > 0) limit = Math.min(limit, MAX_SCALED_VIS / amount);
        }
        if (limit <= 0) return 0;
        FakeThaumcraftPlayer player = getOwnerPlayer();
        if (player == null || !consumeVis(requiredVis, player, false)) return 0;
        if (consumeVis(scaleAspects(requiredVis, limit), player, false)) return limit;

        int affordable = 1;
        int unaffordable = limit;
        while (unaffordable - affordable > 1) {
            int middle = affordable + (unaffordable - affordable) / 2;
            if (consumeVis(scaleAspects(requiredVis, middle), player, false)) {
                affordable = middle;
            } else {
                unaffordable = middle;
            }
        }
        return affordable;
    }

    private static AspectList scaleAspects(AspectList aspects, int multiplier) {
        AspectList scaled = new AspectList();
        for (Aspect aspect : aspects.getAspects()) {
            if (aspect == null) continue;
            int amount = aspects.getAmount(aspect);
            if (amount <= 0) continue;
            long required = (long) amount * multiplier;
            if (required > MAX_SCALED_VIS || required < 0) return null;
            scaled.add(aspect, (int) required);
        }
        return scaled;
    }

    private FakeThaumcraftPlayer getOwnerPlayer() {
        IGregTechTileEntity baseMetaTileEntity = getBaseMetaTileEntity();
        String ownerName = baseMetaTileEntity.getOwnerName();
        if (ownerName == null || ownerName.isEmpty()) return null;

        World world = baseMetaTileEntity.getWorld();
        UUID ownerUuid = baseMetaTileEntity.getOwnerUuid();
        GameProfile profile = ownerPlayer == null ? null : ownerPlayer.getGameProfile();
        if (profile == null || ownerPlayer.worldObj != world
            || !ownerName.equals(profile.getName())
            || !Objects.equals(ownerUuid, profile.getId())) {
            ownerPlayer = new FakeThaumcraftPlayer(world, new GameProfile(ownerUuid, ownerName));
        }
        return ownerPlayer;
    }

    private boolean consumeVis(AspectList requiredVis, FakeThaumcraftPlayer player, boolean consume) {
        ItemStack wand = getArcaneWand();
        if (wand == null || requiredVis == null) return false;
        if (requiredVis.size() == 0) return true;

        boolean successful = ((ItemWandCasting) wand.getItem())
            .consumeAllVisCrafting(wand, player, requiredVis, consume);
        if (successful && consume) getBaseMetaTileEntity().markDirty();
        return successful;
    }

    @Override
    public int getCasingTextureID() {
        return GTNLCasings.MagicCasing.getTextureId();
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity baseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        if (side == facing) {
            if (active) {
                return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureID()),
                    TextureFactory.builder()
                        .addIcon(Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                        .extFacing()
                        .glow()
                        .build() };
            }

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
    public boolean needsClientTick() {
        return true;
    }

    @Override
    public boolean getPerfectOC() {
        return true;
    }

    @Override
    protected @NotNull MTEMultiBlockBaseGui<?> getGui() {
        return new SmallArcaneAssemblerGui(this);
    }

    @Override
    public MultiblockTooltipBuilder createTooltip() {
        GTNLMultiblockTooltipBuilder tt = new GTNLMultiblockTooltipBuilder();
        tt.addAutoTooltip(this)
            .toolTipFinisher();
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tileEntity) {
        return new SmallArcaneAssembler(mName);
    }
}
