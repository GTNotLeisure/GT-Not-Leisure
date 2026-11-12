package com.science.gtnl.common.recipe.thaumcraft;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import com.science.gtnl.api.IRecipePool;
import com.science.gtnl.common.item.wand.MajoWandStage;
import com.science.gtnl.compat.thaumcraft.TCResearches;
import com.science.gtnl.loader.ItemLoader;
import com.science.gtnl.utils.recipes.RecipeBuilder;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTOreDictUnificator;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.api.research.ResearchPage;
import thaumcraft.common.config.ConfigItems;

public class MajoWandRecipes implements IRecipePool {

    @Override
    public void loadRecipes() {
        addCoreRecipe(
            MajoWandStage.DEPARTURE,
            TierEU.RECIPE_LV,
            15,
            144,
            new ItemStack(ConfigItems.itemWandRod, 1, 0),
            material(OrePrefixes.ingot, Materials.Thaumium, 2),
            material(OrePrefixes.ingot, Materials.Gold, 2),
            new ItemStack(Items.string, 4),
            material(OrePrefixes.circuit, Materials.LV, 1));
        addCoreRecipe(
            MajoWandStage.RESONANCE,
            TierEU.RECIPE_MV,
            30,
            288,
            new ItemStack(ConfigItems.itemWandRod, 1, 2),
            material(OrePrefixes.plate, Materials.Thaumium, 4),
            material(OrePrefixes.wireFine, Materials.Electrum, 8),
            material(OrePrefixes.circuit, Materials.MV, 2));
        addCoreRecipe(
            MajoWandStage.STARLIGHT,
            TierEU.RECIPE_HV,
            45,
            576,
            new ItemStack(ConfigItems.itemShard, 2, 6),
            material(OrePrefixes.plate, Materials.StainlessSteel, 4),
            material(OrePrefixes.circuit, Materials.HV, 2),
            ItemList.Field_Generator_HV.get(1));
        addCoreRecipe(
            MajoWandStage.VOID,
            TierEU.RECIPE_EV,
            60,
            1152,
            new ItemStack(ConfigItems.itemResource, 8, 16),
            material(OrePrefixes.plate, Materials.Titanium, 4),
            material(OrePrefixes.circuit, Materials.EV, 2),
            ItemList.Field_Generator_EV.get(1));
        addCoreRecipe(
            MajoWandStage.ASHEN_LEGACY,
            TierEU.RECIPE_LuV,
            90,
            2304,
            new ItemStack(ConfigItems.itemResource, 16, 16),
            material(OrePrefixes.plate, Materials.TungstenSteel, 8),
            material(OrePrefixes.circuit, Materials.LuV, 4),
            ItemList.Field_Generator_LuV.get(2));
        addCoreRecipe(
            MajoWandStage.JOURNEYS_END,
            TierEU.RECIPE_ZPM,
            120,
            4608,
            material(OrePrefixes.plate, Materials.HSSG, 8),
            material(OrePrefixes.ingot, Materials.Iridium, 8),
            new ItemStack(ConfigItems.itemResource, 16, 16),
            material(OrePrefixes.circuit, Materials.ZPM, 4),
            ItemList.Field_Generator_ZPM.get(2));
    }

    private static void addCoreRecipe(MajoWandStage stage, long voltage, int seconds, int solder,
        ItemStack... components) {
        RecipeBuilder recipe = RecipeBuilder.builder()
            .itemInputs(components)
            .itemOutputs(core(stage))
            .duration(seconds * 20)
            .eut(voltage);
        if (solder > 0) recipe.fluidInputs(Materials.SolderingAlloy.getMolten(solder));
        recipe.addTo(RecipeMaps.assemblerRecipes);
    }

