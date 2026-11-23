package com.science.gtnl.utils.machine.bee;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;

import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IBeeRoot;

public class BeeBreedingInputs {

    private final Map<String, List<ItemStack>> drones = new HashMap<>();
    private final Map<String, List<ItemStack>> queens = new HashMap<>();
    private final Map<ItemStack, Integer> reserved = new IdentityHashMap<>();
    private final Set<String> species = new LinkedHashSet<>();
    private int reservedQueens;
    private int queenCount;

    public record ParentPair(ItemStack queen, ItemStack drone) {}

    public BeeBreedingInputs(List<ItemStack> inputs) {
        IBeeRoot root = BeeBreedingHelper.getBeeRoot();
        if (root == null) return;
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        long count = 0;
        for (ItemStack stack : inputs) {
            if (stack == null || stack.stackSize <= 0 || !seen.add(stack) || !root.isMember(stack)) continue;
            EnumBeeType type = root.getType(stack);
            if (type != EnumBeeType.DRONE && type != EnumBeeType.PRINCESS && type != EnumBeeType.QUEEN) continue;
            String uid = BeeBreedingHelper.getBeeUID(stack);
            if (uid == null || uid.isEmpty()) continue;
            species.add(uid);
            if (type == EnumBeeType.PRINCESS) continue;
            Map<String, List<ItemStack>> inventory = type == EnumBeeType.DRONE ? drones : queens;
            inventory.computeIfAbsent(uid, ignored -> new ArrayList<>())
                .add(stack);
            if (type != EnumBeeType.DRONE) count += stack.stackSize;
        }
        queenCount = (int) Math.min(Integer.MAX_VALUE, count);
    }

    public Set<String> getSpecies() {
        return Collections.unmodifiableSet(species);
    }

    public int getQueenCount() {
        return queenCount;
    }

    public Set<String> getDroneSpecies() {
        Set<String> available = new LinkedHashSet<>();
        drones.forEach((uid, stacks) -> { if (available(stacks) != null) available.add(uid); });
        return Set.copyOf(available);
    }

    public Set<String> getQueenSpecies() {
        Set<String> available = new LinkedHashSet<>();
        queens.forEach((uid, stacks) -> { if (available(stacks) != null) available.add(uid); });
        return Set.copyOf(available);
    }

    public ParentPair findPair(String parent1, String parent2) {
        ParentPair pair = match(parent1, parent2);
        return pair == null ? match(parent2, parent1) : pair;
    }

    public ParentPair match(String queenSpecies, String droneSpecies) {
        ItemStack drone = available(drones.get(droneSpecies));
        if (drone == null) return null;
        ItemStack queen = available(queens.get(queenSpecies));
        return queen == null ? null : new ParentPair(queen, drone);
    }

    private ItemStack available(List<ItemStack> stacks) {
        if (stacks == null) return null;
        for (ItemStack stack : stacks) {
            if (stack.stackSize > reserved.getOrDefault(stack, 0)) return stack;
        }
        return null;
    }

    public void reserve(ParentPair pair, boolean consumeDrone) {
        reserved.merge(pair.queen(), 1, Integer::sum);
        reservedQueens++;
        if (consumeDrone) reserved.merge(pair.drone(), 1, Integer::sum);
    }

    public void commit() {
        reserved.forEach((stack, count) -> stack.stackSize -= count);
        reserved.clear();
        queenCount -= reservedQueens;
        reservedQueens = 0;
    }
}
