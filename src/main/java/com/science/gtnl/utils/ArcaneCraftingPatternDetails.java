package com.science.gtnl.utils;

import java.util.Arrays;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import com.science.gtnl.common.recipe.thaumcraft.ArcaneEssentiaPayment;
import com.science.gtnl.common.recipe.thaumcraft.ArcanePatternRecipes;
import com.science.gtnl.compat.thaumicenergistics.InfusionProviders;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.inv.MEInventoryCrafting;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import thaumcraft.api.aspects.Aspect;

public final class ArcaneCraftingPatternDetails extends DireCraftingPatternDetails {

    @Getter
    private final Object2LongMap<Aspect> essentia;
    @Getter
    private final boolean networkSupplied;
    private final IAEStack<?>[] ingredients;
    private final IAEStack<?>[] condensedIngredients;

    public ArcaneCraftingPatternDetails(ItemStack stack, int multiply, boolean matrix, NBTTagCompound cost) {
        super(
            readArcaneInputs(stack),
            readOutputs(stack),
            multiply,
            identity(stack, multiply, matrix),
            matrix ? routingKey(stack, cost, multiply) : null);
        if (super.getCondensedAEInputs().length == 0 || getAEOutputs()[0] == null
            || getAEOutputs()[0].getStackSize() <= 0) {
            throw new IllegalArgumentException("Missing arcane pattern inputs or output");
        }
        var scaled = new Object2LongLinkedOpenHashMap<Aspect>();
        for (String key : cost.func_150296_c()) {
            Aspect aspect = Aspect.getAspect(key);
            int amount = cost.getInteger(key);
            if (aspect == null || amount <= 0) throw new IllegalArgumentException("Invalid essentia cost: " + key);
            scaled.put(aspect, (long) amount * getMultiply());
        }
        essentia = Object2LongMaps.unmodifiable(scaled);
        networkSupplied = ArcanePatternRecipes.writesEssentia(stack.getTagCompound());
        var extra = networkSupplied ? readEssentia(stack.getTagCompound()) : new IAEStack<?>[0];
        ingredients = append(super.getAEInputs(), extra);
        condensedIngredients = append(super.getCondensedAEInputs(), extra);
    }

    private IAEStack<?>[] readEssentia(NBTTagCompound tag) {
        var extra = new IAEStack<?>[essentia.size()];
        if (!essentia.isEmpty() && !InfusionProviders.isAvailable())
            throw new IllegalArgumentException("Essentia storage is unavailable");
        var list = ArcanePatternRecipes.inputs(tag);
        if (list.tagCount() > 9) {
            for (int i = 0; i < extra.length; i++) {
                extra[i] = InfusionProviders.loadIngredient(list.getCompoundTagAt(9 + i));
                if (extra[i] == null) throw new IllegalArgumentException("Invalid essentia ingredient");
                extra[i].setStackSize(Math.multiplyExact(extra[i].getStackSize(), (long) getMultiply()));
            }
        } else {
            int i = 0;
            for (var entry : Object2LongMaps.fastIterable(essentia))
                extra[i++] = InfusionProviders.nativeIngredient(entry.getKey(), entry.getLongValue());
        }
        return extra;
    }

    public boolean hasExpectedDelivery(InventoryCrafting table, long crafts) {
        if (!(table instanceof MEInventoryCrafting me)) return !networkSupplied || essentia.isEmpty();
        var delivered = new Object2LongOpenHashMap<Aspect>();
        for (int i = 0; i < me.getSizeInventory(); i++) {
            var input = me.getAEStackInSlot(i);
            if (input == null || input instanceof IAEItemStack) continue;
            var aspect = InfusionProviders.aspectOf(input);
            if (aspect == null) continue;
            long count = input.getStackSize();
            if (count <= 0 || Long.MAX_VALUE - delivered.getLong(aspect) < count) return false;
            delivered.addTo(aspect, count);
        }
        return networkSupplied ? ArcaneEssentiaPayment.matchesDelivery(essentia, crafts, delivered)
            : delivered.isEmpty();
    }

    private static IAEStack<?>[] append(IAEStack<?>[] items, IAEStack<?>[] extra) {
        if (extra.length == 0) return items;
        var all = Arrays.copyOf(items, items.length + extra.length, IAEStack[].class);
        System.arraycopy(extra, 0, all, items.length, extra.length);
        return all;
    }

    @Override
    public IAEStack<?>[] getAEInputs() {
        return ingredients;
    }

    @Override
    public IAEStack<?>[] getCondensedAEInputs() {
        return condensedIngredients;
    }

    private static IAEItemStack[] readArcaneInputs(ItemStack stack) {
        if (!stack.hasTagCompound()) throw new IllegalArgumentException("Missing arcane pattern");
        IAEItemStack[] inputs = new IAEItemStack[9];
        var tag = stack.getTagCompound();
        var slots = ArcanePatternRecipes.inputs(tag);
        if (slots.tagCount() < 9) throw new IllegalArgumentException("Invalid arcane crafting grid");
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = fromTagCreateAEItem(slots.getCompoundTagAt(i));
            if (!slots.getCompoundTagAt(i)
                .hasNoTags() && (inputs[i] == null || inputs[i].getStackSize() != 1)) {
                throw new IllegalArgumentException("Invalid arcane ingredient in slot " + i);
            }
        }
        return inputs;
    }

    static ItemStack routingKey(ItemStack stack, NBTTagCompound cost, int multiply) {
        var source = stack.getTagCompound();
        if (source == null) throw new IllegalArgumentException("Missing arcane pattern");
        var inputs = ArcanePatternRecipes.inputs(source);
        var grid = new NBTTagList();
        for (int i = 0; i < 9; i++) grid.appendTag(
            inputs.getCompoundTagAt(i)
                .copy());
        var tag = new NBTTagCompound();
        tag.setTag("in", grid);
        tag.setTag(
            "out",
            source.getCompoundTag("out")
                .copy());
        tag.setTag("recipeCost", cost.copy());
        tag.setBoolean("writeEssentia", ArcanePatternRecipes.writesEssentia(stack.getTagCompound()));
        tag.setInteger("matrixMultiply", Math.max(1, multiply));
        var key = new ItemStack(stack.getItem(), 1, stack.getItemDamage());
        key.setTagCompound(tag);
        return key;
    }

    static ItemStack identity(ItemStack stack, int multiply, boolean matrix) {
        ItemStack pattern = stack.copy();
        pattern.stackSize = 1;
        if (!matrix) return pattern;
        NBTTagCompound tag = pattern.getTagCompound();
        tag.removeTag(ArcanePatternRecipes.STALE);
        tag.setInteger("matrixMultiply", Math.max(1, multiply));
        return pattern;
    }
}
