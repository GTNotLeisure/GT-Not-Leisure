package com.science.gtnl.common.recipe.gtnl;

import java.util.List;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.science.gtnl.api.IRecipePool;
import com.science.gtnl.common.machine.multiblock.GrandAssemblyLine;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.utils.recipes.RecipeBuilder;
import com.science.gtnl.utils.recipes.RecipeInputConflictResolver;

import gregtech.api.enums.ItemList;
import gregtech.api.util.GTRecipe;
import gtnhlanth.common.register.LanthItemList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import tectech.thing.CustomItemList;

public class GrandAssemblyLineSpecialRecipes implements IRecipePool {

    // Circuits fixed by hand before automatic resolution existed; kept so encoded patterns stay valid.
    private static final Object2IntOpenHashMap<ItemId> PRESET_CIRCUITS = new Object2IntOpenHashMap<>();

    @Override
    public void loadRecipes() {
        preset(ItemList.Casing_Dim_Injector.get(1), 1);
        preset(ItemList.Casing_Dim_Trans.get(1), 2);

        preset(LanthItemList.LUV_BEAMLINE_INPUT_HATCH, 1);
        preset(LanthItemList.LUV_BEAMLINE_OUTPUT_HATCH, 2);

        preset(new ItemStack(LanthItemList.FOCUS_MANIPULATION_CASING), 1);
        preset(new ItemStack(LanthItemList.TARGET_RECEPTACLE_CASING), 2);

        preset(CustomItemList.SpacetimeCompressionFieldGeneratorTier0.get(1), 1);
        preset(CustomItemList.SpacetimeCompressionFieldGeneratorTier1.get(1), 2);
        preset(CustomItemList.SpacetimeCompressionFieldGeneratorTier2.get(1), 3);
        preset(CustomItemList.SpacetimeCompressionFieldGeneratorTier3.get(1), 4);
        preset(CustomItemList.SpacetimeCompressionFieldGeneratorTier4.get(1), 5);
        preset(CustomItemList.SpacetimeCompressionFieldGeneratorTier5.get(1), 6);
        preset(CustomItemList.SpacetimeCompressionFieldGeneratorTier6.get(1), 7);
        preset(CustomItemList.SpacetimeCompressionFieldGeneratorTier7.get(1), 8);
        preset(CustomItemList.SpacetimeCompressionFieldGeneratorTier8.get(1), 9);

        preset(CustomItemList.TimeAccelerationFieldGeneratorTier0.get(1), 1);
        preset(CustomItemList.TimeAccelerationFieldGeneratorTier1.get(1), 2);
        preset(CustomItemList.TimeAccelerationFieldGeneratorTier2.get(1), 3);
        preset(CustomItemList.TimeAccelerationFieldGeneratorTier3.get(1), 4);
        preset(CustomItemList.TimeAccelerationFieldGeneratorTier4.get(1), 5);
        preset(CustomItemList.TimeAccelerationFieldGeneratorTier5.get(1), 6);
        preset(CustomItemList.TimeAccelerationFieldGeneratorTier6.get(1), 7);
        preset(CustomItemList.TimeAccelerationFieldGeneratorTier7.get(1), 8);
        preset(CustomItemList.TimeAccelerationFieldGeneratorTier8.get(1), 9);

        preset(CustomItemList.StabilisationFieldGeneratorTier0.get(1), 1);
        preset(CustomItemList.StabilisationFieldGeneratorTier1.get(1), 2);
        preset(CustomItemList.StabilisationFieldGeneratorTier2.get(1), 3);
        preset(CustomItemList.StabilisationFieldGeneratorTier3.get(1), 4);
        preset(CustomItemList.StabilisationFieldGeneratorTier4.get(1), 5);
        preset(CustomItemList.StabilisationFieldGeneratorTier5.get(1), 6);
        preset(CustomItemList.StabilisationFieldGeneratorTier6.get(1), 7);
        preset(CustomItemList.StabilisationFieldGeneratorTier7.get(1), 8);
        preset(CustomItemList.StabilisationFieldGeneratorTier8.get(1), 9);
    }

    private static void preset(ItemStack output, int circuit) {
        if (output == null || circuit <= 0) return;
        PRESET_CIRCUITS.put(ItemId.create(output), circuit);
    }

    /**
     * Runs once every assembly line recipe is registered.
     */
    public static void resolveConflicts() {
        List<GTRecipe.RecipeAssemblyLine> recipes = GTRecipe.RecipeAssemblyLine.sAssemblylineRecipes;
        RecipeInputConflictResolver resolver = new RecipeInputConflictResolver("GrandAssemblyLine", true);
        for (GTRecipe.RecipeAssemblyLine recipe : recipes) {
            int preset = recipe.mOutput == null ? 0 : PRESET_CIRCUITS.getInt(ItemId.create(recipe.mOutput));
            resolver.recipe(recipe)
                .presetCircuit(preset)
                .add();
        }
        RecipeInputConflictResolver.Result result = resolver.resolve();

        GrandAssemblyLine.specialRecipe.clear();
        ObjectOpenHashSet<DisplayKey> displayed = new ObjectOpenHashSet<>();
        for (int i = 0; i < recipes.size(); i++) {
            int circuit = result.circuit(i);
            if (circuit <= 0) continue;
            GTRecipe.RecipeAssemblyLine recipe = recipes.get(i);
            GrandAssemblyLine.specialRecipe.put(recipe.getPersistentHash(), circuit);
            if (displayed.add(new DisplayKey(ItemId.create(recipe.mOutput), circuit))) {
                registerDisplayRecipe(recipe.mOutput, circuit);
            }
        }
    }

    private static void registerDisplayRecipe(ItemStack output, int circuit) {
        ItemStack displayOutput = output.copy();
        displayOutput.stackSize = 1;
        RecipeBuilder.builder()
            .itemInputs()
            .circuit(circuit)
            .itemOutputs(displayOutput)
            .fake()
            .duration(0)
            .eut(0)
            .addTo(GTNLRecipeMaps.GrandAssemblyLineSpecialRecipes);
    }

    private record DisplayKey(ItemId output, int circuit) {}
}
