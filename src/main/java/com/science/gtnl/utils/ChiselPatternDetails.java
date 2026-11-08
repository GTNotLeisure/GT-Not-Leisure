package com.science.gtnl.utils;

import java.util.Collection;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.github.bsideup.jabel.Desugar;
import com.science.gtnl.utils.machine.MachineUtils;

import appeng.api.AEApi;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

@Desugar
public final class ChiselPatternDetails implements ICraftingPatternDetails {

    private final ItemStack stack;
    private final IAEItemStack[] inputs, outputs;

    private ChiselPatternDetails(@NotNull IAEItemStack input, @NotNull IAEItemStack output) {
        this.inputs = new IAEItemStack[] { input };
        this.outputs = new IAEItemStack[] { output };

        stack = AEApi.instance()
            .definitions()
            .items()
            .encodedUltimatePattern()
            .maybeStack(1)
            .get();
        final NBTTagCompound encodedValue = new NBTTagCompound();

        final NBTTagList tagIn = new NBTTagList();
        tagIn.appendTag(input.toNBTGeneric());
        final NBTTagList tagOut = new NBTTagList();
        tagOut.appendTag(output.toNBTGeneric());

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", false);
        encodedValue.setBoolean("substitute", false);
        encodedValue.setBoolean("beSubstitute", false);
        stack.setTagCompound(encodedValue);
    }

    public static boolean addChiselPatterns(@Nullable IAEItemStack input, @Nullable Collection<ItemStack> outputs,
        @NotNull Collection<ChiselPatternDetails> patterns, int parallel) {
        if (input == null) return false;
        if (outputs == null || outputs.isEmpty()) return false;
        parallel = Math.max(1, parallel);
        for (var itemStack : outputs) {
            var out = AEItemStack.create(itemStack);
            if (out != null && !MachineUtils.equlsItemStackAndAEItemStack(input, itemStack)) {
                patterns.add(
                    new ChiselPatternDetails(
                        input.copy()
                            .setStackSize(parallel),
                        out.setStackSize(parallel)));
            }
        }
        return true;
    }

    @Override
    public ItemStack getPattern() {
        return stack;
    }

    @Override
    public boolean isValidItemForSlot(int i, ItemStack itemStack, World world) {
        throw new IllegalStateException("Only crafting recipes supported.");
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
        return inputs;
    }

    @Override
    public IAEItemStack[] getCondensedAEOutputs() {
        return outputs;
    }

    @Override
    public IAEItemStack[] getAEOutputs() {
        return outputs;
    }

    // Required by the current AE2 interface for legacy callers.
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
    public ItemStack getOutput(InventoryCrafting inventoryCrafting, World world) {
        throw new IllegalStateException("Only crafting recipes supported.");
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public void setPriority(int i) {

    }
}
