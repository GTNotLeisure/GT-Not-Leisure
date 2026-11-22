package com.science.gtnl.utils.machine.bee;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.oredict.OreDictionary;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.EnumBeeChromosome;
import forestry.api.apiculture.EnumBeeType;
import forestry.api.apiculture.IAlleleBeeSpecies;
import forestry.api.apiculture.IBee;
import forestry.api.apiculture.IBeeGenome;
import forestry.api.apiculture.IBeeMutation;
import forestry.api.apiculture.IBeeRoot;
import forestry.api.genetics.AlleleManager;
import forestry.api.genetics.IAllele;
import forestry.api.genetics.IMutationCondition;
import forestry.core.genetics.mutations.Mutation;
import forestry.core.genetics.mutations.MutationConditionRequiresResource;
import forestry.core.genetics.mutations.MutationConditionRequiresResourceOreDict;
import forestry.core.utils.InventoryUtil;

public class BeeBreedingHelper {

    private static final Random RANDOM = new Random();

    private static volatile Catalog catalog;

    private record Catalog(IBeeRoot root, int templateCount, int mutationCount, int alleleCount,
        Map<String, IAlleleBeeSpecies> species, List<IAlleleBeeSpecies> registered,
        Map<String, List<MutationData>> mutationsByResult, List<MutationData> mutations,
        Map<String, IAllele[]> templates) {}

    private BeeBreedingHelper() {}

    public static IBeeRoot getBeeRoot() {
        return BeeManager.beeRoot;
    }

    public static boolean isBee(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        IBeeRoot root = getBeeRoot();
        return root != null && root.isMember(stack);
    }

    public static boolean isPrincess(ItemStack stack) {
        if (!isBee(stack)) return false;
        return getBeeRoot().getType(stack) == EnumBeeType.PRINCESS;
    }

    public static boolean isDrone(ItemStack stack) {
        if (!isBee(stack)) return false;
        return getBeeRoot().getType(stack) == EnumBeeType.DRONE;
    }

    public static String getBeeUID(ItemStack stack) {
        if (!isBee(stack)) return null;
        IBee bee = getBeeRoot().getMember(stack);
        if (bee == null || bee.getGenome() == null) return null;
        IAlleleBeeSpecies species = bee.getGenome()
            .getPrimary();
        return species != null ? species.getUID() : null;
    }

    public static boolean isQueen(ItemStack stack) {
        return isBee(stack) && getBeeRoot().getType(stack) == EnumBeeType.QUEEN;
    }

    public static boolean isFemale(ItemStack stack) {
        if (!isBee(stack)) return false;
        EnumBeeType type = getBeeRoot().getType(stack);
        return type == EnumBeeType.PRINCESS || type == EnumBeeType.QUEEN;
    }

    public static String getSpeciesDisplayName(String input) {
        if (input == null || input.isEmpty()) return "";
        IAlleleBeeSpecies species = getSpeciesByUID(input);
        if (species == null) species = getSpeciesByName(input);
        return BeeSpeciesSearch.displayName(species == null ? input : species.getUID());
    }

    public static String getCanonicalUID(String value) {
        if (value == null || value.isEmpty()) return value;
        IAlleleBeeSpecies species = getSpeciesByUID(value);
        if (species == null) species = getSpeciesByName(value);
        return species == null ? value : species.getUID();
    }

    public static IAlleleBeeSpecies getSpeciesByUID(String uid) {
        if (uid == null || uid.isEmpty()) return null;
        Catalog cache = ensureCaches();
        return cache == null ? null
            : cache.species()
                .get(uid);
    }

    public static IAlleleBeeSpecies getSpeciesByName(String name) {
        if (name == null || name.isEmpty() || getBeeRoot() == null) return null;
        return BeeSpeciesSearch.resolve(name);
    }

    public static boolean tryMutation(double chance) {
        return RANDOM.nextDouble() * 100 < chance;
    }

    public static List<MutationData> getMutationsForUID(String uid) {
        Catalog cache = ensureCaches();
        return cache == null ? new ArrayList<>()
            : new ArrayList<>(
                cache.mutationsByResult()
                    .getOrDefault(uid, List.of()));
    }

    public static List<IAlleleBeeSpecies> getRegisteredSpecies() {
        Catalog cache = ensureCaches();
        return cache == null ? List.of() : cache.registered();
    }

    public static List<MutationData> getAllMutations() {
        Catalog cache = ensureCaches();
        return cache == null ? List.of() : cache.mutations();
    }

    public static void invalidateCaches() {
        catalog = null;
    }

