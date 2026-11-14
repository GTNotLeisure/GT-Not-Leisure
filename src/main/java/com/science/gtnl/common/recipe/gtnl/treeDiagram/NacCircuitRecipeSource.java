package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.science.gtnl.common.circuit.CircuitCatalog;
import com.science.gtnl.common.circuit.CircuitTarget;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.items.CircuitComponentFakeItem;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;
import gregtech.common.tileentities.machines.multi.nanochip.util.CircuitComponent;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

public class NacCircuitRecipeSource implements CircuitRecipeSource {

    private static final Set<String> PREFERRED_FAMILIES = Set.of("crystal", "wetware", "bio", "optical");

    private final Map<ItemId, CircuitComponent> realComponents = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<ItemId, List<Producer>> producers = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<CircuitComponent, List<Producer>> opticalFinishers = new Object2ObjectLinkedOpenHashMap<>();
    private final List<Fluid> processingFluids = new ObjectArrayList<>();

    public NacCircuitRecipeSource() {
        Materials[] water = { Materials.Grade1PurifiedWater, Materials.Grade2PurifiedWater,
            Materials.Grade3PurifiedWater, Materials.Grade4PurifiedWater, Materials.Grade5PurifiedWater,
            Materials.Grade6PurifiedWater, Materials.Grade7PurifiedWater, Materials.Grade8PurifiedWater,
            Materials.DimensionallyShiftedSuperfluid };
        for (Materials material : water) processingFluids.add(
            material.getFluid(1)
                .getFluid());
        for (CircuitComponent component : CircuitComponent.VALUES) {
            if (component.realComponent == null) continue;
            ItemStack stack = component.realComponent.get();
            if (isReal(stack)) realComponents.putIfAbsent(ItemId.create(stack), component);
        }
        RecipeMap<?>[] modules = { RecipeMaps.nanochipPartProcessorRecipes, RecipeMaps.nanochipBoardProcessorRecipes,
            RecipeMaps.nanochipEtchingArray, RecipeMaps.nanochipCuttingChamber, RecipeMaps.nanochipWireTracer,
            RecipeMaps.nanochipSuperconductorSplitter, RecipeMaps.nanochipOpticalOrganizer,
            RecipeMaps.nanochipEncasementWrapper, RecipeMaps.nanochipBiologicalCoordinator };
        for (RecipeMap<?> map : modules) {
            for (GTRecipe recipe : map.getAllRecipes()) indexRecipe(recipe, false, false);
        }
        for (GTRecipe recipe : RecipeMaps.nanochipAssemblyMatrixRecipes.getAllRecipes())
            indexRecipe(recipe, false, true);

        Set<ItemId> bridges = new ObjectOpenHashSet<>();
        ItemList[] bridgeItems = { ItemList.RealizedQuantumCircuitRack, ItemList.UnboundWDMStrands,
            ItemList.IsolatedBDMStrands };
        for (ItemList item : bridgeItems) if (item.hasBeenSet()) bridges.add(ItemId.create(item.get(1)));
        for (RecipeMap<?> map : new RecipeMap<?>[] { RecipeMaps.autoclaveRecipes,
            RecipeMaps.centrifugeNonCellRecipes }) {
            for (GTRecipe recipe : map.getAllRecipes()) {
                for (ItemStack output : recipe.mOutputs) {
                    if (output != null && bridges.contains(ItemId.create(output))) {
                        indexRecipe(recipe, true, false);
                        break;
                    }
                }
            }
        }
    }

    private void indexRecipe(GTRecipe original, boolean external, boolean matrix) {
        if (original.mFakeRecipe || !original.mEnabled) return;
        for (GTRecipe recipe : CircuitRecipeVariants.splitUnequalAlternatives(original)) {
            Producer producer = new Producer(recipe, external, fluidRank(recipe));
            Set<ItemId> indexed = new ObjectOpenHashSet<>();
            for (ItemStack rawOutput : recipe.mOutputs) {
                if (rawOutput == null || rawOutput.stackSize <= 0) continue;
                ItemStack output = external ? normalizeExternal(rawOutput) : rawOutput;
                if (!indexed.add(ItemId.create(output))) continue;
                CircuitComponent component = component(output);
                if (component != null && component.xorResult != null) {
                    if (!matrix) opticalFinishers.computeIfAbsent(component, ignored -> new ObjectArrayList<>())
                        .add(producer);
                    continue;
                }
                if (matrix && isReal(realItem(output))) continue;
                producers.computeIfAbsent(ItemId.create(output), ignored -> new ObjectArrayList<>())
                    .add(producer);
            }
        }
    }

    @Override
    public String id() {
        return "nac";
    }

