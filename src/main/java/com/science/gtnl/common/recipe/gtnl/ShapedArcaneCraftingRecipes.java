package com.science.gtnl.common.recipe.gtnl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.science.gtnl.api.IRecipePool;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.common.recipe.thaumcraft.TCRecipeTools;
import com.science.gtnl.utils.recipes.RecipeInputConflictResolver;

import gregtech.api.enums.TierEU;
import gregtech.api.interfaces.IRecipeMap;
import gregtech.api.objects.OreDictItemStack;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.api.recipe.metadata.SimpleRecipeMetadataKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import thaumcraft.api.aspects.AspectList;

public class ShapedArcaneCraftingRecipes implements IRecipePool {

    public static final RecipeMetadataKey<AspectList> ARCANE_VIS = SimpleRecipeMetadataKey
        .create(AspectList.class, "gtnl_arcane_vis");
    public static final RecipeMetadataKey<String> ARCANE_RESEARCH = SimpleRecipeMetadataKey
        .create(String.class, "gtnl_arcane_research");

    private static final List<String> BLACKLISTED_OREDICT_NAMES = Arrays.asList(
        "craftingToolScrewdriver",
        "craftingToolHardHammer",
        "craftingToolSaw",
        "craftingToolSoftHammer",
        "craftingSoftHammer",
        "craftingToolWrench",
        "craftingToolFile",
        "craftingToolCrowbar",
        "craftingToolWireCutter",
        "craftingToolBlade");

    @Override
    public void loadRecipes() {
        TCRecipeTools.getShapedArcaneCraftingRecipe();
        TCRecipeTools.getShapelessArcaneCraftingRecipe();

        List<PendingRecipe> pending = new ObjectArrayList<>();

        for (TCRecipeTools.ShapedArcaneCraftingRecipe recipe : TCRecipeTools.ShapedAR) {
            collectRecipe(
                pending,
                Arrays.asList(recipe.getInputItems()),
                recipe.getOutput(),
                recipe.getInputAspects(),
                recipe.getResearch());
        }

        for (TCRecipeTools.ShapelessArcaneCraftingRecipe recipe : TCRecipeTools.ShaplessAR) {
            collectRecipe(
                pending,
                recipe.getInputItems(),
                recipe.getOutput(),
                recipe.getInputAspects(),
                recipe.getResearch());
        }

        RecipeInputConflictResolver resolver = new RecipeInputConflictResolver("ShapedArcaneCrafting", false);
        for (PendingRecipe recipe : pending) {
            resolver.add(recipe.inputs(), recipe.output());
        }
        RecipeInputConflictResolver.Result result = resolver.resolve();

        IRecipeMap IAA = GTNLRecipeMaps.IndustrialShapedArcaneCraftingRecipes;
        for (int i = 0; i < pending.size(); i++) {
            if (result.isDuplicate(i)) continue;
            registerRecipe(IAA, pending.get(i), result.circuit(i));
        }
    }

    private void collectRecipe(List<PendingRecipe> pending, Iterable<?> inputItems, ItemStack recipeOutput,
        AspectList aspects, String research) {
        if (TCRecipeTools.shouldSkipThaumcraftItem(recipeOutput.getItem())) return;

        ItemStack output = recipeOutput.copy();

        for (Object[] inputs : createInputVariants(inputItems)) {
            Object[] recipeInputs = Arrays.stream(inputs)
                .map(input -> input instanceof ItemStack stack ? stack.copy() : input)
                .toArray();
            pending.add(new PendingRecipe(recipeInputs, output, aspects, research));
        }
    }

    private void registerRecipe(IRecipeMap map, PendingRecipe recipe, int circuit) {
        int duration = TCRecipeTools
            .computeAspectDurationFromCopy(recipe.aspects(), TCRecipeTools.ARCANE_DURATION_TICKS_PER_ASPECT);
        if (circuit > 0) {
            TCRecipeTools.addArcaneRecipeWithCircuit(
                map,
                recipe.inputs(),
                new ItemStack[] { recipe.output()
                    .copy() },
                recipe.aspects(),
                recipe.research(),
                ARCANE_VIS,
                ARCANE_RESEARCH,
                duration,
                TierEU.RECIPE_LV,
                circuit);
        } else {
            TCRecipeTools.addArcaneRecipe(
                map,
                recipe.inputs(),
                new ItemStack[] { recipe.output()
                    .copy() },
                recipe.aspects(),
                recipe.research(),
                ARCANE_VIS,
                ARCANE_RESEARCH,
                duration,
                TierEU.RECIPE_LV);
        }
    }

    private List<Object[]> createInputVariants(Iterable<?> inputs) {
        List<Object[]> variants = new ObjectArrayList<>();
        variants.add(new Object[0]);

        for (Object input : inputs) {
            List<Object> alternatives = convertInput(input);
            if (alternatives == null) return Collections.emptyList();
            if (alternatives.isEmpty()) continue;

            // Only explicit alternatives are expanded. Ore dictionary slots keep their original ID.
            List<Object[]> expanded = new ObjectArrayList<>();
            for (Object[] variant : variants) {
                for (Object alternative : alternatives) {
                    Object[] next = Arrays.copyOf(variant, variant.length + 1);
                    next[variant.length] = alternative;
                    expanded.add(next);
                }
            }
            variants = expanded;
        }
        return variants;
    }

    private List<Object> convertInput(Object input) {
        if (input == null) return Collections.emptyList();
        if (input instanceof ItemStack stack) {
            return stack.getItem() == null ? null : Collections.singletonList(normalizeInput(stack));
        }
        if (!(input instanceof List<?>candidates) || candidates.isEmpty()) return null;

        String oreName = findOreDictionaryName(input);
        if (oreName != null) {
            int amount = BLACKLISTED_OREDICT_NAMES.contains(oreName) ? 0 : 1;
            return Collections.singletonList(new OreDictItemStack(oreName, amount));
        }

        List<Object> alternatives = new ObjectArrayList<>();
        for (Object candidate : candidates) {
            if (!(candidate instanceof ItemStack stack) || stack.getItem() == null) continue;
            alternatives.add(normalizeInput(stack));
        }
        return alternatives.isEmpty() ? null : alternatives;
    }

    private ItemStack normalizeInput(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.stackSize = isOreDictBlacklisted(stack) ? 0 : 1;
        return TCRecipeTools.checkInputSpecial(copy)[0];
    }

    private boolean isOreDictBlacklisted(ItemStack itemStack) {
        int[] oreIDs = OreDictionary.getOreIDs(itemStack);
        for (int oreID : oreIDs) {
            String oreName = OreDictionary.getOreName(oreID);
            if (BLACKLISTED_OREDICT_NAMES.contains(oreName)) {
                return true;
            }
        }
        return false;
    }

    public static String findOreDictionaryName(Object input) {
        if (!(input instanceof Iterable<?>candidates)) return null;

        // Thaumcraft stores Forge's live list in place of the original ore name. Match that
        // exact list, not a shared ore tag that could broaden an explicit set of alternatives.
        for (Object candidate : candidates) {
            if (!(candidate instanceof ItemStack stack) || stack.getItem() == null) continue;

            for (int oreId : OreDictionary.getOreIDs(stack)) {
                String oreName = OreDictionary.getOreName(oreId);
                if (OreDictionary.getOres(oreName, false) == input) {
                    return oreName;
                }
            }
            return null;
        }
        return null;
    }

    private record PendingRecipe(Object[] inputs, ItemStack output, AspectList aspects, String research) {}
}
