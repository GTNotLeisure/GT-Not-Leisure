package com.science.gtnl.common.crafting.quantum.ae2;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import com.science.gtnl.common.crafting.quantum.api.crafting.IAssemblerPattern;
import com.science.gtnl.common.crafting.quantum.api.stacks.AEKey;
import com.science.gtnl.common.crafting.quantum.api.stacks.GenericStack;
import com.science.gtnl.common.crafting.quantum.api.stacks.KeyCounter;
import com.science.gtnl.common.crafting.quantum.crafting.CraftingEventSimulation;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;

public class QuantumAssemblerPattern extends QuantumPattern implements IAssemblerPattern {

    public QuantumAssemblerPattern(Shape shape, @Nullable StackKey[][] variants) {
        super(shape, variants);
    }

    @Override
    public @Nullable GenericStack assemble(InventoryCrafting input, World level) {
        ItemStack output = getDetails().getOutput(input, level);
        if (output == null) return null;
        return keyOf(CraftingEventSimulation.processCraftingResult(output.copy(), input, level));
    }

    @Override
    public List<GenericStack> getRemainingItems(InventoryCrafting input) {
        var result = new ArrayList<GenericStack>();
        for (int slot = 0; slot < input.getSizeInventory(); slot++) {
            var stack = input.getStackInSlot(slot);
            var remaining = stack == null ? null : Platform.getContainerItem(stack);
            var key = keyOf(remaining);
            if (key != null) result.add(key);
        }
        return result;
    }

    @Override
    public void fillCraftingGrid(KeyCounter[] inputs, CraftingGridAccessor grid) {
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i].size() != 1) throw new IllegalArgumentException("Workbench slot requires one exact stack");
            var entry = inputs[i].iterator()
                .next();
            if (!(entry.getKey() instanceof StackKey key) || !(key.getStack() instanceof IAEItemStack item)) {
                throw new IllegalArgumentException("Workbench slot requires an item");
            }
            var stack = item.getItemStack();
            stack.stackSize = Math.toIntExact(entry.getLongValue());
            grid.set(getInputSlot(i), stack);
        }
    }

    @Override
    public int candidatePriority(AEKey key) {
        return key instanceof StackKey stack && stack.getStack() instanceof IAEItemStack item
            && item.getItem()
                .isDamageable() ? item.getItemStack()
                    .getItemDamage() : 0;
    }
}
