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
    private final Map<String, List<ItemStack>> females = new HashMap<>();
    private final Map<ItemStack, Integer> reserved = new IdentityHashMap<>();
    private final Set<String> species = new LinkedHashSet<>();
    private final Map<String, ItemStack> specimens = new HashMap<>();
    private final Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private int reservedFemales;
    private int femaleCount;

    public record ParentPair(ItemStack female, ItemStack drone, String femaleSpecies, String droneSpecies) {}

    public BeeBreedingInputs(List<ItemStack> inputs) {
        IBeeRoot root = BeeBreedingHelper.getBeeRoot();
        if (root == null) return;
        for (ItemStack stack : inputs) {
            if (stack == null || stack.stackSize <= 0 || seen.contains(stack) || !root.isMember(stack)) continue;
            EnumBeeType type = root.getType(stack);
            if (type != EnumBeeType.DRONE && type != EnumBeeType.PRINCESS && type != EnumBeeType.QUEEN) continue;
            String uid = BeeBreedingHelper.getBeeUID(stack);
            if (uid == null || uid.isEmpty()) continue;
            addKnownInput(stack, uid, type);
        }
    }

    public void addKnownInput(ItemStack stack, String uid, EnumBeeType type) {
        if (stack.stackSize <= 0 || !seen.add(stack)) return;
        species.add(uid);
        specimens.putIfAbsent(uid, stack);
        Map<String, List<ItemStack>> inventory = type == EnumBeeType.DRONE ? drones : females;
        inventory.computeIfAbsent(uid, ignored -> new ArrayList<>())
            .add(stack);
        if (type != EnumBeeType.DRONE)
            femaleCount = (int) Math.min(Integer.MAX_VALUE, (long) femaleCount + stack.stackSize);
    }

    public ItemStack getSpecimen(String uid) {
        return specimens.get(uid);
    }

    public boolean hasFemale(String uid) {
        return available(females.get(uid)) != null;
    }

    public boolean hasDrone(String uid) {
        return available(drones.get(uid)) != null;
    }

    public Set<String> getSpecies() {
        return Collections.unmodifiableSet(species);
    }

    public int getFemaleCount() {
        return femaleCount;
    }

    public Set<String> getDroneSpecies() {
        Set<String> available = new LinkedHashSet<>();
        drones.forEach((uid, stacks) -> { if (available(stacks) != null) available.add(uid); });
        return Set.copyOf(available);
    }

    public Set<String> getFemaleSpecies() {
        Set<String> available = new LinkedHashSet<>();
        females.forEach((uid, stacks) -> { if (available(stacks) != null) available.add(uid); });
        return Set.copyOf(available);
    }

    public ParentPair findPair(String parent1, String parent2) {
        ParentPair pair = match(parent1, parent2);
        return pair == null ? match(parent2, parent1) : pair;
    }

    public ParentPair match(String femaleSpecies, String droneSpecies) {
        ItemStack drone = available(drones.get(droneSpecies));
        if (drone == null) return null;
        ItemStack female = available(females.get(femaleSpecies));
        return female == null ? null : new ParentPair(female, drone, femaleSpecies, droneSpecies);
    }

    private ItemStack available(List<ItemStack> stacks) {
        if (stacks == null) return null;
        for (ItemStack stack : stacks) {
            if (stack.stackSize > reserved.getOrDefault(stack, 0)) return stack;
        }
        return null;
    }

    public void reserve(ParentPair pair, boolean consumeFemale, boolean consumeDrone) {
        if (consumeFemale) {
            reserved.merge(pair.female(), 1, Integer::sum);
            reservedFemales++;
        }
        if (consumeDrone) reserved.merge(pair.drone(), 1, Integer::sum);
    }

    public void commit() {
        reserved.forEach((stack, count) -> stack.stackSize -= count);
        reserved.clear();
        femaleCount -= reservedFemales;
        reservedFemales = 0;
    }
}
