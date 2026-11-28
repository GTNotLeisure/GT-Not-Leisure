package com.science.gtnl.mixins.late.thaumcraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.science.gtnl.common.recipe.thaumcraft.ArcanePatternRecipes;

import thaumcraft.api.ThaumcraftApiHelper;
import thaumcraft.api.internal.IInternalMethodHandler;

@Mixin(value = ThaumcraftApiHelper.class, remap = false)
public class MixinArcanePatternResearch {

    @WrapOperation(
        method = "isResearchComplete",
        at = @At(
            value = "INVOKE",
            target = "Lthaumcraft/api/internal/IInternalMethodHandler;isResearchComplete(Ljava/lang/String;Ljava/lang/String;)Z"),
        require = 1)
    private static boolean gtnl$checkEncodedResearch(IInternalMethodHandler handler, String player, String key,
        Operation<Boolean> original) {
        Boolean replay = ArcanePatternRecipes.replayResearch(player, key);
        if (replay != null) return replay;
        boolean granted = original.call(handler, player, key);
        ArcanePatternRecipes.recordResearch(player, key, granted);
        return granted;
    }
}
