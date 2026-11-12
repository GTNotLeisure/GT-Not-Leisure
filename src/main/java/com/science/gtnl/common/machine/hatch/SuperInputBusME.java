package com.science.gtnl.common.machine.hatch;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.utils.item.ItemStackHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.science.gtnl.api.mixinHelper.ICommonMetaTileEntityInventory;
import com.science.gtnl.api.mixinHelper.IMetaTileEntityInventoryHandler;
import com.science.gtnl.common.gui.modularui.hatch.SuperInputBusMEGui;
import com.science.gtnl.utils.enums.GTNLItemList;

import appeng.api.config.Actionable;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.GridFlags;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStackWatcher;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import gregtech.api.enums.GTValues;
import gregtech.api.interfaces.IConfigurationCircuitSupport;
import gregtech.api.interfaces.IDataCopyable;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.GTUtility;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;
import gregtech.common.config.MachineStats;
import gregtech.common.tileentities.machines.IHatchWatcher;
import gregtech.common.tileentities.machines.IRecipeProcessingAwareHatch;
import gregtech.common.tileentities.machines.ISmartInputHatch;
import gregtech.common.tileentities.machines.MTEHatchInputBusME;
import gregtech.common.tileentities.machines.RecipeCheckReason;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

@IMetaTileEntity.SkipGenerateName
@IMetaTileEntity.SkipGenerateDescription
public class SuperInputBusME extends MTEHatchInputBusME implements IConfigurationCircuitSupport,
    IRecipeProcessingAwareHatch, IPowerChannelState, ISmartInputHatch, IDataCopyable {

    public static int SIDE_SLOT_COUNT = 100;
    public static int ALL_SLOT_COUNT = SIDE_SLOT_COUNT * 2 + 1 + 9 * 9;

    private final ItemStack[] stockDisplayCache = new ItemStack[SIDE_SLOT_COUNT];
    public final int[] displayStackSizes = new int[SIDE_SLOT_COUNT];

    public ItemStack[] shadowInventory = new ItemStack[SIDE_SLOT_COUNT];
    public int[] storedStackSizes = new int[SIDE_SLOT_COUNT];
    {
        Arrays.fill(storedStackSizes, Integer.MAX_VALUE);
    }
    public int[] savedStackSizes = new int[SIDE_SLOT_COUNT];
    private IStackWatcher watcher;

    protected boolean justHadNewItems = false;

    public SuperInputBusME(int aID, boolean autoPullAvailable, String aName, String aNameRegional) {
        super(aID, autoPullAvailable, aName, aNameRegional);
        ((ICommonMetaTileEntityInventory) this).setMInventory(new ItemStack[ALL_SLOT_COUNT]);
        ((IMetaTileEntityInventoryHandler) this).setInventoryHandler(new ItemStackHandler(mInventory) {

            @Override
            public void onContentsChanged(int slot) {
                SuperInputBusME.this.onContentsChanged(slot);
            }
        });
        disableSort = true;
    }

    public SuperInputBusME(String aName, boolean autoPullAvailable, int aTier, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, autoPullAvailable, aTier, aDescription, aTextures);
        ((ICommonMetaTileEntityInventory) this).setMInventory(new ItemStack[ALL_SLOT_COUNT]);
        ((IMetaTileEntityInventoryHandler) this).setInventoryHandler(new ItemStackHandler(mInventory) {

            @Override
            public void onContentsChanged(int slot) {
                SuperInputBusME.this.onContentsChanged(slot);
            }
        });
        disableSort = true;
    }

    @Override
    public String getLocalNameKey() {
        if (!hasOwnLocalName()) return super.getLocalNameKey();
        return autoPullAvailable ? "gtnl.hatch.super_input_bus_me.advanced.name" : "gtnl.hatch.super_input_bus_me.name";
    }

    @Override
    public String getLocalName() {
        if (!hasOwnLocalName()) return super.getLocalName();
        return StatCollector.translateToLocal(getLocalNameKey());
    }

    @Override
    public String[] getDescription() {
        List<String> strings = new ObjectArrayList<>(8);
        strings.add(StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.tooltip.0"));
        strings.add(
            StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.tooltip.1") + " "
                + GTValues.TIER_COLORS[autoPullAvailable ? 7 : 4]
                + GTValues.VN[autoPullAvailable ? 7 : 4]);
        strings.add(StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.tooltip.2"));
        strings.add(StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.tooltip.3"));

        if (autoPullAvailable) {
            strings.add(StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.auto_pull.tooltip.0"));
            strings.add(StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.auto_pull.tooltip.1"));
            strings.add(StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.auto_pull.tooltip.2"));
            strings.add(StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.auto_pull.tooltip.3"));
        }

        strings.add(StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.tooltip.4"));
        strings.add(StatCollector.translateToLocal("gtnl.hatch.super_input_bus_me.tooltip.5"));
        return strings.toArray(new String[0]);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new SuperInputBusME(mName, autoPullAvailable, mTier, mDescriptionArray, mTextures);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);

        NBTTagList stackSizeList = new NBTTagList();
        for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
            int size = storedStackSizes[i];
            if (size == Integer.MAX_VALUE) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("slot", i);
            tag.setInteger("size", size);
            stackSizeList.appendTag(tag);
        }

        aNBT.setTag("storedStackSizes", stackSizeList);
    }

    @Override
    public void setAutoPullItemList(boolean pullItemList) {
        if (!autoPullAvailable) {
            return;
        }

        autoPullItemList = pullItemList;
        if (!autoPullItemList) {
            for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
                mInventory[i] = null;
            }
        } else {
            refreshItemList();
        }
        updateAllInformationSlots();
        configureWatchers();
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);

        Arrays.fill(mInventory, SIDE_SLOT_COUNT, SIDE_SLOT_COUNT * 2, null);

        if (aNBT.hasKey("storedStackSizes")) {
            NBTTagList list = aNBT.getTagList("storedStackSizes", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                int slot = tag.getInteger("slot");
                int size = tag.getInteger("size");
                if (slot < SIDE_SLOT_COUNT) {
                    storedStackSizes[slot] = size;
                }
            }
        }
        normalizeLegacyFilterStacks();
    }

    // TODO : remove later
    protected void normalizeLegacyFilterStacks() {
        for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
            ItemStack filter = mInventory[i];
            if (filter != null && filter.stackSize != 1) mInventory[i] = GTUtility.copyAmount(1, filter);
        }
    }

    @Override
    public void updateSlots() {
        for (int i = 201; i < ALL_SLOT_COUNT; i++) {
            if (mInventory[i] != null && mInventory[i].stackSize <= 0) {
                mInventory[i] = null;
            }
        }
    }

    @Override
    public boolean needsPeriodicChecks() {
        return !MachineStats.machines.useStackWatcher;
    }

    @Override
    public void addWatcher(IHatchWatcher watcher) {
        watchers.add(watcher);
    }

    @Override
    public void removeWatcher(IHatchWatcher watcher) {
        watchers.remove(watcher);
    }

    @Override
    public boolean pasteCopiedData(EntityPlayer player, NBTTagCompound nbt) {
        if (nbt == null || !COPIED_DATA_IDENTIFIER.equals(nbt.getString("type"))) return false;
        ItemStack circuit = GTUtility.loadItem(nbt, "circuit");
        if (GTUtility.isStackInvalid(circuit)) circuit = null;

        if (autoPullAvailable) {
            setAutoPullItemList(nbt.getBoolean("autoPull"));
            minAutoPullStackSize = nbt.getInteger("minStackSize");
            // Data sticks created before refreshTime was implemented should not cause stocking buses to
            // spam divide by zero errors
            if (nbt.hasKey("refreshTime")) {
                autoPullRefreshTime = nbt.getInteger("refreshTime");
            }
        }

        NBTTagList list = nbt.getTagList("storedStackSizes", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getInteger("slot");
            int size = tag.getInteger("size");
            if (slot >= 0 && slot < SIDE_SLOT_COUNT && size > 0) {
                storedStackSizes[slot] = size;
            }
        }

        additionalConnection = nbt.getBoolean("additionalConnection");
        if (!autoPullItemList) {
            clearSlotConfigs();
            NBTTagList stockingItems = nbt.getTagList("itemsToStock", 10);
            for (int i = 0; i < stockingItems.tagCount() && i < SIDE_SLOT_COUNT; i++) {
                setSlotConfig(i, GTUtility.loadItem(stockingItems.getCompoundTagAt(i)));
            }
            configureWatchers();
        }
        setInventorySlotContents(getCircuitSlot(), circuit);
        updateValidGridProxySides();
        byte color = nbt.getByte("color");
        this.getBaseMetaTileEntity()
            .setColorization(color);
        updateAllInformationSlots();
        return true;
    }

    @Override
    public NBTTagCompound getCopiedData(EntityPlayer player) {
        NBTTagCompound nbt = super.getCopiedData(player);

        NBTTagList stackSizeList = new NBTTagList();
        for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
            int size = storedStackSizes[i];
            if (size == Integer.MAX_VALUE) continue;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("slot", i);
            tag.setInteger("size", size);
            stackSizeList.appendTag(tag);
        }
        nbt.setTag("storedStackSizes", stackSizeList);

        NBTTagList stockingItems = new NBTTagList();
        if (!autoPullItemList) {
            for (int index = 0; index < SIDE_SLOT_COUNT; index++) {
                ItemStack config = getSlotConfig(index);
                if (config == null) continue;
                stockingItems.appendTag(GTUtility.saveItem(config));
            }
            nbt.setTag("itemsToStock", stockingItems);
        }
        return nbt;
    }

    @Override
    public int getCircuitSlot() {
        return SIDE_SLOT_COUNT * 2;
    }

    @Override
    public int getManualSlot() {
        return getManualSlotStartForGui();
    }

    @Override
    public int getCircuitSlotX() {
        return 188;
    }

    @Override
    public int getSizeInventory() {
        return ALL_SLOT_COUNT;
    }

    @Override
    public boolean setStackToZeroInsteadOfNull(int aIndex) {
        if (processingRecipe) {
            return true;
        }
        return aIndex < 201 || aIndex >= ALL_SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int aIndex) {
        if (aIndex < 0 || aIndex >= mInventory.length) return null;

        if (aIndex == getCircuitSlot() || (aIndex >= getManualSlot() && aIndex < ALL_SLOT_COUNT)) {
            return mInventory[aIndex];
        }
        if (aIndex >= SIDE_SLOT_COUNT && aIndex < SIDE_SLOT_COUNT * 2) return null; // 显示槽
        if (!processingRecipe) return null;
        if (!isAllowedToWork()) return null;

        // Must pass the reference out to the multi
        return shadowInventory[aIndex];
    }

    @Override
    public void onExplosion() {
        for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
            mInventory[i] = null;
        }
    }

    @Override
    public void refreshItemList() {
        if (!isAllowedToWork()) return;

        IMEMonitor<IAEItemStack> sg;
        Iterator<IAEItemStack> iterator;
        try {
            sg = getProxy().getStorage()
                .getItemInventory();
            iterator = sg.getStorageList()
                .iterator();
        } catch (final GridAccessException ignored) {
            return;
        }

        int index = 0;
        boolean configChanged = false;
        while (iterator.hasNext() && index < SIDE_SLOT_COUNT) {
            IAEItemStack curr = iterator.next();
            if (curr.getStackSize() < minAutoPullStackSize) continue;

            ItemStack oldStack = shadowInventory[index] == null ? null : shadowInventory[index].copy();

            mInventory[index] = GTUtility.copyAmount(1, curr.getItemStack());

            ItemStack newStack = shadowInventory[index] = curr.getItemStack();
            savedStackSizes[index] = newStack.stackSize;
            displayStackSizes[index] = newStack.stackSize;

            boolean sametype = GTUtility.areStacksEqual(oldStack, newStack);
            if (newStack != null) {
                if (sametype) {
                    if (newStack.stackSize > oldStack.stackSize) justHadNewItems = true;
                } else {
                    justHadNewItems = true;
                }
            }
            if (!((oldStack == null && newStack == null) || sametype)) configChanged = true;
            index++;
        }

        for (int i = index; i < SIDE_SLOT_COUNT; i++) {
            configChanged |= mInventory[i] != null;
            mInventory[i] = null;
            shadowInventory[i] = null;
            savedStackSizes[i] = 0;
            displayStackSizes[i] = 0;
        }

        if (configChanged) configureWatchers();
    }

    @Override
    public void updateAllInformationSlots() {
        if (isAllowedToWork()) {
            for (int index = 0; index < SIDE_SLOT_COUNT; index++) {
                updateInformationSlot(index);
            }
        } else {
            clearExtractedStacks();
        }
    }

    @Override
    public CheckRecipeResult endRecipeProcessing(MTEMultiBlockBase controller) {
        CheckRecipeResult checkRecipeResult = CheckRecipeResultRegistry.SUCCESSFUL;

        IMEMonitor<IAEItemStack> sg;
        IEnergyGrid energy;
        try {
            // on some setup endRecipeProcessing() somehow runs before onFirstTick();
            if (!getProxy().isReady()) getProxy().onReady();
            sg = getProxy().getStorage()
                .getItemInventory();
            energy = getProxy().getEnergy();
        } catch (GridAccessException e) {
            processingRecipe = false;
            controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
            return SimpleCheckRecipeResult.ofFailurePersistOnShutdown("stocking_bus_fail_extraction");
        }

        for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
            if (mInventory[i] == null || shadowInventory[i] == null || savedStackSizes[i] == 0) continue;

            int toExtract = savedStackSizes[i] - shadowInventory[i].stackSize;
            if (toExtract <= 0) continue;

            savedStackSizes[i] = shadowInventory[i].stackSize;

            IAEItemStack request = AEItemStack.create(shadowInventory[i]);
            request.setStackSize(toExtract);
            IAEItemStack result = Platform.poweredExtraction(energy, sg, request, getRequestSource());
            if (result == null || result.getStackSize() != toExtract) {
                controller.stopMachine(ShutDownReasonRegistry.CRITICAL_NONE);
                checkRecipeResult = SimpleCheckRecipeResult.ofFailurePersistOnShutdown("stocking_bus_fail_extraction");
            }
        }

        processingRecipe = false;
        return checkRecipeResult;
    }

    /**
     * Update the right side of the GUI, which shows the amounts of items set on the left side
     */
    @Override
    public void updateInformationSlot(int index) {
        if (index < 0 || index >= SIDE_SLOT_COUNT) return;

        if (mInventory[index] == null || !isAllowedToWork()) {
            shadowInventory[index] = null;
            savedStackSizes[index] = 0;
            return;
        }

        AENetworkProxy proxy = getProxy();
        if (proxy == null || !proxy.isActive()) {
            shadowInventory[index] = null;
            savedStackSizes[index] = 0;
            return;
        }

        try {
            IMEMonitor<IAEItemStack> sg = proxy.getStorage()
                .getItemInventory();
            IAEItemStack request = AEItemStack.create(mInventory[index]);
            request.setStackSize(storedStackSizes[index]);
            IAEItemStack result = sg.extractItems(request, Actionable.SIMULATE, getRequestSource());
            ItemStack extracted = result != null ? result.getItemStack() : null;
            shadowInventory[index] = extracted;
            savedStackSizes[index] = extracted == null ? 0 : extracted.stackSize;
            displayStackSizes[index] = savedStackSizes[index];
        } catch (GridAccessException ignored) {}
    }

    public ItemStack updateInformationSlot(int aIndex, ItemStack aStack) {
        updateInformationSlot(aIndex);
        return shadowInventory[aIndex];
    }

    @Override
    public AENetworkProxy getProxy() {
        if (gridProxy == null) {
            if (getBaseMetaTileEntity() instanceof IGridProxyable) {
                gridProxy = new AENetworkProxy(
                    (IGridProxyable) getBaseMetaTileEntity(),
                    "proxy",
                    autoPullAvailable ? GTNLItemList.AdvancedSuperInputBusME.get(1)
                        : GTNLItemList.SuperInputBusME.get(1),
                    true);
                gridProxy.setFlags(GridFlags.REQUIRE_CHANNEL);
                updateValidGridProxySides();
                if (getBaseMetaTileEntity().getWorld() != null) gridProxy.setOwner(
                    getBaseMetaTileEntity().getWorld()
                        .getPlayerEntityByName(getBaseMetaTileEntity().getOwnerName()));
            }
        }
        return this.gridProxy;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex >= 201 && aIndex < ALL_SLOT_COUNT;
    }

    @Override
    public int getGUIWidth() {
        return 392;
    }

    @Override
    public int getGUIHeight() {
        return 179;
    }

    @Override
    public ModularPanel buildUI(PosGuiData data, PanelSyncManager syncManager, UISettings uiSettings) {
        refreshGuiStateOnOpen();
        return new SuperInputBusMEGui(this).build(data, syncManager, uiSettings);
    }

    public int getFilterSlotCountForGui() {
        return SIDE_SLOT_COUNT;
    }

    public int getStockSlotOffsetForGui() {
        return SIDE_SLOT_COUNT;
    }

    public int getManualSlotStartForGui() {
        return SIDE_SLOT_COUNT * 2 + 1;
    }

    public ItemStack updateInformationSlotForGui(int index, ItemStack stack) {
        ItemStack informationStack = updateInformationSlot(index, stack);
        configureWatchers();
        return informationStack;
    }

    public boolean containsFilterStackForGui(ItemStack stack) {
        for (int i = 0; i < SIDE_SLOT_COUNT; ++i) {
            if (GTUtility.areStacksEqual(mInventory[i], stack, false)) return true;
        }
        return false;
    }

    public int getStoredStackSizeForGui(int slot) {
        if (slot < 0 || slot >= storedStackSizes.length) {
            return Integer.MAX_VALUE;
        }
        return storedStackSizes[slot];
    }

    public void setStoredStackSizeForGui(int slot, int stackSize) {
        if (slot < 0 || slot >= storedStackSizes.length) {
            return;
        }
        storedStackSizes[slot] = Math.max(1, stackSize);
        updateInformationSlot(slot);
        configureWatchers();
    }

    protected void refreshGuiStateOnOpen() {
        if (!getBaseMetaTileEntity().isServerSide()) {
            return;
        }
        if (autoPullItemList) {
            refreshItemList();
        }
        updateAllInformationSlots();
    }

    protected void clearInformationSlots() {
        for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
            mInventory[i + SIDE_SLOT_COUNT] = null;
        }
    }

    @Override
    public void updateWatcher(IStackWatcher newWatcher) {
        watcher = newWatcher;
        configureWatchers();
    }

    @Override
    public void onStackChange(IItemList stacks, IAEStack fullStack, IAEStack diffStack, BaseActionSource source,
        StorageChannel channel) {
        if (diffStack.getStackSize() > 0) {
            scheduleRecipeCheck(RecipeCheckReason.THROTTLED);
        }
    }

    private void configureWatchers() {
        if (watcher != null) {
            watcher.clear();
            if (MachineStats.machines.useStackWatcher && !autoPullItemList) {
                for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
                    ItemStack stack = mInventory[i];
                    if (stack != null) watcher.add(AEItemStack.create(stack));
                }
            }
        }
        scheduleRecipeCheck(RecipeCheckReason.THROTTLED);
    }

    private void scheduleRecipeCheck(RecipeCheckReason reason) {
        for (IHatchWatcher hatchWatcher : watchers) {
            hatchWatcher.scheduleRecipeCheck(reason);
        }
    }

    @Override
    public void setSlotConfig(int index, ItemStack config) {
        if (index < 0 || index >= SIDE_SLOT_COUNT) return;
        ItemStack normalized = (config == null || config.getItem() == null) ? null : GTUtility.copyAmount(1, config);
        mInventory[index] = normalized;
        if (normalized == null) {
            shadowInventory[index] = null;
            savedStackSizes[index] = 0;
            displayStackSizes[index] = 0;
        }
        configureWatchers();
    }

    @Override
    public boolean setSlotConfigAndUpdate(int index, ItemStack config) {
        if (index < 0 || index >= SIDE_SLOT_COUNT) return false;
        setSlotConfig(index, config);
        updateInformationSlot(index);
        return true;
    }

    @Override
    public ItemStack getSlotConfig(int index) {
        if (index < 0 || index >= SIDE_SLOT_COUNT) return null;
        ItemStack config = mInventory[index];
        return GTUtility.isStackInvalid(config) ? null : GTUtility.copyAmount(1, config);
    }

    @Override
    protected void clearSlotConfigs() {
        Arrays.fill(mInventory, 0, SIDE_SLOT_COUNT, null);
        Arrays.fill(shadowInventory, null);
        Arrays.fill(savedStackSizes, 0);
        Arrays.fill(displayStackSizes, 0);
        configureWatchers();
    }

    @Override
    protected void clearExtractedStacks() {
        Arrays.fill(shadowInventory, null);
        Arrays.fill(savedStackSizes, 0);
        Arrays.fill(displayStackSizes, 0);
    }

    @Override
    public ItemStack getFirstValidStack(boolean slotsMustMatch) {
        if (slotsMustMatch) {
            ItemStack firstValid = null;
            for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
                if (shadowInventory[i] == null) continue;
                if (firstValid == null) firstValid = shadowInventory[i];
                else if (!GTUtility.areStacksEqual(firstValid, shadowInventory[i])) return null;
            }
            return firstValid;
        }
        for (int i = 0; i < SIDE_SLOT_COUNT; i++) {
            if (shadowInventory[i] != null) return shadowInventory[i];
        }
        return null;
    }

    public ItemStack getStockDisplayStack(int index) {
        if (index < 0 || index >= SIDE_SLOT_COUNT) return null;

        if (!getBaseMetaTileEntity().isServerSide()) {
            return stockDisplayCache[index];
        }

        ItemStack extracted = shadowInventory[index];
        if (extracted == null) return null;
        int amount = displayStackSizes[index];
        if (amount <= 0) return null;
        ItemStack display = extracted.copy();
        display.stackSize = Math.min(amount, Integer.MAX_VALUE);
        return display;
    }

    public void setStockDisplayStack(int index, ItemStack stack) {
        if (index < 0 || index >= SIDE_SLOT_COUNT) return;
        stockDisplayCache[index] = (stack == null || stack.getItem() == null || stack.stackSize <= 0) ? null : stack;
    }
}
