package com.science.gtnl.common.gui.container;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.gtnllib.utils.GameUtils;
import com.science.gtnl.common.block.blocks.tile.TileEntityDirePatternEncoder;
import com.science.gtnl.common.item.items.ArcaneCraftPattern;
import com.science.gtnl.common.item.items.DireCraftPattern;
import com.science.gtnl.common.recipe.thaumcraft.ArcanePatternRecipes;
import com.science.gtnl.compat.avaritia.DireCrafting;
import com.science.gtnl.utils.enums.GTNLItemList;

import appeng.container.AEBaseContainer;
import appeng.container.ContainerNull;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotFakeCraftingMatrix;
import appeng.container.slot.SlotInaccessible;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.sync.SyncCodecs;
import appeng.container.sync.handlers.BooleanSyncHandler;
import appeng.container.sync.handlers.ObjectSyncHandler;
import appeng.util.Platform;
import cpw.mods.fml.common.network.ByteBufUtils;

public class ContainerDirePatternEncoder extends AEBaseContainer {

    public static final NBTTagCompound empty = new NBTTagCompound();

    private final TileEntityDirePatternEncoder te;
    private final BooleanSyncHandler arcaneSupported;
    private final BooleanSyncHandler arcaneMode;
    private final BooleanSyncHandler writeEssentia;
    private final ObjectSyncHandler<NBTTagCompound> aspects;
    private int previewRevision = -1;

    private final SlotFakeCraftingMatrix[] craftingSlots = new SlotFakeCraftingMatrix[81];
    private final SlotInaccessible outputSlot;
    private final SlotRestrictedInput patternSlotIN;
    private final SlotRestrictedInput patternSlotOUT;

    public ContainerDirePatternEncoder(InventoryPlayer player, TileEntityDirePatternEncoder anchor) {
        super(player, anchor);
        te = anchor;
        arcaneSupported = syncRegistrar().booleanS2C("arcaneSupported");
        arcaneSupported.setLocalValue(te.isArcaneSupported());
        arcaneMode = syncRegistrar().booleanS2C("arcaneMode");
        arcaneMode.setLocalValue(te.isArcaneMode());
        writeEssentia = syncRegistrar().booleanS2C("writeEssentia");
        writeEssentia.setLocalValue(te.writesEssentia());
        aspects = syncRegistrar().objectS2C(
            "arcaneIngredients",
            SyncCodecs.of(
                "aspectAmounts",
                ByteBufUtils::writeTag,
                ByteBufUtils::readTag,
                tag -> (NBTTagCompound) tag.copy(),
                NBTTagCompound::equals),
            new NBTTagCompound());

        var recipe = te.getRecipe();
        var pattern = te.getPattern();

        for (int y = 0; y < 9; y++) {
            for (int x = 0; x < 9; x++) {
                this.addSlotToContainer(
                    this.craftingSlots[x
                        + y * 9] = new SlotFakeCraftingMatrix(recipe, x + y * 9, 12 + x * 18, 8 + y * 18) {

                            @Override
                            public boolean isEnabled() {
                                return !isArcaneMode() || isArcaneSlot(getSlotIndex());
                            }

                            @Override
                            public void putStack(ItemStack is) {
                                if (is == null) {
                                    super.putStack(is);
                                    return;
                                }
                                var i = is.copy();
                                i.stackSize = 1;
                                super.putStack(i);
                            }
                        });
            }
        }

        this.addSlotToContainer(outputSlot = new SlotInaccessible(new InventoryBasic("preview", false, 1), 0, 210, 80));

        this.addSlotToContainer(
            this.patternSlotIN = new SlotRestrictedInput(
                SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                pattern,
                0,
                210,
                100 + 10,
                this.getInventoryPlayer()));
        this.addSlotToContainer(
            this.patternSlotOUT = new SlotRestrictedInput(
                SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                pattern,
                1,
                210,
                100 + 50,
                this.getInventoryPlayer()) {

                @Override
                public boolean isItemValid(final ItemStack i) {
                    return i != null && i.getItem() instanceof DireCraftPattern
                        && (!(i.getItem() instanceof ArcaneCraftPattern) || supportsArcaneMode());
                }
            });

        this.patternSlotOUT.setStackLimit(1);
        this.bindPlayerInventory(player, 31, 174);
    }

    public boolean supportsArcaneMode() {
        return te.isArcaneSupported() && arcaneSupported.get();
    }

    public boolean isArcaneMode() {
        return supportsArcaneMode() && arcaneMode.get();
    }