    public static BreedingStep findRegisteredStep(String parent1, String parent2, String result, double chance) {
        for (MutationData mutation : getMutationsForUID(result)) {
            if (Double.compare(mutation.chance, chance) != 0) continue;
            if ((mutation.parent1.equals(parent1) && mutation.parent2.equals(parent2))
                || (mutation.parent1.equals(parent2) && mutation.parent2.equals(parent1))) {
                return new BreedingStep(parent1, parent2, result, chance, mutation.foundations);
            }
        }
        return null;
    }

    private static List<FoundationRequirement> readFoundations(IBeeMutation mutation) {
        if (!(mutation instanceof Mutation forestryMutation)) return Collections.emptyList();
        List<FoundationRequirement> requirements = new ArrayList<>();
        for (IMutationCondition condition : forestryMutation.getMutationConditions()) {
            if (condition instanceof MutationConditionRequiresResource resource) {
                requirements.add(new FoundationRequirement(resource.getBlockRequired(), ""));
            } else if (condition instanceof MutationConditionRequiresResourceOreDict resource) {
                requirements.add(new FoundationRequirement(null, OreDictionary.getOreName(resource.getOreDictId())));
            }
        }
        return Collections.unmodifiableList(requirements);
    }

    public static ItemStack createDrone(String species) {
        return createBee(species, EnumBeeType.DRONE);
    }

    public static ItemStack createPrincess(String species) {
        return createBee(species, EnumBeeType.PRINCESS);
    }

    public static ItemStack createBee(String speciesName, EnumBeeType type) {
        Catalog cache = ensureCaches();
        if (cache == null) return null;
        String uid = getCanonicalUID(speciesName);
        IAlleleBeeSpecies species = cache.species()
            .get(uid);
        if (species == null) return null;
        IAllele[] template = cache.templates()
            .get(uid);
        if (template == null) {
            template = cache.root()
                .getDefaultTemplate()
                .clone();
            template[EnumBeeChromosome.SPECIES.ordinal()] = species;
        }
        IBeeGenome genome = cache.root()
            .templateAsGenome(template);
        IBee bee = cache.root()
            .getBee(null, genome);
        return bee == null ? null
            : cache.root()
                .getMemberStack(bee, type.ordinal());
    }

    private static Catalog ensureCaches() {
        IBeeRoot root = getBeeRoot();
        if (root == null) return null;
        Map<String, IAllele[]> templates = root.getGenomeTemplates();
        var mutations = root.getMutations(false);
        Map<String, IAllele> alleles = AlleleManager.alleleRegistry == null ? Map.of()
            : AlleleManager.alleleRegistry.getRegisteredAlleles();
        Catalog previous = catalog;
        int templateCount = templates == null ? 0 : templates.size();
        if (previous != null && previous.root() == root
            && previous.templateCount() == templateCount
            && previous.mutationCount() == mutations.size()
            && previous.alleleCount() == alleles.size()) return previous;
        Map<String, IAlleleBeeSpecies> species = new HashMap<>();
        Map<String, IAllele[]> genomes = new HashMap<>();
        for (IAllele allele : alleles.values()) registerSpecies(species, allele);
        if (templates != null) for (IAllele[] template : templates.values()) {
            if (template == null || template.length <= EnumBeeChromosome.SPECIES.ordinal()) continue;
            IAllele allele = template[EnumBeeChromosome.SPECIES.ordinal()];
            registerSpecies(species, allele);
            if (allele instanceof IAlleleBeeSpecies bee && species.containsKey(bee.getUID()))
                genomes.put(bee.getUID(), template);
        }
        for (IBeeMutation mutation : mutations) {
            registerSpecies(species, mutation.getAllele0());
            registerSpecies(species, mutation.getAllele1());
            IAllele[] template = mutation.getTemplate();
            if (template == null || template.length <= EnumBeeChromosome.SPECIES.ordinal()) continue;
            IAllele allele = template[EnumBeeChromosome.SPECIES.ordinal()];
            registerSpecies(species, allele);
            if (allele instanceof IAlleleBeeSpecies bee && species.containsKey(bee.getUID()))
                genomes.putIfAbsent(bee.getUID(), template);
        }
        Map<String, List<MutationData>> byResult = new HashMap<>();
        List<MutationData> all = new ArrayList<>();
        for (IBeeMutation mutation : mutations) {
            IAllele[] template = mutation.getTemplate();
            if (template == null || template.length <= EnumBeeChromosome.SPECIES.ordinal()
                || !(template[EnumBeeChromosome.SPECIES.ordinal()] instanceof IAlleleBeeSpecies result)) continue;
            IAllele parent1 = mutation.getAllele0(), parent2 = mutation.getAllele1();
            double chance = mutation.getBaseChance();
            if (parent1 == null || parent2 == null
                || !species.containsKey(parent1.getUID())
                || !species.containsKey(parent2.getUID())
                || !species.containsKey(result.getUID())
                || !Double.isFinite(chance)
                || chance <= 0) continue;
            MutationData data = new MutationData(
                parent1.getUID(),
                parent2.getUID(),
                result.getUID(),
                chance,
                readFoundations(mutation));
            byResult.computeIfAbsent(result.getUID(), ignored -> new ArrayList<>())
                .add(data);
            all.add(data);
        }
        byResult.replaceAll((uid, values) -> List.copyOf(values));
        List<IAlleleBeeSpecies> registered = new ArrayList<>(species.values());
        registered.sort(
            (left, right) -> left.getUID()
                .compareTo(right.getUID()));
        Catalog next = new Catalog(
            root,
            templateCount,
            mutations.size(),
            alleles.size(),
            Map.copyOf(species),
            List.copyOf(registered),
            Map.copyOf(byResult),
            List.copyOf(all),
            Map.copyOf(genomes));
        catalog = next;
        return next;
    }

