package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.gtnhlib.util.data.ItemId;

import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipe.GTRecipe_WithAlt;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class CircuitRecipeDraft {

    private final List<CircuitItemInput> items = new ObjectArrayList<>();
    private final List<CircuitFluidInput> fluids = new ObjectArrayList<>();
    private final List<ItemStack> outputs = new ObjectArrayList<>();
    private final IntList outputChances = new IntArrayList();
    private final List<FluidStack> fluidOutputs = new ObjectArrayList<>();
    private final IntList fluidOutputChances = new IntArrayList();
    private BigInteger totalEnergy = BigInteger.ZERO;
    private int peakEu;
    private int processNodes;
    private boolean nbtSensitive;

    public CircuitRecipeDraft() {}

    public CircuitRecipeDraft(CircuitRecipeDraft original) {
        items.addAll(original.items);
        fluids.addAll(original.fluids);
        for (ItemStack stack : original.outputs) outputs.add(stack.copy());
        outputChances.addAll(original.outputChances);
        for (FluidStack fluid : original.fluidOutputs) fluidOutputs.add(fluid.copy());
        fluidOutputChances.addAll(original.fluidOutputChances);
        totalEnergy = original.totalEnergy;
        peakEu = original.peakEu;
        processNodes = original.processNodes;
        nbtSensitive = original.nbtSensitive;
    }

    public static List<CircuitItemInput> itemInputs(GTRecipe recipe, long runs) {
        List<CircuitItemInput> result = new ObjectArrayList<>();
        for (int index = 0; index < recipe.mInputs.length; index++) {
            ItemStack input = recipe.mInputs[index];
            if (input == null) continue;
            List<ItemId> alternatives = new ObjectArrayList<>();
            int oreId = -1;
            if (recipe instanceof GTRecipe_WithAlt withAlt) {
                if (withAlt.mOreDictIds != null && index < withAlt.mOreDictIds.length)
                    oreId = withAlt.mOreDictIds[index];
                if (withAlt.mOreDictAlt != null && index < withAlt.mOreDictAlt.length
                    && withAlt.mOreDictAlt[index] != null) {
                    for (ItemStack alternative : withAlt.mOreDictAlt[index]) {
                        if (alternative == null) continue;
                        if (alternative.stackSize != input.stackSize) {
                            throw new IllegalArgumentException("Item alternatives have different quantities");
                        }
                        ItemId id = ItemId.create(alternative);
                        if (!alternatives.contains(id)) alternatives.add(id);
                    }
                }
            }
            if (alternatives.isEmpty()) alternatives.add(ItemId.create(input));
            result.add(
                new CircuitItemInput(
                    alternatives,
                    recipe.getInputChance(index) == 0 ? input.stackSize : Math.multiplyExact(input.stackSize, runs),
                    recipe.getInputChance(index),
                    oreId));
        }
        return result;
    }

    public void addRecipeFluids(GTRecipe recipe, long runs) {
        for (int index = 0; index < recipe.mFluidInputs.length; index++) {
            FluidStack input = recipe.mFluidInputs[index];
            if (input == null) continue;
            List<FluidStack> alternatives = new ObjectArrayList<>();
            if (recipe instanceof GTRecipe_WithAlt withAlt && withAlt.mAltFluidInputs != null
                && index < withAlt.mAltFluidInputs.length
                && withAlt.mAltFluidInputs[index] != null) {
                for (FluidStack alternative : withAlt.mAltFluidInputs[index]) {
                    if (alternative == null) continue;
                    if (alternative.amount != input.amount) {
                        throw new IllegalArgumentException("Fluid alternatives have different quantities");
                    }
                    alternatives.add(alternative);
                }
            }
            if (alternatives.isEmpty()) alternatives.add(input);
            fluids.add(
                new CircuitFluidInput(
                    alternatives,
                    recipe.getFluidInputChance(index) == 0 ? input.amount : Math.multiplyExact(input.amount, runs),
                    recipe.getFluidInputChance(index)));
        }
    }

    public void addRecipeCost(GTRecipe recipe, long runs) {
        if (recipe.mDuration <= 0 || recipe.mEUt < 0 || runs <= 0) {
            throw new IllegalArgumentException("Invalid circuit processing cost");
        }
        int eu = Math.max(1, recipe.mEUt);
        totalEnergy = totalEnergy.add(
            BigInteger.valueOf(eu)
                .multiply(BigInteger.valueOf(recipe.mDuration))
                .multiply(BigInteger.valueOf(runs)));
        peakEu = Math.max(peakEu, eu);
        nbtSensitive |= recipe.isNBTSensitive;
    }

    public void addItem(CircuitItemInput input) {
        items.add(input);
    }

    public void addFluid(CircuitFluidInput input) {
        fluids.add(input);
    }

    public void addOutput(ItemStack stack, long amount, int chance) {
        if (amount <= 0) return;
        if (chance == 10000) {
            for (int index = 0; index < outputs.size(); index++) {
                ItemStack existing = outputs.get(index);
                if (outputChances.getInt(index) == 10000 && ItemId.create(existing)
                    .equals(ItemId.create(stack))) {
                    existing.stackSize = Math.toIntExact(Math.addExact(existing.stackSize, amount));
                    return;
                }
            }
        }
        ItemStack copy = stack.copy();
        copy.stackSize = Math.toIntExact(amount);
        outputs.add(copy);
        outputChances.add(chance);
    }

    public void addFluidOutput(FluidStack fluid, long amount, int chance) {
        if (amount <= 0) return;
        if (chance == 10000) {
            for (int index = 0; index < fluidOutputs.size(); index++) {
                FluidStack existing = fluidOutputs.get(index);
                if (fluidOutputChances.getInt(index) == 10000 && existing.isFluidEqual(fluid)) {
                    existing.amount = Math.toIntExact(Math.addExact(existing.amount, amount));
                    return;
                }
            }
        }
        FluidStack copy = fluid.copy();
        copy.amount = Math.toIntExact(amount);
        fluidOutputs.add(copy);
        fluidOutputChances.add(chance);
    }

    public List<CircuitItemInput> items() {
        return Collections.unmodifiableList(items);
    }

    public List<CircuitFluidInput> fluids() {
        return Collections.unmodifiableList(fluids);
    }

    public ItemStack[] outputs() {
        return outputs.stream()
            .map(ItemStack::copy)
            .toArray(ItemStack[]::new);
    }

    public FluidStack[] fluidOutputs() {
        return fluidOutputs.stream()
            .map(FluidStack::copy)
            .toArray(FluidStack[]::new);
    }

    public int[] outputChances() {
        return outputChances.toIntArray();
    }

    public int[] fluidOutputChances() {
        return fluidOutputChances.toIntArray();
    }

    public int duration() {
        BigInteger divisor = BigInteger.valueOf(Math.max(1, peakEu));
        return totalEnergy.add(divisor)
            .subtract(BigInteger.ONE)
            .divide(divisor)
            .intValueExact();
    }

    public int peakEu() {
        return peakEu;
    }

    public int processNodes() {
        return processNodes;
    }

    public void setProcessNodes(int count) {
        processNodes = count;
    }

    public boolean nbtSensitive() {
        return nbtSensitive;
    }
}
