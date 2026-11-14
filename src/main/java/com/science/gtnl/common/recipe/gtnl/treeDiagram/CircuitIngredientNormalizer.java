package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.science.gtnl.common.circuit.CircuitIdentity;
import com.science.gtnl.common.circuit.CircuitTarget;
import com.science.gtnl.common.circuit.computation.CircuitComputationPolicy;
import com.science.gtnl.utils.recipes.RecipeBuilder;
import com.science.gtnl.utils.recipes.data.CircuitRecipeRequirement;
import com.science.gtnl.utils.recipes.metadata.TreeDiagramCircuitMetadata;

import bartworks.system.material.CircuitGeneration.CircuitWraps;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.objects.ItemData;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTRecipe;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class CircuitIngredientNormalizer {

    private static final Set<OrePrefixes> MELTABLE_PARTS = new ReferenceOpenHashSet<>(
        List.of(
            OrePrefixes.wireGt01,
            OrePrefixes.wireGt02,
            OrePrefixes.wireGt04,
            OrePrefixes.wireGt08,
            OrePrefixes.wireGt12,
            OrePrefixes.wireGt16,
            OrePrefixes.frameGt,
            OrePrefixes.dust,
            OrePrefixes.nugget,
            OrePrefixes.ingot,
            OrePrefixes.plate,
            OrePrefixes.plateDouble,
            OrePrefixes.plateDense,
            OrePrefixes.rod,
            OrePrefixes.round,
            OrePrefixes.bolt,
            OrePrefixes.screw,
            OrePrefixes.ring,
            OrePrefixes.foil,
            OrePrefixes.itemCasing,
            OrePrefixes.wireFine,
            OrePrefixes.gearGt,
            OrePrefixes.gearGtSmall,
            OrePrefixes.rotor,
            OrePrefixes.stickLong,
            OrePrefixes.spring,
            OrePrefixes.springSmall,
            OrePrefixes.plateSuperdense,
            OrePrefixes.pipeTiny,
            OrePrefixes.pipeSmall,
            OrePrefixes.pipeMedium,
            OrePrefixes.pipeLarge,
            OrePrefixes.pipeHuge,
            OrePrefixes.pipeQuadruple,
            OrePrefixes.pipeNonuple));

    private final Map<ItemId, ItemId> singlesByWrap = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<ItemId, ItemId> wrapsBySingle = new Object2ObjectLinkedOpenHashMap<>();
    private final Int2ObjectMap<ItemId> wrapsByOre = new Int2ObjectOpenHashMap<>();
    private final CircuitComputationPolicy computationPolicy;

    public CircuitIngredientNormalizer(CircuitComputationPolicy computationPolicy) {
        this.computationPolicy = computationPolicy;
        for (CircuitWraps wrap : CircuitWraps.VALUES) {
            if (!wrap.itemSingle.hasBeenSet() || !wrap.itemWrap.hasBeenSet()) continue;
            ItemId single = ItemId.create(wrap.itemSingle.get(1));
            ItemId wrapped = ItemId.create(wrap.itemWrap.get(1));
            singlesByWrap.put(wrapped, single);
            wrapsBySingle.put(single, wrapped);
        }
    }

    public GTRecipe normalize(CircuitRecipeDraft draft, CircuitTarget target) {
        Map<ItemKey, Long> items = new Object2ObjectLinkedOpenHashMap<>();
        Map<FluidKey, CircuitFluidInput> fluids = new Object2ObjectLinkedOpenHashMap<>();
        for (CircuitFluidInput fluid : draft.fluids()) mergeFluid(fluids, fluid);
        for (CircuitItemInput input : draft.items()) {
            CircuitItemInput unwrapped = unwrap(input);
            CircuitFluidInput molten = asMolten(unwrapped);
            if (molten != null) {
                mergeFluid(fluids, molten);
            } else {
                boolean catalyst = unwrapped.amount() == 0 || unwrapped.chance() == 0;
                ItemKey key = new ItemKey(
                    sorted(unwrapped.alternatives()),
                    unwrapped.chance(),
                    unwrapped.oreDictId(),
                    catalyst);
                items.merge(key, unwrapped.amount(), catalyst ? Math::max : Math::addExact);
            }
        }

        List<Object> itemInputs = new ObjectArrayList<>();
        IntArrayList itemChances = new IntArrayList();
        IntArrayList itemOreIds = new IntArrayList();
        for (Map.Entry<ItemKey, Long> entry : items.entrySet()) {
            ItemKey key = entry.getKey();
            long original = entry.getValue();
            long amount = key.catalyst() ? original : discount(original);
            if (original > 0 && amount == 0) continue;
            List<ItemId> wrapped = new ObjectArrayList<>();
            for (ItemId item : key.alternatives()) {
                ItemId wrap = wrapsBySingle.get(item);
                if (wrap == null) {
                    wrapped.clear();
                    break;
                }
                wrapped.add(wrap);
            }
            if (wrapsByOre.containsKey(key.oreId())) {
                wrapped = Collections.singletonList(wrapsByOre.get(key.oreId()));
            }
            if (!key.catalyst() && amount >= 16 && !wrapped.isEmpty()) {
                addItems(itemInputs, itemChances, itemOreIds, wrapped, amount / 16, key.chance(), -1);
                amount %= 16;
                if (amount == 0) continue;
            }
            addItems(itemInputs, itemChances, itemOreIds, key.alternatives(), amount, key.chance(), key.oreId());
        }

        List<FluidStack> fluidInputs = new ObjectArrayList<>();
        List<FluidStack[]> fluidAlternatives = new ObjectArrayList<>();
        IntArrayList fluidChances = new IntArrayList();
        for (CircuitFluidInput input : fluids.values()) {
            long amount = input.chance() == 0 || input.amount() == 0 ? input.amount() : discount(input.amount());
            if (input.amount() > 0 && amount == 0) continue;
            FluidStack[] alternatives = input.alternatives()
                .toArray(new FluidStack[0]);
            for (FluidStack alternative : alternatives) alternative.amount = Math.toIntExact(amount);
            fluidInputs.add(alternatives[0]);
            fluidAlternatives.add(alternatives);
            fluidChances.add(input.chance());
        }

        long computation = computationPolicy
            .recipeRequirement(target.circuitTier(), items.size(), fluids.size(), draft.processNodes());
        CircuitRecipeRequirement requirement = new CircuitRecipeRequirement(
            target.familyId(),
            target.circuitTier(),
            target.requiredNaniteTier(),
            computation);
        RecipeBuilder builder = RecipeBuilder.builder()
            .itemInputs(itemInputs.toArray())
            .fluidInputs(fluidInputs.toArray(new FluidStack[0]))
            .itemOutputs(draft.outputs(), draft.outputChances())
            .fluidOutputs(draft.fluidOutputs(), draft.fluidOutputChances())
            .duration(draft.duration())
            .eut(draft.peakEu())
            .specialValue(target.requiredNaniteTier())
            .metadata(TreeDiagramCircuitMetadata.INSTANCE, requirement);
        builder.inputChances = itemChances.toIntArray();
        builder.altOreIds = itemOreIds.toIntArray();
        builder.fluidInputChances = fluidChances.toIntArray();
        builder.altFluidInputs = fluidAlternatives.toArray(new FluidStack[0][]);
        if (draft.nbtSensitive()) builder.nbtSensitive();
        return builder.build()
            .orElseThrow(() -> new IllegalArgumentException("Invalid normalized circuit recipe"));
    }

    private CircuitItemInput unwrap(CircuitItemInput input) {
        if (input.amount() == 0 || input.chance() == 0) return input;
        if (input.alternatives()
            .size() == 1) {
            ItemId wrapped = input.alternatives()
                .get(0);
            ItemData data = GTOreDictUnificator.getAssociation(wrapped.toStack(1));
            if (wrapped.getTag() == null && data != null
                && data.mPrefix == OrePrefixes.wrapCircuit
                && data.mMaterial != null) {
                int oreId = OreDictionary.getOreID(
                    OrePrefixes.circuit.get(data.mMaterial.mMaterial)
                        .toString());
                List<ItemId> alternatives = new ObjectArrayList<>();
                for (ItemStack circuit : OreDictionary.getOres(OreDictionary.getOreName(oreId))) {
                    ItemId id = ItemId.create(circuit);
                    if (!alternatives.contains(id)) alternatives.add(id);
                }
                if (alternatives.isEmpty())
                    throw new IllegalArgumentException("Wrapped circuit has no raw equivalents");
                wrapsByOre.put(oreId, wrapped);
                return new CircuitItemInput(
                    alternatives,
                    Math.multiplyExact(input.amount(), 16),
                    input.chance(),
                    oreId);
            }
        }
        List<ItemId> singles = new ObjectArrayList<>();
        for (ItemId alternative : input.alternatives()) {
            ItemId single = singlesByWrap.get(alternative);
            if (single == null) return input;
            singles.add(single);
        }
        return new CircuitItemInput(singles, Math.multiplyExact(input.amount(), 16), input.chance(), -1);
    }

    private static CircuitFluidInput asMolten(CircuitItemInput input) {
        if (input.amount() == 0 || input.chance() == 0) return null;
        List<FluidStack> alternatives = new ObjectArrayList<>();
        long amount = -1;
        for (ItemId item : input.alternatives()) {
            if (item.getTag() != null) return null;
            ItemData data = GTOreDictUnificator.getAssociation(item.toStack(1));
            if (data == null || data.mPrefix == null
                || data.mMaterial == null
                || !MELTABLE_PARTS.contains(data.mPrefix)) {
                return null;
            }
            FluidStack fluid = data.mMaterial.mMaterial.getMolten(1);
            if (fluid == null) return null;
            long material = Math.multiplyExact(input.amount(), data.mPrefix.getMaterialAmount());
            long fluidAmount = Math.multiplyExact(material, 144) / GTValues.M;
            if (amount >= 0 && amount != fluidAmount) return null;
            amount = fluidAmount;
            if (alternatives.stream()
                .noneMatch(fluid::isFluidEqual)) alternatives.add(fluid);
        }
        return new CircuitFluidInput(alternatives, amount, input.chance());
    }

    private static void addItems(List<Object> inputs, IntArrayList chances, IntArrayList oreIds,
        List<ItemId> alternatives, long amount, int chance, int oreId) {
        ItemStack[] stacks = new ItemStack[alternatives.size()];
        for (int index = 0; index < stacks.length; index++) stacks[index] = alternatives.get(index)
            .toStack(Math.toIntExact(amount));
        inputs.add(stacks.length == 1 ? stacks[0] : stacks);
        chances.add(chance);
        oreIds.add(oreId);
    }

    private static void mergeFluid(Map<FluidKey, CircuitFluidInput> fluids, CircuitFluidInput input) {
        List<FluidStack> alternatives = new ObjectArrayList<>(input.alternatives());
        alternatives.sort(Comparator.comparing(CircuitIngredientNormalizer::fluidIdentity));
        List<String> ids = new ObjectArrayList<>();
        for (FluidStack fluid : alternatives) ids.add(fluidIdentity(fluid));
        boolean catalyst = input.amount() == 0 || input.chance() == 0;
        FluidKey key = new FluidKey(ids, input.chance(), catalyst);
        CircuitFluidInput previous = fluids.get(key);
        long amount = previous == null ? input.amount()
            : catalyst ? Math.max(previous.amount(), input.amount()) : Math.addExact(previous.amount(), input.amount());
        fluids.put(key, new CircuitFluidInput(alternatives, amount, input.chance()));
    }

    private static List<ItemId> sorted(List<ItemId> items) {
        List<ItemId> sorted = new ObjectArrayList<>(items);
        sorted.sort(Comparator.comparing(item -> CircuitIdentity.item(item.toStack(1))));
        return Collections.unmodifiableList(sorted);
    }

    public static String fluidIdentity(FluidStack fluid) {
        return fluid.getFluid()
            .getName() + ":"
            + CircuitIdentity.nbt(fluid.tag);
    }

    public static long discount(long amount) {
        return Math.addExact(Math.multiplyExact(amount / 10, 9), amount % 10 * 9 / 10);
    }

    @Desugar
    private record ItemKey(List<ItemId> alternatives, int chance, int oreId, boolean catalyst) {}

    @Desugar
    private record FluidKey(List<String> alternatives, int chance, boolean catalyst) {}
}
