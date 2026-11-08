package com.science.gtnl.utils;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.ItemList;
import lombok.Getter;

public final class DireCraftingPatternDetails implements ICraftingPatternDetails {

    private final ItemStack pattern;
    private final IAEItemStack patternKey;
    private final IAEItemStack[] inputs;
    private final IAEItemStack[] condensedInputs;
    private final IAEItemStack[] outputs;
    @Getter
    private final int multiply;

    public DireCraftingPatternDetails(ItemStack is) {
        this(is, 1);
    }

    public DireCraftingPatternDetails(ItemStack is, int multiply) {
        this(readInputs(is), readOutputs(is), multiply);
    }

    public DireCraftingPatternDetails(ICraftingPatternDetails is) {
        this(is, 1);
    }

    public DireCraftingPatternDetails(ICraftingPatternDetails is, int multiply) {
        this(is.getAEInputs(), is.getCondensedAEOutputs(), multiply);
    }

    private DireCraftingPatternDetails(IAEStack<?>[] inputs, IAEStack<?>[] outputs, int multiply) {
        this.multiply = Math.max(1, multiply);
        this.inputs = multiplyStacks(inputs, this.multiply);
        this.outputs = multiplyStacks(outputs, this.multiply);
        var list = new ItemList();
        for (IAEItemStack input : this.inputs) {
            if (input != null) list.addStorage(input);
        }
        condensedInputs = list.toArray(new IAEItemStack[0]);
        pattern = createProcessingPattern(this.inputs, this.outputs);
        patternKey = AEItemStack.create(pattern);
    }

    private static IAEItemStack[] readInputs(ItemStack stack) {
        if (!stack.hasTagCompound()) throw new IllegalArgumentException("No pattern here!");
        IAEItemStack[] inputs = new IAEItemStack[81];
        NBTTagList in = stack.getTagCompound()
            .getTagList("in", Constants.NBT.TAG_COMPOUND);
        if (in.tagCount() > inputs.length) throw new IllegalArgumentException("Too many pattern inputs!");
        for (int i = 0; i < in.tagCount(); i++) {
            inputs[i] = fromTagCreateAEItem(in.getCompoundTagAt(i));
        }
        return inputs;
    }

    private static IAEItemStack[] readOutputs(ItemStack stack) {
        if (!stack.hasTagCompound()) throw new IllegalArgumentException("No pattern here!");
        return new IAEItemStack[] { fromTagCreateAEItem(
            stack.getTagCompound()
                .getCompoundTag("out")) };
    }

    private static ItemStack createProcessingPattern(IAEItemStack[] inputs, IAEItemStack[] outputs) {
        ItemStack pattern = AEApi.instance()
            .definitions()
            .items()
            .encodedUltimatePattern()
            .maybeStack(1)
            .get();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("in", writeStacks(inputs));
        tag.setTag("out", writeStacks(outputs));
        tag.setBoolean("crafting", false);
        tag.setBoolean("substitute", false);
        tag.setBoolean("beSubstitute", false);
        pattern.setTagCompound(tag);
        return pattern;
    }

    private static NBTTagList writeStacks(IAEItemStack[] stacks) {
        NBTTagList list = new NBTTagList();
        for (IAEItemStack stack : stacks) {
            list.appendTag(stack == null ? new NBTTagCompound() : stack.toNBTGeneric());
        }
        return list;
    }

    private static IAEItemStack[] multiplyStacks(IAEStack<?>[] stacks, int multiply) {
        IAEItemStack[] copy = new IAEItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            IAEItemStack stack = Platform.stackConvert(stacks[i]);
            if (stacks[i] != null && stack == null) {
                throw new IllegalArgumentException("Unsupported assembler pattern stack type!");
            }
            if (stack != null) {
                copy[i] = stack.copy()
                    .setStackSize(multiplyStackSize(stack.getStackSize(), multiply));
            }
        }
        return copy;
    }

    private static long multiplyStackSize(long stackSize, int multiplier) {
        if (stackSize <= 0) return stackSize;
        if (stackSize > Long.MAX_VALUE / multiplier) return Long.MAX_VALUE;
        return stackSize * multiplier;
    }

    private static IAEItemStack fromTagCreateAEItem(final NBTTagCompound tag) {
        if (tag.hasNoTags()) return null;
        if (tag.hasKey("Cnt")) return AEItemStack.loadItemStackFromNBT(tag);
        return AEItemStack.create(Platform.loadItemStackFromNBT(tag));
    }

    @Override
    public ItemStack getPattern() {
        return pattern;
    }

    @Override
    public boolean isValidItemForSlot(int slotIndex, ItemStack itemStack, World world) {
        return false;
    }

    @Override
    public boolean isCraftable() {
        return false;
    }

    @Override
    public IAEItemStack[] getAEInputs() {
        return inputs;
    }

    @Override
    public IAEItemStack[] getCondensedAEInputs() {
        return condensedInputs;
    }

    @Override
    public IAEItemStack[] getCondensedAEOutputs() {
        return outputs;
    }

    @Override
    public IAEItemStack[] getAEOutputs() {
        return outputs;
    }

    @Deprecated
    @Override
    public IAEItemStack[] getInputs() {
        return getAEInputs();
    }

    @Deprecated
    @Override
    public IAEItemStack[] getCondensedInputs() {
        return getCondensedAEInputs();
    }

    @Deprecated
    @Override
    public IAEItemStack[] getCondensedOutputs() {
        return getCondensedAEOutputs();
    }

    @Deprecated
    @Override
    public IAEItemStack[] getOutputs() {
        return getAEOutputs();
    }

    @Override
    public boolean canSubstitute() {
        return false;
    }

    @Override
    public boolean canBeSubstitute() {
        return false;
    }

    @Override
    public ItemStack getOutput(InventoryCrafting craftingInv, World world) {
        return null;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public void setPriority(int priority) {

    }

    @Override
    public int hashCode() {
        return patternKey.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof DireCraftingPatternDetails other)) return false;
        return patternKey.equals(other.patternKey);
    }
}
