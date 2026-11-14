package com.science.gtnl.common.recipe.gtnl.treeDiagram;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.github.bsideup.jabel.Desugar;
import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.circuit.CircuitCatalog;
import com.science.gtnl.common.circuit.CircuitIdentity;
import com.science.gtnl.common.circuit.CircuitTarget;
import com.science.gtnl.utils.recipes.data.CircuitRecipeRequirement;
import com.science.gtnl.utils.recipes.metadata.TreeDiagramCircuitMetadata;

import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTRecipe.GTRecipe_WithAlt;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

public class TreeDiagramRecipeGenerator {

    private final List<CircuitRecipeSource> sources;
    private final CircuitCatalog catalog;
    private final CircuitIngredientNormalizer normalizer;

    public TreeDiagramRecipeGenerator(List<CircuitRecipeSource> sources, CircuitCatalog catalog,
        CircuitIngredientNormalizer normalizer) {
        this.sources = List.copyOf(sources);
        this.catalog = catalog;
        this.normalizer = normalizer;
    }

    public CircuitRecipeGenerationReport generate(RecipeMap<?> destination) {
        catalog.registerDefaults();
        Map<ItemId, String> selectedSources = new Object2ObjectLinkedOpenHashMap<>();
        Set<String> fingerprints = new ObjectOpenHashSet<>();
        int generated = 0;
        int skipped = 0;
        int itemInputs = 0;
        int itemOutputs = 0;
        int fluidInputs = 0;
        int fluidOutputs = 0;
        long maximumComputation = 0;
        Map<CircuitTarget, List<Candidate>> candidates = new Object2ObjectLinkedOpenHashMap<>();
        for (CircuitRecipeSource source : sources) {
            for (GTRecipe original : source.recipes()) {
                if (original.mFakeRecipe || !original.mEnabled || original.mOutputs.length == 0) continue;
                ItemStack output = source.primaryOutput(original);
                if (output == null || output.stackSize <= 0) continue;
                CircuitTarget target = catalog.registerTarget(output);
                if (target == null) continue;
                candidates.computeIfAbsent(target, ignored -> new ObjectArrayList<>())
                    .add(new Candidate(source, original));
            }
        }
        for (Map.Entry<CircuitTarget, List<Candidate>> entry : candidates.entrySet()) {
            CircuitTarget target = entry.getKey();
            entry.getValue()
                .sort(
                    Comparator.<Candidate>comparingInt(
                        candidate -> candidate.source()
                            .priority(target))
                        .reversed());
            for (Candidate candidate : entry.getValue()) {
                CircuitRecipeSource source = candidate.source();
                GTRecipe original = candidate.recipe();
                ItemStack output = target.item()
                    .toStack(1);
                String selected = selectedSources.get(target.item());
                if (selected != null && !selected.equals(source.id())) continue;
                try {
                    for (GTRecipe variant : CircuitRecipeVariants.splitUnequalAlternatives(original)) {
                        for (CircuitRecipeDraft draft : source.expand(variant)) {
                            GTRecipe recipe = normalizer.normalize(draft, target);
                            if (recipe.owners != null) {
                                recipe.owners.clear();
                                if (original.owners != null) recipe.owners.addAll(original.owners);
                                recipe.setOwner(ScienceNotLeisure.MODID);
                            }
                            String fingerprint = fingerprint(recipe);
                            if (!fingerprints.add(fingerprint)) continue;
                            if (destination.addRecipe(recipe, false, false, false) == null) {
                                throw new IllegalArgumentException("Recipe backend rejected normalized circuit");
                            }
                            selectedSources.put(target.item(), source.id());
                            catalog.markCraftable(target);
                            generated++;
                            itemInputs = Math.max(itemInputs, recipe.mInputs.length);
                            itemOutputs = Math.max(itemOutputs, recipe.mOutputs.length);
                            fluidInputs = Math.max(fluidInputs, recipe.mFluidInputs.length);
                            fluidOutputs = Math.max(fluidOutputs, recipe.mFluidOutputs.length);
                            CircuitRecipeRequirement requirement = recipe
                                .getMetadata(TreeDiagramCircuitMetadata.INSTANCE);
                            maximumComputation = Math.max(maximumComputation, requirement.baseComputation());
                            ScienceNotLeisure.LOG.debug(
                                "Tree Diagram {} -> {}: items {}/{}, fluids {}/{}, {} CU",
                                source.id(),
                                CircuitIdentity.item(output),
                                recipe.mInputs.length,
                                recipe.mOutputs.length,
                                recipe.mFluidInputs.length,
                                recipe.mFluidOutputs.length,
                                requirement.baseComputation());
                        }
                    }
                } catch (IllegalArgumentException | ArithmeticException e) {
                    skipped++;
                    ScienceNotLeisure.LOG.warn(
                        "Tree Diagram skipped {} recipe for {}: {}",
                        source.id(),
                        CircuitIdentity.item(output),
                        e.getMessage());
                }
            }
        }
        catalog.finishRegistration();
        CircuitRecipeGenerationReport report = new CircuitRecipeGenerationReport(
            generated,
            selectedSources.size(),
            skipped,
            itemInputs,
            itemOutputs,
            fluidInputs,
            fluidOutputs,
            maximumComputation);
        ScienceNotLeisure.LOG.info("Tree Diagram recipe generation: {}", report);
        for (Map.Entry<ItemId, String> entry : selectedSources.entrySet()) {
            ScienceNotLeisure.LOG.info(
                "Tree Diagram circuit {} uses {}",
                CircuitIdentity.item(
                    entry.getKey()
                        .toStack(1)),
                entry.getValue());
        }
        return report;
    }