    @Override
    public int priority(CircuitTarget target) {
        return PREFERRED_FAMILIES.contains(target.familyId()) || target.circuitTier() >= 12 ? 30 : 0;
    }

    @Override
    public Collection<GTRecipe> recipes() {
        return RecipeMaps.nanochipAssemblyMatrixRecipes.getAllRecipes();
    }

    @Override
    public ItemStack primaryOutput(GTRecipe recipe) {
        return recipe.mOutputs.length == 0 ? null : realItem(recipe.mOutputs[0]);
    }

    @Override
    public List<CircuitRecipeDraft> expand(GTRecipe recipe) {
        if (recipe.mOutputs.length == 0 || recipe.mOutputs[0].stackSize <= 0
            || 64 % recipe.mOutputs[0].stackSize != 0
            || recipe.getOutputChance(0) != 10000) {
            throw new IllegalArgumentException("NAC primary output cannot be normalized to 64");
        }
        long runs = 64 / recipe.mOutputs[0].stackSize;
        List<CircuitRecipeDraft> drafts = new ObjectArrayList<>();
        RuntimeException failure = null;
        for (int grade = 1; grade <= processingFluids.size(); grade++) {
            try {
                List<Expansion> contexts = Collections.singletonList(new Expansion(grade));
                for (CircuitItemInput input : CircuitRecipeDraft.itemInputs(recipe, runs))
                    contexts = expandAcross(contexts, input);
                for (Expansion context : contexts) {
                    context.draft.addRecipeCost(recipe, runs);
                    context.nodes.add(recipe);
                    context.draft.addRecipeFluids(recipe, runs);
                    for (int index = 0; index < recipe.mOutputs.length; index++) {
                        ItemStack output = recipe.mOutputs[index];
                        if (output != null) addRealOutput(
                            context,
                            output,
                            Math.multiplyExact(output.stackSize, runs),
                            recipe.getOutputChance(index));
                    }
                    addFluidOutputs(context, recipe, runs);
                }
                contexts = finishOpticalOutput(contexts, component(recipe.mOutputs[0]), 64);
                for (Expansion context : contexts) {
                    for (ItemId id : context.available.keySet()) {
                        long amount = context.available.getLong(id);
                        // Internal virtual leftovers have no item that a normal output bus can deliver.
                        if (amount > 0 && isReal(realItem(id.toStack(1))))
                            addRealOutput(context, id.toStack(1), amount, 10000);
                    }
                    context.draft.setProcessNodes(context.nodes.size());
                    drafts.add(context.draft);
                }
            } catch (IllegalArgumentException | ArithmeticException e) {
                failure = e;
            }
        }
        if (drafts.isEmpty() && failure != null) throw failure;
        return drafts;
    }

    private List<Expansion> expandAcross(List<Expansion> contexts, CircuitItemInput input) {
        List<Expansion> result = new ObjectArrayList<>();
        for (Expansion context : contexts) result.addAll(expandInput(context, input));
        return result;
    }

