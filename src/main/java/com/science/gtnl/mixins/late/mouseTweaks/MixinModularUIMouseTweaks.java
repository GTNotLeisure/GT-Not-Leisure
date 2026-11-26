package com.science.gtnl.mixins.late.mouseTweaks;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.cleanroommc.modularui.api.IMuiScreen;
import com.cleanroommc.modularui.api.widget.IVanillaSlot;
import com.cleanroommc.modularui.api.widget.IWidget;

@Deprecated
@Pseudo
@Mixin(targets = "yalter.mousetweaks.DeobfuscationLayer", remap = false)
public abstract class MixinModularUIMouseTweaks {

    @Inject(
        method = "getSelectedSlot(Lnet/minecraft/client/gui/inventory/GuiContainer;Lnet/minecraft/inventory/Container;I)Lnet/minecraft/inventory/Slot;",
        at = @At("HEAD"),
        cancellable = true)
    private static void gtnl$selectHoveredModularSlot(GuiContainer gui, Container container, int slotCount,
        CallbackInfoReturnable<Slot> cir) {
        if (!(gui instanceof IMuiScreen muiScreen)) return;

        // Respect scrolling, clipping and panel order instead of raw vanilla slot coordinates.
        IWidget hovered = muiScreen.getScreen()
            .getContext()
            .getTopHovered();
        Slot selectedSlot = hovered instanceof IVanillaSlot vanillaSlot && vanillaSlot.handleAsVanillaSlot()
            ? vanillaSlot.getVanillaSlot()
            : null;
        cir.setReturnValue(selectedSlot);
    }
}
