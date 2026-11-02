package com.science.gtnl.mixins.late.appliedEnergistics.meminiterminal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import appeng.client.gui.widgets.GuiImgButton;

@Mixin(value = GuiImgButton.class, remap = false)
public interface AccessorGuiImgButton {

    @Invoker("getIconIndex")
    int gtnl$getIconIndex();
}
