package com.science.gtnl.utils.recipes.metadata;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;
import com.science.gtnl.common.circuit.CircuitCatalog;
import com.science.gtnl.common.circuit.CircuitFamily;
import com.science.gtnl.utils.recipes.data.CircuitRecipeRequirement;

import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMetadataKey;
import gregtech.nei.RecipeDisplayInfo;

public class TreeDiagramCircuitMetadata extends RecipeMetadataKey<CircuitRecipeRequirement> {

    public static final TreeDiagramCircuitMetadata INSTANCE = new TreeDiagramCircuitMetadata();

    public TreeDiagramCircuitMetadata() {
        super(CircuitRecipeRequirement.class, "tree_diagram_circuit");
    }

    @Override
    public void drawInfo(RecipeDisplayInfo recipeInfo, Object value) {
        if (!(value instanceof CircuitRecipeRequirement requirement)) return;
        int tier = Math.max(0, Math.min(14, requirement.circuitTier()));
        drawWrapped(
            recipeInfo,
            StatCollector.translateToLocalFormatted("gtnl.tree_diagram.nei.circuit_tier", GTValues.VN[tier]));
        drawWrapped(
            recipeInfo,
            StatCollector
                .translateToLocalFormatted("gtnl.tree_diagram.nei.nanite_tier", requirement.requiredNaniteTier()));
        drawWrapped(
            recipeInfo,
            StatCollector.translateToLocalFormatted(
                "gtnl.tree_diagram.nei.computation",
                NumberFormatUtil.formatNumber(requirement.baseComputation())));
        CircuitFamily family = CircuitCatalog.INSTANCE.family(requirement.familyId());
        if (family != null) {
            drawWrapped(
                recipeInfo,
                StatCollector.translateToLocalFormatted(
                    "gtnl.tree_diagram.nei.unlock",
                    NumberFormatUtil.formatNumber(family.requiredItems()),
                    family.unlockItem()
                        .toStack(1)
                        .getDisplayName()));
        }
    }

    private static void drawWrapped(RecipeDisplayInfo recipeInfo, String text) {
        recipeInfo.drawTextMultipleLines(Minecraft.getMinecraft().fontRenderer.listFormattedStringToWidth(text, 158));
    }
}