    private List<Expansion> expandInput(Expansion context, CircuitItemInput input) {
        if (input.alternatives()
            .size() > 1) {
            List<ItemId> realAlternatives = new ObjectArrayList<>();
            boolean terminal = true;
            for (ItemId item : input.alternatives()) {
                ItemStack real = realItem(item.toStack(1));
                if (!isReal(real) || !isBoundary(item) && producers.containsKey(item)) {
                    terminal = false;
                    break;
                }
                realAlternatives.add(ItemId.create(real));
            }
            if (terminal) {
                context.draft
                    .addItem(new CircuitItemInput(realAlternatives, input.amount(), input.chance(), input.oreDictId()));
                return Collections.singletonList(context);
            }
            List<Expansion> branches = new ObjectArrayList<>();
            for (ItemId item : input.alternatives()) {
                branches.addAll(
                    expandInput(
                        new Expansion(context),
                        new CircuitItemInput(Collections.singletonList(item), input.amount(), input.chance(), -1)));
            }
            return branches;
        }

        ItemId item = input.alternatives()
            .get(0);
        if (input.amount() == 0 || input.chance() == 0 || isBoundary(item) || !producers.containsKey(item)) {
            ItemStack real = realItem(item.toStack(1));
            if (!isReal(real)) throw new IllegalArgumentException("Unresolved NAC input: " + item);
            context.draft.addItem(
                new CircuitItemInput(
                    Collections.singletonList(ItemId.create(real)),
                    input.amount(),
                    input.chance(),
                    input.oreDictId()));
            return Collections.singletonList(context);
        }
        if (input.chance() != 10000) throw new IllegalArgumentException("Probabilistic NAC intermediate input");
        long available = context.available.getLong(item);
        long consumed = Math.min(input.amount(), available);
        context.available.put(item, available - consumed);
        long remaining = input.amount() - consumed;
        if (remaining == 0) return Collections.singletonList(context);
        if (!context.path.add(item)) throw new IllegalArgumentException("Cyclic NAC input: " + item);

        List<Expansion> result = new ObjectArrayList<>();
        RuntimeException failure = null;
        for (Producer producer : select(producers.get(item), context.grade)) {
            try {
                long yield = outputAmount(producer, item);
                if (yield <= 0) continue;
                long runs = Math.addExact((remaining - 1) / yield, 1);
                List<Expansion> branches = Collections.singletonList(new Expansion(context));
                for (CircuitItemInput child : inputs(producer, runs)) branches = expandAcross(branches, child);
                for (Expansion branch : branches) {
                    branch.draft.addRecipeCost(producer.recipe(), runs);
                    branch.draft.addRecipeFluids(producer.recipe(), runs);
                    branch.nodes.add(producer.recipe());
                    for (int index = 0; index < producer.recipe().mOutputs.length; index++) {
                        ItemStack output = producer.recipe().mOutputs[index];
                        if (output == null) continue;
                        if (producer.recipe()
                            .getOutputChance(index) != 10000) {
                            throw new IllegalArgumentException("Probabilistic NAC intermediate output");
                        }
                        ItemId outputId = ItemId.create(producer.external() ? normalizeExternal(output) : output);
                        branch.available.put(
                            outputId,
                            Math.addExact(
                                branch.available.getLong(outputId),
                                Math.multiplyExact(output.stackSize, runs)));
                    }
                    addFluidOutputs(branch, producer.recipe(), runs);
                    long left = branch.available.getLong(item) - remaining;
                    if (left < 0)
                        throw new IllegalArgumentException("NAC producer does not cover its requested output");
                    branch.available.put(item, left);
                    branch.path.remove(item);
                    result.add(branch);
                }
            } catch (IllegalArgumentException | ArithmeticException e) {
                failure = e;
            }
        }
        context.path.remove(item);
        if (result.isEmpty()) throw new IllegalArgumentException("No usable NAC producer for " + item, failure);
        return result;
    }

    private List<CircuitItemInput> inputs(Producer producer, long runs) {
        List<CircuitItemInput> inputs = CircuitRecipeDraft.itemInputs(producer.recipe(), runs);
        if (!producer.external()) return inputs;
        List<CircuitItemInput> normalized = new ObjectArrayList<>();
        for (CircuitItemInput input : inputs) {
            List<ItemId> alternatives = new ObjectArrayList<>();
            for (ItemId item : input.alternatives())
                alternatives.add(ItemId.create(normalizeExternal(item.toStack(1))));
            normalized.add(new CircuitItemInput(alternatives, input.amount(), input.chance(), input.oreDictId()));
        }
        return normalized;
    }

