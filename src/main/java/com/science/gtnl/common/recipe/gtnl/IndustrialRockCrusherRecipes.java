package com.science.gtnl.common.recipe.gtnl;

import static gregtech.api.util.GTRecipeBuilder.TICKS;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.gtnhlib.util.data.ItemId;
import com.science.gtnl.api.IRecipePool;
import com.science.gtnl.common.material.GTNLRecipeMaps;
import com.science.gtnl.utils.enums.ModsItemlist;
import com.science.gtnl.utils.recipes.RecipeBuilder;

import appeng.api.AEApi;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public class IndustrialRockCrusherRecipes implements IRecipePool {

    public static final List<ItemId> ENTRIES = new ObjectArrayList<>();
    public static final Map<ItemId, ItemStack[]> VIRTUAL_INPUTS = new Object2ObjectOpenHashMap<>();

    public RecipeMap<?> IRCR = GTNLRecipeMaps.IndustrialRockCrusherRecipes;

    @Override
    public void loadRecipes() {
        var aeBlocks = AEApi.instance()
            .definitions()
            .blocks();

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(Blocks.stone, 0))
            .itemOutputs(new ItemStack(Blocks.stone, 1))
            .duration(16)
            .eut(TierEU.RECIPE_ULV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(Blocks.cobblestone, 0))
            .itemOutputs(new ItemStack(Blocks.cobblestone, 1))
            .duration(16)
            .eut(TierEU.RECIPE_ULV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(Blocks.netherrack, 0))
            .itemOutputs(new ItemStack(Blocks.netherrack, 1))
            .duration(16)
            .eut(TierEU.RECIPE_ULV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(Items.redstone, 0))
            .itemOutputs(new ItemStack(Blocks.obsidian, 1))
            .duration(16)
            .eut(TierEU.RECIPE_HV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(GregTechAPI.sBlockGranites, 0, 0))
            .itemOutputs(new ItemStack(GregTechAPI.sBlockGranites, 1, 0))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(GregTechAPI.sBlockGranites, 0, 1))
            .itemOutputs(new ItemStack(GregTechAPI.sBlockGranites, 1, 1))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(GregTechAPI.sBlockGranites, 0, 8))
            .itemOutputs(new ItemStack(GregTechAPI.sBlockGranites, 1, 8))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(GregTechAPI.sBlockGranites, 0, 9))
            .itemOutputs(new ItemStack(GregTechAPI.sBlockGranites, 1, 9))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(GregTechAPI.sBlockStones, 0, 0))
            .itemOutputs(new ItemStack(GregTechAPI.sBlockStones, 1, 0))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(GregTechAPI.sBlockStones, 0, 1))
            .itemOutputs(new ItemStack(GregTechAPI.sBlockStones, 1, 1))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(GregTechAPI.sBlockStones, 0, 8))
            .itemOutputs(new ItemStack(GregTechAPI.sBlockStones, 1, 8))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(new ItemStack(GregTechAPI.sBlockStones, 0, 9))
            .itemOutputs(new ItemStack(GregTechAPI.sBlockStones, 1, 9))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(ModsItemlist.EtFuturumRequiemDeepslate.get(0))
            .itemOutputs(ModsItemlist.EtFuturumRequiemDeepslate.get(1))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(ModsItemlist.EtFuturumRequiemCobbledDeepslate.get(0))
            .itemOutputs(ModsItemlist.EtFuturumRequiemCobbledDeepslate.get(1))
            .duration(16)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(ModsItemlist.EtFuturumRequiemBlackstone.get(0))
            .itemOutputs(ModsItemlist.EtFuturumRequiemBlackstone.get(1))
            .duration(16)
            .eut(TierEU.RECIPE_HV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(ModsItemlist.BotaniaAndesite.get(0))
            .itemOutputs(ModsItemlist.BotaniaAndesite.get(1))
            .duration(16)
            .eut(TierEU.RECIPE_MV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(ModsItemlist.BotaniaBasalt.get(0))
            .itemOutputs(ModsItemlist.BotaniaBasalt.get(1))
            .duration(16)
            .eut(TierEU.RECIPE_MV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(ModsItemlist.BotaniaDiorite.get(0))
            .itemOutputs(ModsItemlist.BotaniaDiorite.get(1))
            .duration(16)
            .eut(TierEU.RECIPE_MV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(ModsItemlist.BotaniaGranite.get(0))
            .itemOutputs(ModsItemlist.BotaniaGranite.get(1))
            .duration(16)
            .eut(TierEU.RECIPE_MV)
            .addTo(IRCR);

        RecipeBuilder.builder()
            .itemInputs(
                GTUtility.copyAmountUnsafe(
                    0,
                    aeBlocks.skyStone()
                        .maybeStack(1)
                        .orNull()))
            .itemOutputs(
                aeBlocks.skyStone()
                    .maybeStack(1)
                    .orNull())
            .duration(20 * TICKS)
            .eut(TierEU.RECIPE_EV)
            .addTo(IRCR);

        ENTRIES.clear();
        VIRTUAL_INPUTS.clear();
        for (GTRecipe recipe : IRCR.getAllRecipes()) {
            if (recipe.mOutputs == null || recipe.mOutputs.length == 0) continue;
            ItemStack out = recipe.mOutputs[0];
            if (out == null) continue;
            ItemId id = ItemId.createWithoutNBT(out);
            if (!ENTRIES.contains(id)) {
                ENTRIES.add(id);
            }
            VIRTUAL_INPUTS.putIfAbsent(
                id,
                recipe.mInputs == null ? new ItemStack[0]
                    : Arrays.stream(recipe.mInputs)
                        .filter(Objects::nonNull)
                        .map(ItemStack::copy)
                        .toArray(ItemStack[]::new));
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean hasVirtualInputsFor(ItemId entry) {
        if (entry == null) return false;
        ItemStack[] virtualInputs = VIRTUAL_INPUTS.get(entry);
        return virtualInputs != null && virtualInputs.length > 0;
    }

    public static List<ItemStack> virtualInputsFor(ItemId entry) {
        if (!hasVirtualInputsFor(entry)) return null;
        ItemStack[] virtualInputs = VIRTUAL_INPUTS.get(entry);
        List<ItemStack> replaced = new ObjectArrayList<>(virtualInputs.length);
        for (ItemStack stack : virtualInputs) {
            if (stack == null) continue;
            ItemStack copy = stack.copy();
            copy.stackSize = 1;
            replaced.add(copy);
        }
        return replaced;
    }
}
