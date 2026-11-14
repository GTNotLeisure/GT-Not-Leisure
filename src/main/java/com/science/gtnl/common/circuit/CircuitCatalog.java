package com.science.gtnl.common.circuit;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizon.gtnhlib.util.data.ItemId;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class CircuitCatalog {

    public static final CircuitCatalog INSTANCE = new CircuitCatalog();

    private static final Materials[] TIERS = { Materials.ULV, Materials.LV, Materials.MV, Materials.HV, Materials.EV,
        Materials.IV, Materials.LuV, Materials.ZPM, Materials.UV, Materials.UHV, Materials.UEV, Materials.UIV,
        Materials.UMV, Materials.UXV, Materials.MAX };

    private final Map<String, CircuitFamily> familiesById = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<ItemId, CircuitTarget> targetsByItem = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<ItemId, CircuitFamily> familiesByUnlockItem = new Object2ObjectLinkedOpenHashMap<>();
    private final Set<String> craftableFamilies = new ObjectOpenHashSet<>();
    private List<CircuitFamily> visibleFamilies = Collections.emptyList();
    private boolean defaultsRegistered;

    public void registerDefaults() {
        if (defaultsRegistered) return;
        registerSeries(
            "processor",
            2,
            ItemList.Circuit_Processor,
            ItemList.Circuit_Advanced,
            ItemList.Circuit_Data,
            ItemList.Circuit_Elite);
        registerSeries(
            "nano",
            3,
            ItemList.Circuit_Nanoprocessor,
            ItemList.Circuit_Nanocomputer,
            ItemList.Circuit_Elitenanocomputer,
            ItemList.Circuit_Master);
        registerSeries(
            "quantum",
            4,
            ItemList.Circuit_Quantumprocessor,
            ItemList.Circuit_Quantumcomputer,
            ItemList.Circuit_Masterquantumcomputer,
            ItemList.Circuit_Quantummainframe);
        registerSeries(
            "crystal",
            5,
            ItemList.Circuit_Crystalprocessor,
            ItemList.Circuit_Crystalcomputer,
            ItemList.Circuit_Ultimatecrystalcomputer,
            ItemList.Circuit_Crystalmainframe);
        registerSeries(
            "wetware",
            6,
            ItemList.Circuit_Neuroprocessor,
            ItemList.Circuit_Wetwarecomputer,
            ItemList.Circuit_Wetwaresupercomputer,
            ItemList.Circuit_Wetwaremainframe);
        registerSeries(
            "bio",
            7,
            ItemList.Circuit_Bioprocessor,
            ItemList.Circuit_Biowarecomputer,
            ItemList.Circuit_Biowaresupercomputer,
            ItemList.Circuit_Biomainframe);
        registerSeries(
            "optical",
            8,
            ItemList.Circuit_OpticalProcessor,
            ItemList.Circuit_OpticalAssembly,
            ItemList.Circuit_OpticalComputer,
            ItemList.Circuit_OpticalMainframe);
        defaultsRegistered = true;
    }

    private void registerSeries(String id, int firstTier, ItemList... items) {
        List<ItemId> members = new ObjectArrayList<>(items.length);
        for (ItemList item : items) {
            if (!item.hasBeenSet()) return;
            members.add(ItemId.create(item.get(1)));
        }
        CircuitFamily family = new CircuitFamily(
            id,
            members.get(members.size() - 1),
            firstTier + items.length - 1,
            members);
        registerFamily(family);
        for (int index = 0; index < members.size(); index++) {
            registerTarget(new CircuitTarget(members.get(index), firstTier + index, id));
        }
    }

    public void registerFamily(CircuitFamily family) {
        CircuitFamily previous = familiesById.putIfAbsent(family.id(), family);
        if (previous != null && !previous.equals(family)) {
            throw new IllegalArgumentException("Conflicting circuit family: " + family.id());
        }
        CircuitFamily existing = familiesByUnlockItem.putIfAbsent(family.unlockItem(), family);
        if (existing != null && !existing.id()
            .equals(family.id())) {
            throw new IllegalArgumentException("Circuit unlock item belongs to multiple families");
        }
    }

    public CircuitTarget registerTarget(ItemStack stack) {
        ItemId item = ItemId.create(stack);
        CircuitTarget existing = targetsByItem.get(item);
        if (existing != null) return existing;
        int tier = circuitTier(stack);
        if (tier < 0) return null;
        String id = "item/" + CircuitIdentity.digest(CircuitIdentity.item(stack));
        registerFamily(new CircuitFamily(id, item, tier, Collections.singletonList(item)));
        CircuitTarget target = new CircuitTarget(item, tier, id);
        registerTarget(target);
        return target;
    }

    public void registerTarget(CircuitTarget target) {
        CircuitFamily family = familiesById.get(target.familyId());
        if (family == null || !family.members()
            .contains(target.item())) {
            throw new IllegalArgumentException("Circuit target has no matching family: " + target.familyId());
        }
        CircuitTarget previous = targetsByItem.putIfAbsent(target.item(), target);
        if (previous != null && !previous.equals(target))
            throw new IllegalArgumentException("Conflicting circuit target");
    }

    public static int circuitTier(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return -1;
        ItemData association = GTOreDictUnificator.getAssociation(stack);
        if (association != null && association.mPrefix == OrePrefixes.circuit && association.mMaterial != null) {
            for (int tier = 0; tier < TIERS.length; tier++) {
                if (association.mMaterial.mMaterial == TIERS[tier]) return tier;
            }
        }
        for (int ore : OreDictionary.getOreIDs(stack)) {
            String name = OreDictionary.getOreName(ore);
            for (int tier = 0; tier < TIERS.length; tier++) {
                if (name.equals(
                    OrePrefixes.circuit.get(TIERS[tier])
                        .toString()))
                    return tier;
            }
        }
        return -1;
    }

    public CircuitTarget target(ItemStack stack) {
        return stack == null ? null : targetsByItem.get(ItemId.create(stack));
    }

    public CircuitFamily family(String id) {
        return familiesById.get(id);
    }

    public CircuitFamily unlockFamily(ItemStack stack) {
        if (stack == null) return null;
        CircuitFamily family = familiesByUnlockItem.get(ItemId.create(stack));
        return family != null && craftableFamilies.contains(family.id()) ? family : null;
    }

    public void markCraftable(CircuitTarget target) {
        craftableFamilies.add(target.familyId());
    }

    public void finishRegistration() {
        List<CircuitFamily> families = new ObjectArrayList<>();
        for (CircuitFamily family : familiesById.values()) {
            if (craftableFamilies.contains(family.id())) families.add(family);
        }
        families.sort(
            Comparator.comparingInt(CircuitFamily::unlockTier)
                .thenComparing(CircuitFamily::id));
        visibleFamilies = Collections.unmodifiableList(families);
    }

    public List<CircuitFamily> families() {
        return visibleFamilies;
    }
}