    public void setArcaneMode(boolean value) {
        te.setArcaneMode(value);
        arcaneMode.set(te.isArcaneMode());
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            arcaneMode.set(te.isArcaneMode());
            writeEssentia.set(te.writesEssentia());
            if (previewRevision != te.getRecipeRevision()) {
                previewRevision = te.getRecipeRevision();
                var match = te.isArcaneMode() ? findArcane(null) : null;
                outputSlot.putStack(te.isArcaneMode() ? (match == null ? null : match.output()) : findDire());
                aspects.set(te.writesEssentia() && match != null ? match.costs() : new NBTTagCompound());
            }
        }
        super.detectAndSendChanges();
    }

    @Override
    public SlotFake getValidDestinationFakeSlot(ItemStack stack) {
        for (var slot : craftingSlots) {
            if (!slot.isEnabled()) continue;
            if (slot.getStack() == null) return slot;
            if (Platform.isSameItemPrecise(slot.getStack(), stack)) return null;
        }
        return null;
    }

    private ArcanePatternRecipes.Match findArcane(NBTTagCompound encoded) {
        return ArcanePatternRecipes.match(
            getPlayerInv().player,
            i -> te.getRecipe()
                .getStackInSlot(arcaneSlot(i)),
            encoded);
    }

    private ItemStack findDire() {
        final InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 9, 9);

        for (int x = 0; x < 81; x++) {
            ic.setInventorySlotContents(
                x,
                te.getRecipe()
                    .getStackInSlot(x));
        }

        return DireCrafting.output(ic, te.getWorldObj());
    }

    public static int arcaneSlot(int index) {
        return 30 + index / 3 * 9 + index % 3;
    }

    public static boolean isArcaneSlot(int slot) {
        return slot / 9 >= 3 && slot / 9 <= 5 && slot % 9 >= 3 && slot % 9 <= 5;
    }

    public NBTTagCompound getAspectAmounts() {
        return aspects.get();
    }

    public boolean writesEssentia() {
        return writeEssentia.get();
    }

    public void setWriteEssentia(boolean write) {
        te.setWriteEssentia(write);
        detectAndSendChanges();
    }

    public void encode(boolean isShift) {
        final NBTTagCompound encodedValue = new NBTTagCompound();
        boolean arcane = te.isArcaneMode();
        var match = arcane ? findArcane(encodedValue) : null;
        ItemStack out = arcane ? (match == null ? null : match.output()) : findDire();
        if (out == null || out.stackSize <= 0) return;
        ItemStack old = patternSlotOUT.getStack();
        if (old != null && (!patternSlotOUT.isItemValid(old) || old.stackSize != 1)) return;

        final NBTTagCompound tagOut = Platform.writeItemStackToNBT(out, new NBTTagCompound());

        final NBTTagList tagIn = new NBTTagList();
        for (int i = 0; i < (arcane ? 9 : 81); i++) {
            ItemStack ingredient = te.getRecipe()
                .getStackInSlot(arcane ? arcaneSlot(i) : i);
            tagIn
                .appendTag(ingredient != null ? Platform.writeItemStackToNBT(ingredient, new NBTTagCompound()) : empty);
        }
        if (match != null) match.finishPattern(encodedValue, tagIn, te.writesEssentia());
        else encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", true);
        encodedValue.setString("author", this.getPlayerInv().player.getCommandSenderName());

        ItemStack output = (arcane ? GTNLItemList.ArcaneCraftPattern : GTNLItemList.DireCraftPattern).get(1);
        output.setTagCompound(encodedValue);
        if (old == null) {
            if (patternSlotIN.getStack() == null || !patternSlotIN.isItemValid(patternSlotIN.getStack())) return;
            patternSlotIN.decrStackSize(1);
        }
        te.storeEncodedPattern(isShift ? null : output);
        patternSlotOUT.onSlotChanged();
        if (isShift) GameUtils.placeItemBackInInventory(getPlayerInv().player, output);
    }

    public void clear() {
        boolean arcane = te.isArcaneMode();
        for (int i = 0; i < (arcane ? 9 : 81); i++) {
            te.getRecipe()
                .setInventorySlotContents(arcane ? arcaneSlot(i) : i, null);
        }
    }

    public void writeNEINBT(NBTTagCompound tag, boolean arcane) {
        if (tag == null || arcane && !supportsArcaneMode()) return;
        setArcaneMode(arcane);
        clear();
        for (int i = 0; i < (arcane ? 9 : 81); i++) {
            int slot = arcane ? arcaneSlot(i) : i;
            ItemStack stack = ItemStack.loadItemStackFromNBT(tag.getCompoundTag(Integer.toString(slot)));
            if (stack != null) craftingSlots[slot].putStack(stack);
        }
    }

}
