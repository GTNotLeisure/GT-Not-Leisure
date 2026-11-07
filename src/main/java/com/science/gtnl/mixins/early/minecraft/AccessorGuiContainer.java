package com.science.gtnl.mixins.early.minecraft;

import net.minecraft.client.gui.inventory.GuiContainer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiContainer.class)
public interface AccessorGuiContainer {

    @Accessor("guiLeft")
    int gtnl$getGuiLeft();

    @Accessor("guiTop")
    int gtnl$getGuiTop();

    @Accessor("ySize")
    int gtnl$getYSize();
}