    public static void registerResearches() {
        MajoWandStage departure = MajoWandStage.DEPARTURE;
        InfusionRecipe firstWand = ThaumcraftApi.addInfusionCraftingRecipe(
            departure.getResearch(),
            ItemLoader.majoWand.createStack(departure),
            2,
            new AspectList().add(Aspect.MAGIC, 20)
                .add(Aspect.TOOL, 16)
                .add(Aspect.AURA, 16),
            core(departure),
            new ItemStack[] { material(OrePrefixes.plate, Materials.Thaumium, 1),
                material(OrePrefixes.plate, Materials.Thaumium, 1), new ItemStack(ConfigItems.itemResource, 1, 7),
                new ItemStack(ConfigItems.itemResource, 1, 7) });
        research(departure).setPages(new ResearchPage(text(departure)), new ResearchPage(firstWand))
            .setParents(TCResearches.ROOT_RESEARCH, "ROD_greatwood", "THAUMIUM", "INFUSION")
            .registerResearchItem();

        registerUpgrade(
            MajoWandStage.RESONANCE,
            "ROD_silverwood",
            3,
            new AspectList().add(Aspect.MAGIC, 32)
                .add(Aspect.AURA, 32)
                .add(Aspect.TREE, 32),
            core(MajoWandStage.RESONANCE),
            new ItemStack(ConfigItems.itemWandRod, 1, 2),
            new ItemStack(ConfigItems.itemShard, 1, 0),
            new ItemStack(ConfigItems.itemShard, 1, 1),
            new ItemStack(ConfigItems.itemShard, 1, 2),
            new ItemStack(ConfigItems.itemShard, 1, 3),
            new ItemStack(ConfigItems.itemShard, 1, 4),
            new ItemStack(ConfigItems.itemShard, 1, 5));
        registerUpgrade(
            MajoWandStage.STARLIGHT,
            "CAP_thaumium",
            5,
            primals(32).add(Aspect.MAGIC, 64)
                .add(Aspect.ENERGY, 64),
            core(MajoWandStage.STARLIGHT),
            new ItemStack(ConfigItems.itemShard, 1, 6),
            new ItemStack(ConfigItems.itemShard, 1, 6),
            new ItemStack(ConfigItems.itemResource, 1, 2),
            new ItemStack(ConfigItems.itemResource, 1, 2),
            new ItemStack(ConfigItems.itemResource, 1, 15),
            new ItemStack(Items.ender_pearl),
            new ItemStack(Items.ender_pearl));
        registerUpgrade(
            MajoWandStage.VOID,
            "VOIDMETAL",
            7,
            new AspectList().add(Aspect.ELDRITCH, 96)
                .add(Aspect.VOID, 96)
                .add(Aspect.MAGIC, 96)
                .add(Aspect.ENERGY, 96),
            core(MajoWandStage.VOID),
            new ItemStack(ConfigItems.itemResource, 1, 16),
            new ItemStack(ConfigItems.itemResource, 1, 16),
            new ItemStack(ConfigItems.itemResource, 1, 16),
            new ItemStack(ConfigItems.itemResource, 1, 16),
            new ItemStack(ConfigItems.itemShard, 1, 6),
            new ItemStack(ConfigItems.itemShard, 1, 6),
            new ItemStack(Items.nether_star));
        registerUpgrade(
            MajoWandStage.ASHEN_LEGACY,
            "VOIDMETAL",
            7,
            new AspectList().add(Aspect.MAGIC, 128)
                .add(Aspect.AURA, 128)
                .add(Aspect.ELDRITCH, 96)
                .add(Aspect.LIFE, 96),
            core(MajoWandStage.ASHEN_LEGACY),
            new ItemStack(ConfigItems.itemResource, 1, 15),
            new ItemStack(ConfigItems.itemResource, 1, 17),
            new ItemStack(ConfigItems.itemResource, 1, 17),
            new ItemStack(ConfigItems.itemShard, 1, 6),
            new ItemStack(ConfigItems.itemShard, 1, 6),
            new ItemStack(ConfigItems.itemResource, 1, 15),
            new ItemStack(ConfigItems.itemResource, 1, 15));
        registerUpgrade(
            MajoWandStage.JOURNEYS_END,
            MajoWandStage.ASHEN_LEGACY.getResearch(),
            9,
            primals(96).add(Aspect.MAGIC, 192)
                .add(Aspect.AURA, 192)
                .add(Aspect.ELDRITCH, 128)
                .add(Aspect.ENERGY, 128)
                .add(Aspect.TRAVEL, 128),
            core(MajoWandStage.JOURNEYS_END),
            material(OrePrefixes.ingot, Materials.Iridium, 1),
            material(OrePrefixes.ingot, Materials.Iridium, 1),
            material(OrePrefixes.ingot, Materials.HSSG, 1),
            material(OrePrefixes.ingot, Materials.HSSG, 1),
            new ItemStack(ConfigItems.itemShard, 1, 6),
            new ItemStack(ConfigItems.itemResource, 1, 15),
            new ItemStack(Items.nether_star));
    }

    private static void registerUpgrade(MajoWandStage stage, String prerequisite, int instability, AspectList aspects,
        ItemStack... components) {
        InfusionRecipe recipe = new MajoWandUpgradeRecipe(stage, instability, aspects, components);
        ThaumcraftApi.getCraftingRecipes()
            .add(recipe);
        MajoWandStage previous = MajoWandStage.fromMetadata(stage.ordinal() - 1);
        ResearchItem research = research(stage).setPages(new ResearchPage(text(stage)), new ResearchPage(recipe))
            .setParents(previous.getResearch(), "INFUSION")
            .setConcealed();
        if (!prerequisite.equals(previous.getResearch())) research.setParentsHidden(prerequisite);
        research.registerResearchItem();
    }

    private static ResearchItem research(MajoWandStage stage) {
        return new ResearchItem(
            stage.getResearch(),
            "gtnl",
            new AspectList().add(Aspect.MAGIC, 3 + stage.ordinal())
                .add(Aspect.TOOL, 3 + stage.ordinal())
                .add(Aspect.TRAVEL, 3 + stage.ordinal()),
            2 + stage.ordinal() * 2,
            -6,
            Math.min(1 + stage.ordinal(), 3),
            ItemLoader.majoWand.createStack(stage));
    }

    private static String text(MajoWandStage stage) {
        return "tc.research_text." + stage.getResearch() + ".0";
    }

    private static ItemStack core(MajoWandStage stage) {
        return ItemLoader.majoWandCore.createStack(stage);
    }

    private static ItemStack material(OrePrefixes prefix, Materials material, int amount) {
        return GTOreDictUnificator.get(prefix, material, amount);
    }

    private static AspectList primals(int amount) {
        AspectList aspects = new AspectList();
        for (Aspect aspect : Aspect.getPrimalAspects()) {
            aspects.add(aspect, amount);
        }
        return aspects;
    }
}
