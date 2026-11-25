package com.science.gtnl.common.crafting.quantum.ae2;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;

import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

import com.gtnllib.utils.LongMath;
import com.science.gtnl.common.crafting.quantum.api.stacks.AEKey;
import com.science.gtnl.common.crafting.quantum.api.stacks.KeyCounter;
import com.science.gtnl.common.crafting.quantum.crafting.CraftingAssemblerSimulation;
import com.science.gtnl.common.crafting.quantum.crafting.inv.ICraftingInventory;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.MECraftingInventory;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;

public class QuantumAssemblerInputs {

    private StackKeyFactory keys = new StackKeyFactory();
    private final Map<ICraftingPatternDetails, CraftingAssemblerSimulation> simulations = new Object2ObjectOpenHashMap<>();
    private final Map<ICraftingPatternDetails, QuantumAssemblerPattern> patterns = new Object2ObjectOpenHashMap<>();
    private @Nullable World world;
    private @Nullable ICraftingPatternDetails preparedPattern;
    private @Nullable CraftingAssemblerSimulation.Result prepared;

    public boolean select(ICraftingPatternDetails details, MECraftingInventory inventory, World level) {
        return select(details, inventory, level, ignored -> null);
    }

    public boolean select(ICraftingPatternDetails details, MECraftingInventory inventory, World level,
        Function<IAEStack<?>, IItemList<IAEStack<?>>> budgetLookup) {
        if (this.world != level) {
            clear();
            this.world = level;
        }
        var pattern = this.patterns.computeIfAbsent(
            details,
            key -> new QuantumAssemblerPattern(new QuantumPattern.Shape(key, null, this.keys, level), null));
        var simulation = this.simulations
            .computeIfAbsent(details, key -> new CraftingAssemblerSimulation(pattern, level));
        this.preparedPattern = details;
        this.prepared = simulation.select(view(inventory), false, inputBudgets(details, pattern, budgetLookup));
        return this.prepared != null;
    }

    private KeyCounter @Nullable [] inputBudgets(ICraftingPatternDetails details, QuantumAssemblerPattern pattern,
        Function<IAEStack<?>, IItemList<IAEStack<?>>> budgetLookup) {
        var encoded = details.getAEInputs();
        var limits = new KeyCounter[pattern.getInputs().length];
        var groups = new Reference2ObjectOpenHashMap<IItemList<IAEStack<?>>, KeyCounter>();
        boolean restricted = false;
        for (int input = 0; input < limits.length; input++) {
            var stack = encoded[pattern.getInputSlot(input)];
            var budget = budgetLookup.apply(stack);
            if (budget == null) continue;
            var remaining = groups.get(budget);
            if (remaining == null) {
                remaining = KeyCounter.checked();
                for (var variant : budget) {
                    if (variant.getStackSize() <= 0) continue;
                    var key = this.keys.of(variant);
                    if (key != null) remaining.add(key, variant.getStackSize());
                }
                groups.put(budget, remaining);
            }
            long total = 0;
            for (var entry : remaining) total = LongMath.saturatedAdd(total, entry.getLongValue());
            // Preserve the legacy fallback after a slot's planned budget is exhausted.
            if (total < stack.getStackSize()) continue;
            limits[input] = remaining;
            restricted = true;
        }
        return restricted ? limits : null;
    }

    public boolean isPrepared(ICraftingPatternDetails details) {
        return details.equals(this.preparedPattern) && this.prepared != null;
    }

    public ArrayList<IAEStack<?>> extractSlot(ICraftingPatternDetails details, int slot,
        MECraftingInventory inventory) {
        var result = new ArrayList<IAEStack<?>>(1);
        if (!isPrepared(details)) return result;
        var pattern = this.patterns.get(details);
        for (int input = 0; input < this.prepared.inputs()
            .size(); input++) {
            if (pattern.getInputSlot(input) != slot) continue;
            var options = this.prepared.inputs()
                .get(input);
            if (options.size() != 1 || !(options.getFirst()
                .what() instanceof StackKey key) || !(key.getStack() instanceof IAEItemStack item)) return result;
            var request = item.copy();
            request.setStackSize(
                options.getFirst()
                    .amount());
            var available = inventory.extractItems(request, Actionable.SIMULATE);
            if (available != null && available.getStackSize() == request.getStackSize()) result.add(available);
            return result;
        }
        return result;
    }

    public boolean returnsContainers(ICraftingPatternDetails details) {
        return isPrepared(details) && !this.prepared.returned()
            .isEmpty();
    }

    public void clear() {
        this.simulations.clear();
        this.patterns.clear();
        this.world = null;
        this.preparedPattern = null;
        this.prepared = null;
        this.keys = new StackKeyFactory();
    }

    private ICraftingInventory view(MECraftingInventory inventory) {
        return new ICraftingInventory() {

            @Override
            public void insert(AEKey key, long amount, Actionable mode) {
                throw new UnsupportedOperationException("Grid selection only simulates extraction");
            }

            @Override
            public long extract(AEKey key, long amount, Actionable mode) {
                if (mode != Actionable.SIMULATE)
                    throw new UnsupportedOperationException("Grid selection only simulates extraction");
                var stack = ((StackKey) key).getStack();
                if (!(stack instanceof IAEItemStack item)) return 0;
                var request = item.copy();
                request.setStackSize(amount);
                var available = inventory.extractItems(request, Actionable.SIMULATE);
                return available == null ? 0 : available.getStackSize();
            }

            @Override
            public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
                var result = new ArrayList<AEKey>();
                var stack = ((StackKey) key).getStack();
                if (stack instanceof IAEItemStack item) {
                    for (var candidate : inventory.findFuzzy(item, FuzzyMode.IGNORE_ALL)) {
                        var adapted = keys.of(candidate);
                        if (adapted != null) result.add(adapted);
                    }
                } else result.add(key);
                return result;
            }
        };
    }
}
