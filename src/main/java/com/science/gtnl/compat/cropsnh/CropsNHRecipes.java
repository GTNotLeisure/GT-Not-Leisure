package com.science.gtnl.compat.cropsnh;

import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizon.cropsnh.api.CropsNHItemList;
import com.gtnewhorizon.cropsnh.init.CropsNHFluids;
import com.science.gtnl.utils.enums.GTNLItemList;
import com.science.gtnl.utils.enums.ModsItemlist;
import com.science.gtnl.utils.recipes.RecipeBuilder;

import cpw.mods.fml.common.Optional;
import gregtech.api.enums.Mods;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;

public class CropsNHRecipes {

    public static void assembler() {
        if (!Mods.CropsNH.isModLoaded()) return;
        assemblerRecipes();
    }

    @Optional.Method(modid = "cropsnh")
    private static void assemblerRecipes() {
        RecipeBuilder.builder()
            .itemInputs(
                CropsNHItemList.IndustrialFarmController.get(8),
                CropsNHItemList.cropSticks.get(64),
                new ItemStack(Blocks.dirt, 64))
            .fluidInputs(new FluidStack(CropsNHFluids.enrichedFertilizer, 32000))
            .itemOutputs(GTNLItemList.LargeCropBreeder.get(1))
            .duration(20 * SECONDS)
            .eut(TierEU.RECIPE_MV)
            .addTo(RecipeMaps.assemblerRecipes);

        RecipeBuilder.builder()
            .itemInputs(
                CropsNHItemList.CropManager_LV.get(2),
                new ItemStack(Blocks.dirt, 64),
                CropsNHItemList.cropSticks.get(64),
                ModsItemlist.ForestryFarmValve.get(16),
                ModsItemlist.ForestryFarmGearbox.get(16),
                ModsItemlist.ForestryFarmControl.get(4))
            .itemOutputs(GTNLItemList.SteamGreenhouseModule.get(1))
            .fluidInputs(FluidRegistry.getFluidStack("liquid_sunshine", 16000))
            .duration(200)
            .eut(TierEU.RECIPE_LV)
            .addTo(RecipeMaps.assemblerRecipes);
    }
}