    private List<Expansion> finishOpticalOutput(List<Expansion> contexts, CircuitComponent output, long amount) {
        if (output == null || output.xorResult == null) return contexts;
        long recoveryCount = Math.addExact(Math.multiplyExact(amount, 10000L - output.xorSuccessChance), 9999) / 10000;
        if (recoveryCount == 0) return contexts;
        ItemId unfinished = ItemId.create(output.xorResult.getFakeStack(1));
        List<Expansion> result = new ObjectArrayList<>();
        for (Expansion context : contexts) {
            for (Producer finisher : select(opticalFinishers.get(output), context.grade)) {
                long perRun = outputAmount(finisher, ItemId.create(output.getFakeStack(1)));
                if (perRun <= 0) continue;
                long runs = (recoveryCount - 1) / perRun + 1;
                List<Expansion> branches = Collections.singletonList(new Expansion(context));
                boolean consumesUnfinished = false;
                for (CircuitItemInput input : inputs(finisher, runs)) {
                    if (input.alternatives()
                        .size() == 1 && unfinished.equals(
                            input.alternatives()
                                .get(0))) {
                        consumesUnfinished = true;
                        continue;
                    }
                    branches = expandAcross(branches, input);
                }
                if (!consumesUnfinished) continue;
                for (Expansion branch : branches) {
                    branch.draft.addRecipeCost(finisher.recipe(), runs);
                    branch.draft.addRecipeFluids(finisher.recipe(), runs);
                    branch.nodes.add(finisher.recipe());
                    addFluidOutputs(branch, finisher.recipe(), runs);
                    for (int index = 0; index < finisher.recipe().mOutputs.length; index++) {
                        ItemStack byproduct = finisher.recipe().mOutputs[index];
                        if (byproduct != null && component(byproduct) != output && isReal(realItem(byproduct))) {
                            addRealOutput(
                                branch,
                                byproduct,
                                Math.multiplyExact(byproduct.stackSize, runs),
                                finisher.recipe()
                                    .getOutputChance(index));
                        }
                    }
                    result.add(branch);
                }
            }
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Missing optical circuit finishing recipe");
        return result;
    }

    private long outputAmount(Producer producer, ItemId item) {
        long amount = 0;
        for (int index = 0; index < producer.recipe().mOutputs.length; index++) {
            ItemStack output = producer.recipe().mOutputs[index];
            if (output == null) continue;
            if (item.equals(ItemId.create(producer.external() ? normalizeExternal(output) : output))) {
                if (producer.recipe()
                    .getOutputChance(index) != 10000) return 0;
                amount = Math.addExact(amount, output.stackSize);
            }
        }
        return amount;
    }

    private static List<Producer> select(List<Producer> candidates, int grade) {
        if (candidates == null || candidates.isEmpty()) return Collections.emptyList();
        int rank = -1;
        int lowest = Integer.MAX_VALUE;
        for (Producer producer : candidates) {
            lowest = Math.min(lowest, producer.fluidRank());
            if (producer.fluidRank() <= grade) rank = Math.max(rank, producer.fluidRank());
        }
        if (rank < 0) rank = lowest;
        List<Producer> selected = new ObjectArrayList<>();
        for (Producer producer : candidates) if (producer.fluidRank() == rank) selected.add(producer);
        return selected;
    }

    private int fluidRank(GTRecipe recipe) {
        int rank = 0;
        for (FluidStack fluid : recipe.mFluidInputs) {
            if (fluid != null) rank = Math.max(rank, processingFluids.indexOf(fluid.getFluid()) + 1);
        }
        return rank;
    }

    private boolean isBoundary(ItemId item) {
        ItemStack real = realItem(item.toStack(1));
        return isReal(real) && CircuitCatalog.circuitTier(real) >= 0;
    }

    private ItemStack normalizeExternal(ItemStack stack) {
        CircuitComponent component = realComponents.get(ItemId.create(stack));
        return component == null ? stack : component.getFakeStack(stack.stackSize);
    }

    private static CircuitComponent component(ItemStack stack) {
        return stack != null && stack.getItem() == CircuitComponentFakeItem.INSTANCE
            ? CircuitComponent.tryGetFromFakeStack(stack)
            : null;
    }

    private static ItemStack realItem(ItemStack stack) {
        if (stack == null) return null;
        CircuitComponent component = component(stack);
        if (component == null) return stack.copy();
        if (component.isProcessed && component.componentForProcessed != null)
            component = component.componentForProcessed.get();
        if (component == null || component.realComponent == null) return null;
        ItemStack real = component.realComponent.get();
        if (!isReal(real)) return null;
        real = real.copy();
        real.stackSize = stack.stackSize;
        return real;
    }

    private static boolean isReal(ItemStack stack) {
        return stack != null && stack.getItem() != null
            && stack.getItem() != CircuitComponentFakeItem.INSTANCE
            && stack.getItem() != Item.getItemFromBlock(Blocks.fire);
    }

    private static void addRealOutput(Expansion context, ItemStack stack, long amount, int chance) {
        ItemStack real = realItem(stack);
        if (!isReal(real)) throw new IllegalArgumentException("Unresolved NAC byproduct: " + stack);
        context.draft.addOutput(real, amount, chance);
    }

    private static void addFluidOutputs(Expansion context, GTRecipe recipe, long runs) {
        for (int index = 0; index < recipe.mFluidOutputs.length; index++) {
            FluidStack output = recipe.mFluidOutputs[index];
            if (output != null) context.draft
                .addFluidOutput(output, Math.multiplyExact(output.amount, runs), recipe.getFluidOutputChance(index));
        }
    }

    @Desugar
    private record Producer(GTRecipe recipe, boolean external, int fluidRank) {}

    private static class Expansion {

        private final CircuitRecipeDraft draft;
        private final Object2LongOpenHashMap<ItemId> available;
        private final Set<ItemId> path;
        private final Set<GTRecipe> nodes;
        private final int grade;

        private Expansion(int grade) {
            this.grade = grade;
            draft = new CircuitRecipeDraft();
            available = new Object2LongOpenHashMap<>();
            path = new ObjectOpenHashSet<>();
            nodes = new ReferenceOpenHashSet<>();
        }

        private Expansion(Expansion original) {
            grade = original.grade;
            draft = new CircuitRecipeDraft(original.draft);
            available = new Object2LongOpenHashMap<>(original.available);
            path = new ObjectOpenHashSet<>(original.path);
            nodes = new ReferenceOpenHashSet<>(original.nodes);
        }
    }
}
