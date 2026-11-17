package com.science.gtnl.mixins.early.gregtech;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.science.gtnl.common.gui.recipe.RecipeAreaHooks;

import codechicken.nei.recipe.GuiRecipe;
import codechicken.nei.recipe.TemplateRecipeHandler;
import gregtech.api.recipe.RecipeMapFrontend;
import gregtech.nei.GTNEIDefaultHandler;

@Mixin(value = GTNEIDefaultHandler.class, remap = false)
public abstract class MixinGTNEIDefaultHandler extends TemplateRecipeHandler {

    @Shadow
    @Final
    protected RecipeMapFrontend frontend;

    @Inject(method = "handleTooltip", at = @At("RETURN"), remap = false)
    private void gtnl$areaTooltip(GuiRecipe<?> gui, List<String> currentTip, int recipe,
        CallbackInfoReturnable<List<String>> cir) {
        RecipeAreaHooks.tooltip(frontend, gui, arecipes, recipe, cir.getReturnValue());
    }

    @Override
    public boolean mouseClicked(GuiRecipe<?> gui, int button, int recipe) {
        return RecipeAreaHooks.click(frontend, gui, arecipes, recipe, button)
            || super.mouseClicked(gui, button, recipe);
    }
}
