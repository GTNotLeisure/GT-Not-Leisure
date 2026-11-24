package com.science.gtnl.utils.machine.bee;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import forestry.api.apiculture.EnumBeeType;

public class BeeSeedBank {

    private final Map<String, ItemStack> seeds = new LinkedHashMap<>();
    private final List<ItemStack> breedingInputs = new ArrayList<>();
    private final Map<ItemStack, BeeIdentity> identities = new IdentityHashMap<>();
    private int revision;

    private record BeeIdentity(String uid, EnumBeeType type) {}

    public boolean retain(ItemStack bee) {
        String uid = BeeBreedingHelper.getBeeUID(bee);
        if (uid == null || seeds.containsKey(uid)) return false;
        ItemStack seed = BeeBreedingHelper.copyBeeAsType(bee, EnumBeeType.PRINCESS);
        if (seed == null) return false;
        seeds.put(uid, seed);
        revision++;
        return true;
    }

    public int getRevision() {
        return revision;
    }

    public boolean hasSpecies(String uid) {
        return seeds.containsKey(uid);
    }

    public Set<String> getSpecies() {
        return Collections.unmodifiableSet(seeds.keySet());
    }

    public ItemStack createSeed(String uid, EnumBeeType type) {
        ItemStack seed = seeds.get(uid);
        return seed == null ? null : BeeBreedingHelper.copyBeeAsType(seed, type);
    }

    public void addBreedingInput(ItemStack bee) {
        ItemStack input = bee.copy();
        breedingInputs.add(input);
        identities.put(
            input,
            new BeeIdentity(
                BeeBreedingHelper.getBeeUID(input),
                BeeBreedingHelper.getBeeRoot()
                    .getType(input)));
    }

    public List<ItemStack> getBreedingInputs() {
        return breedingInputs.stream()
            .map(ItemStack::copy)
            .toList();
    }

    public void addToInputs(BeeBreedingInputs inputs) {
        for (ItemStack bee : breedingInputs) {
            BeeIdentity identity = identities.get(bee);
            inputs.addKnownInput(bee, identity.uid, identity.type);
        }
    }

    public boolean ownsInput(ItemStack bee) {
        return identities.containsKey(bee);
    }

    public void updateInputs() {
        breedingInputs.removeIf(bee -> {
            if (bee.stackSize > 0) return false;
            identities.remove(bee);
            return true;
        });
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList templates = new NBTTagList();
        for (ItemStack seed : seeds.values()) templates.appendTag(seed.writeToNBT(new NBTTagCompound()));
        tag.setTag("seeds", templates);
        NBTTagList inputs = new NBTTagList();
        for (ItemStack input : breedingInputs)
            if (input.stackSize > 0) inputs.appendTag(input.writeToNBT(new NBTTagCompound()));
        tag.setTag("inputs", inputs);
        return tag;
    }

    public static BeeSeedBank fromNBT(NBTTagCompound tag) {
        BeeSeedBank bank = new BeeSeedBank();
        NBTTagList templates = tag.getTagList("seeds", 10);
        for (int i = 0; i < templates.tagCount(); i++) {
            ItemStack seed = ItemStack.loadItemStackFromNBT(templates.getCompoundTagAt(i));
            if (seed != null) bank.retain(seed);
        }
        NBTTagList inputs = tag.getTagList("inputs", 10);
        for (int i = 0; i < inputs.tagCount(); i++) {
            ItemStack input = ItemStack.loadItemStackFromNBT(inputs.getCompoundTagAt(i));
            if (input != null && input.stackSize > 0) bank.addBreedingInput(input);
        }
        return bank;
    }
}
