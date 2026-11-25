package com.science.gtnl.compat.nei;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import com.science.gtnl.common.item.wand.MajoWandStage;
import com.science.gtnl.loader.ItemLoader;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import jcm2606.thaumicmachina.core.crafting.infusion.RecipeInfusionWandAugmentation;
import jcm2606.thaumicmachina.core.implement.IAugmentationWand;
import jcm2606.thaumicmachina.wand.WandHelper;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.wands.WandCap;
import thaumcraft.api.wands.WandRod;
import thaumcraft.common.config.ConfigItems;
import thaumcraft.common.items.wands.ItemWandCasting;

@SideOnly(Side.CLIENT)
public class WandAugmentationDisplay {

    private static final MajoWandStage[] STAGES = MajoWandStage.values();

    public static List<Object> adaptRecipes(List<Object> registered, @Nullable ItemStack queried, boolean outputQuery) {
        if (outputQuery && !(queried.getItem() instanceof ItemWandCasting)) return registered;
        boolean augmentedOutput = outputQuery && !WandHelper.getAugmentations(queried)
            .isEmpty();
        List<Object> display = new ArrayList<>(registered.size());
        Set<String> seen = new HashSet<>();
        for (Object entry : registered) {
            if (!(entry instanceof RecipeInfusionWandAugmentation source)) {
                display.add(entry);
                continue;
            }
            if (source.isResearchRecipe || !seen.add(source.augmentation.getID())
                || ResearchCategories.getResearch(source.getResearch()) == null) continue;
            if (queried != null && queried.getItem() instanceof ItemWandCasting) {
                ItemStack input = queried;
                if (augmentedOutput && !WandHelper.hasAugmentation(queried, source.augmentation)) continue;
                if (outputQuery && WandHelper.hasAugmentation(queried, source.augmentation)) {
                    input = WandHelper
                        .removeAugmentationsFrom(queried, new IAugmentationWand[] { source.augmentation }, true);
                }
                addView(display, source, input);
            } else {
                addView(display, source, withPrerequisites(source, createVanillaWand()));
                for (MajoWandStage stage : STAGES) {
                    addView(display, source, withPrerequisites(source, ItemLoader.majoWand.createStack(stage)));
                }
            }
        }
        return display;
    }

    public static ItemStack createVanillaWand() {
        ItemStack stack = new ItemStack(ConfigItems.itemWandCasting);
        ItemWandCasting wand = (ItemWandCasting) stack.getItem();
        wand.setRod(stack, WandRod.rods.get("silverwood"));
        wand.setCap(stack, WandCap.caps.get("thaumium"));
        return stack;
    }

    public static ItemStack withPrerequisites(RecipeInfusionWandAugmentation source, ItemStack stack) {
        IAugmentationWand[] prerequisites = source.augmentation.getPrerequisiteAugmentations();
        return prerequisites == null ? stack : WandHelper.addAugmentationsTo(stack, prerequisites, true);
    }

    public static boolean isApplicable(RecipeInfusionWandAugmentation source, ItemStack input) {
        ItemWandCasting wand = (ItemWandCasting) input.getItem();
        if (!source.augmentation.isCompatibleWith(wand.getRod(input))
            || WandHelper.hasAugmentation(input, source.augmentation)) return false;
        IAugmentationWand[] prerequisites = source.augmentation.getPrerequisiteAugmentations();
        if (prerequisites != null) {
            for (IAugmentationWand prerequisite : prerequisites) {
                if (!WandHelper.hasAugmentation(input, prerequisite)) return false;
            }
        }
        return true;
    }

    public static void addView(List<Object> display, RecipeInfusionWandAugmentation source, ItemStack stack) {
        if (!isApplicable(source, stack)) return;
        ItemStack input = stack.copy();
        input.stackSize = 1;
        display.add(new WandAugmentationDisplayRecipe(source, input));
    }
}
