package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import java.util.Collections;
import java.util.List;

import net.minecraftforge.fluids.FluidStack;

import com.github.bsideup.jabel.Desugar;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

@Desugar
public record CircuitFluidInput(List<FluidStack> alternatives, long amount, int chance) {

    public CircuitFluidInput {
        if (alternatives.isEmpty() || amount < 0) throw new IllegalArgumentException("Invalid circuit fluid input");
        alternatives = copyFluids(alternatives);
    }

    @Override
    public List<FluidStack> alternatives() {
        return copyFluids(alternatives);
    }

    private static List<FluidStack> copyFluids(List<FluidStack> fluids) {
        List<FluidStack> copies = new ObjectArrayList<>(fluids.size());
        for (FluidStack fluid : fluids) {
            FluidStack copy = fluid.copy();
            copy.amount = 1;
            copies.add(copy);
        }
        return Collections.unmodifiableList(copies);
    }
}