    private static void registerSpecies(Map<String, IAlleleBeeSpecies> species, IAllele allele) {
        if (allele instanceof IAlleleBeeSpecies bee
            && (AlleleManager.alleleRegistry == null || !AlleleManager.alleleRegistry.isBlacklisted(bee.getUID())))
            species.putIfAbsent(bee.getUID(), bee);
    }

    public static class FoundationRequirement {

        private final ItemStack block;
        private final String oreName;

        private FoundationRequirement(ItemStack block, String oreName) {
            this.block = block == null ? null : block.copy();
            this.oreName = oreName;
        }

        public String key() {
            return !oreName.isEmpty() ? "ore:" + oreName
                : block == null ? "missing"
                    : Item.getIdFromItem(block.getItem()) + ":" + block.getItemDamage() + ":" + block.getTagCompound();
        }

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.stackSize <= 0) return false;
            if (oreName.isEmpty()) return InventoryUtil.isItemEqual(block, stack, true, true);
            int requiredId = OreDictionary.getOreID(oreName);
            for (int id : OreDictionary.getOreIDs(stack)) if (id == requiredId) return true;
            return false;
        }

        public ItemStack findFluidFoundation(Set<Fluid> fluids) {
            if (fluids.isEmpty()) return null;
            for (ItemStack candidate : getAlternatives()) {
                Fluid fluid = FluidRegistry.lookupFluidForBlock(Block.getBlockFromItem(candidate.getItem()));
                if (fluid != null && fluids.contains(fluid)) return candidate;
            }
            return null;
        }

        public List<ItemStack> getAlternatives() {
            List<ItemStack> alternatives = new ArrayList<>();
            List<ItemStack> candidates = oreName.isEmpty() ? Collections.singletonList(block)
                : OreDictionary.getOres(oreName);
            for (ItemStack candidate : candidates) {
                if (candidate == null || candidate.getItem() == null) continue;
                ItemStack copy = candidate.copy();
                copy.stackSize = 1;
                alternatives.add(copy);
            }
            return alternatives;
        }

        public NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            if (!oreName.isEmpty()) tag.setString("ore", oreName);
            else if (block != null) tag.setTag("block", block.writeToNBT(new NBTTagCompound()));
            return tag;
        }

        public static FoundationRequirement fromNBT(NBTTagCompound tag) {
            return new FoundationRequirement(
                tag.hasKey("block") ? ItemStack.loadItemStackFromNBT(tag.getCompoundTag("block")) : null,
                tag.getString("ore"));
        }
    }

    public static class MutationData {

        public final String parent1;
        public final String parent2;
        public final String result;
        public final double chance;
        public final List<FoundationRequirement> foundations;

        public MutationData(String parent1, String parent2, String result, double chance) {
            this(parent1, parent2, result, chance, Collections.emptyList());
        }

        public MutationData(String parent1, String parent2, String result, double chance,
            List<FoundationRequirement> foundations) {
            this.parent1 = parent1;
            this.parent2 = parent2;
            this.result = result;
            this.chance = chance;
            this.foundations = foundations;
        }
    }

    public static class BreedingStep {

        public final String parent1;
        public final String parent2;
        public final String result;
        public final double chance;
        public final List<FoundationRequirement> foundations;

        public BreedingStep(String parent1, String parent2, String result, double chance) {
            this(parent1, parent2, result, chance, Collections.emptyList());
        }

        public BreedingStep(String parent1, String parent2, String result, double chance,
            List<FoundationRequirement> foundations) {
            this.parent1 = parent1;
            this.parent2 = parent2;
            this.result = result;
            this.chance = chance;
            this.foundations = foundations;
        }

        @Override
        public String toString() {
            return parent1 + " + " + parent2 + " -> " + result;
        }
    }

}
