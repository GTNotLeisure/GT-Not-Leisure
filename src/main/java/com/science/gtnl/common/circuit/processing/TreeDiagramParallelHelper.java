package com.science.gtnl.common.circuit.processing;

import java.util.List;
import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.science.gtnl.common.circuit.nanite.NaniteEffects;
import com.science.gtnl.common.circuit.nanite.NaniteOutputRoll;
import com.science.gtnl.common.circuit.nanite.NaniteOutputSampler;
import com.science.gtnl.utils.recipes.GTNLParallelHelper;

import gregtech.api.util.VoidProtectionHelper;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class TreeDiagramParallelHelper extends GTNLParallelHelper {

    private final NaniteEffects effects;
    private final Random random;
    private final NaniteOutputSampler sampler = new NaniteOutputSampler();
    private NaniteOutputRoll outputRoll;

    public TreeDiagramParallelHelper(NaniteEffects effects, Random random) {
        this.effects = effects;
        this.random = random;
    }

    public NaniteOutputRoll outputRoll() {
        if (outputRoll == null) outputRoll = sampler.sample(random, currentParallel, effects);
        return outputRoll;
    }

    @Override
    protected int getOutputParallelLimit(int candidate, ItemStack[] items, FluidStack[] fluids) {
        int low = 0;
        int high = candidate;
        while (low < high) {
            int middle = low + (int) (((long) high - low + 1) / 2);
            if (canFit(middle, items, fluids)) low = middle;
            else high = middle - 1;
        }
        return low;
    }

    private boolean canFit(int parallels, ItemStack[] items, FluidStack[] fluids) {
        List<ItemStack> itemBounds = new ObjectArrayList<>(items.length);
        for (int index = 0; index < items.length; index++) {
            ItemStack item = items[index];
            if (item != null) addItemsLong(
                itemBounds,
                item.copy(),
                upperBound(item.stackSize, recipe.getOutputChance(index), parallels));
        }
        List<FluidStack> fluidBounds = new ObjectArrayList<>(fluids.length);
        for (int index = 0; index < fluids.length; index++) {
            FluidStack fluid = fluids[index];
            if (fluid != null) addFluidsLong(
                fluidBounds,
                fluid.copy(),
                upperBound(fluid.amount, recipe.getFluidOutputChance(index), parallels));
        }
        VoidProtectionHelper protection = new VoidProtectionHelper().setMachine(machine, true, true)
            .setItemOutputs(itemBounds.toArray(new ItemStack[0]))
            .setFluidOutputs(fluidBounds.toArray(new FluidStack[0]))
            .setMaxParallel(1)
            .build();
        return protection.getMaxParallel() > 0;
    }

    private long upperBound(int amount, int chance, int parallels) {
        long output = Math.multiplyExact(
            Math.multiplyExact((long) amount, parallels),
            (long) Math.ceil(chance * chanceMultiplier / 10000.0));
        double bonus = effects.main()
            .bonusChance() > 0 ? effects.main()
                .bonusFraction() : 0;
        return Math.addExact(output, (long) Math.ceil(output * bonus));
    }

    @Override
    public void calculateItemOutputs(ItemStack[] items) {
        List<ItemStack> result = new ObjectArrayList<>(items.length);
        for (int index = 0; index < items.length; index++) {
            ItemStack output = items[index];
            if (output != null)
                addItemsLong(result, output.copy(), amount(output.stackSize, recipe.getOutputChance(index)));
        }
        itemOutputs = result.toArray(new ItemStack[0]);
    }

    @Override
    public void calculateFluidOutputs(FluidStack[] fluids) {
        List<FluidStack> result = new ObjectArrayList<>(fluids.length);
        for (int index = 0; index < fluids.length; index++) {
            FluidStack output = fluids[index];
            if (output != null)
                addFluidsLong(result, output.copy(), amount(output.amount, recipe.getFluidOutputChance(index)));
        }
        fluidOutputs = result.toArray(new FluidStack[0]);
    }

    private long amount(int amount, int chance) {
        double multiplier = chance * chanceMultiplier / 10000.0;
        long occurrences = Math.addExact(
            Math.multiplyExact((long) Math.floor(multiplier), currentParallel),
            sampler.binomial(random, currentParallel, multiplier - Math.floor(multiplier)));
        return outputRoll().apply(Math.multiplyExact((long) amount, occurrences));
    }
}