    @Desugar
    private record Candidate(CircuitRecipeSource source, GTRecipe recipe) {}

    public static String fingerprint(GTRecipe recipe) {
        List<String> items = new ObjectArrayList<>();
        for (int index = 0; index < recipe.mInputs.length; index++) {
            ItemStack input = recipe.mInputs[index];
            if (input == null) continue;
            List<String> alternatives = new ObjectArrayList<>();
            int oreId = -1;
            if (recipe instanceof GTRecipe_WithAlt withAlt) {
                if (withAlt.mOreDictIds != null && index < withAlt.mOreDictIds.length)
                    oreId = withAlt.mOreDictIds[index];
                if (withAlt.mOreDictAlt != null && index < withAlt.mOreDictAlt.length
                    && withAlt.mOreDictAlt[index] != null) {
                    for (ItemStack alternative : withAlt.mOreDictAlt[index]) {
                        if (alternative != null)
                            alternatives.add(CircuitIdentity.item(alternative) + "*" + alternative.stackSize);
                    }
                }
            }
            if (alternatives.isEmpty()) alternatives.add(CircuitIdentity.item(input) + "*" + input.stackSize);
            Collections.sort(alternatives);
            items.add(alternatives + ":" + oreId + ":" + recipe.getInputChance(index));
        }
        Collections.sort(items);
        List<String> fluids = new ObjectArrayList<>();
        for (int index = 0; index < recipe.mFluidInputs.length; index++) {
            List<String> alternatives = new ObjectArrayList<>();
            if (recipe instanceof GTRecipe_WithAlt withAlt && withAlt.mAltFluidInputs != null
                && index < withAlt.mAltFluidInputs.length
                && withAlt.mAltFluidInputs[index] != null) {
                for (FluidStack alternative : withAlt.mAltFluidInputs[index]) {
                    if (alternative != null) alternatives
                        .add(CircuitIngredientNormalizer.fluidIdentity(alternative) + "*" + alternative.amount);
                }
            }
            if (alternatives.isEmpty()) {
                FluidStack input = recipe.mFluidInputs[index];
                alternatives.add(CircuitIngredientNormalizer.fluidIdentity(input) + "*" + input.amount);
            }
            Collections.sort(alternatives);
            fluids.add(alternatives + ":" + recipe.getFluidInputChance(index));
        }
        Collections.sort(fluids);
        StringBuilder result = new StringBuilder().append(items)
            .append(fluids);
        for (int index = 0; index < recipe.mOutputs.length; index++) {
            ItemStack output = recipe.mOutputs[index];
            result.append(CircuitIdentity.item(output))
                .append('*')
                .append(output.stackSize)
                .append(':')
                .append(recipe.getOutputChance(index));
        }
        for (int index = 0; index < recipe.mFluidOutputs.length; index++) {
            FluidStack output = recipe.mFluidOutputs[index];
            result.append(CircuitIngredientNormalizer.fluidIdentity(output))
                .append('*')
                .append(output.amount)
                .append(':')
                .append(recipe.getFluidOutputChance(index));
        }
        return CircuitIdentity.digest(
            result.append(':')
                .append(recipe.mEUt)
                .append(':')
                .append(recipe.mDuration)
                .append(':')
                .append(recipe.isNBTSensitive)
                .append(':')
                .append(recipe.getMetadata(TreeDiagramCircuitMetadata.INSTANCE))
                .toString());
    }
}
