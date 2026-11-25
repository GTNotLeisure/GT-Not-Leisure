package com.science.gtnl.common.crafting.quantum.api.crafting;

import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import com.science.gtnl.common.crafting.quantum.api.stacks.AEKey;
import com.science.gtnl.common.crafting.quantum.api.stacks.GenericStack;
import com.science.gtnl.common.crafting.quantum.api.stacks.KeyCounter;

public interface IAssemblerPattern extends IPatternDetails {

    public @Nullable GenericStack assemble(InventoryCrafting input, World level);

    public List<GenericStack> getRemainingItems(InventoryCrafting input);

    public void fillCraftingGrid(KeyCounter[] inputs, CraftingGridAccessor grid);

    public default boolean acceptsCandidate(IInput input, AEKey key, World level) {
        return input.isValid(key, level);
    }

    public default int candidatePriority(AEKey key) {
        return 0;
    }

    public @FunctionalInterface interface CraftingGridAccessor {

        public void set(int slot, ItemStack stack);
    }
}
