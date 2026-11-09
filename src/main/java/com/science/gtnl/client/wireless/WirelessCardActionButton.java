package com.science.gtnl.client.wireless;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.layout.IViewportStack;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.gtnllib.gui.theme.AE2Theme;
import com.gtnllib.gui.theme.AE2Theme.Icon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class WirelessCardActionButton extends ButtonWidget<WirelessCardActionButton> {

    private final BooleanSupplier available;

    public WirelessCardActionButton(BooleanSupplier available) {
        this.available = available;
    }

    public WirelessCardActionButton plain() {
        return background(IDrawable.EMPTY).hoverBackground(IDrawable.EMPTY);
    }

    public WirelessCardActionButton text(Supplier<String> text, BooleanSupplier selected) {
        IDrawable look = AE2Theme.button(this, available, selected);
        return background(look).hoverBackground(look)
            .overlay(AE2Theme.label(this, text, available, selected));
    }

    public WirelessCardActionButton icon(Supplier<Icon> icon) {
        return plain().overlay(AE2Theme.iconButton(this, icon, available));
    }

    public WirelessCardActionButton onClick(Runnable action) {
        return onMousePressed(button -> {
            if (button != 0) return false;
            action.run();
            return true;
        });
    }

    @Override
    public boolean canBeSeen(IViewportStack stack) {
        return isEnabled() && super.canBeSeen(stack);
    }

    @Override
    public @NotNull Result onMousePressed(int button) {
        // Icon buttons sit on top of a row's open area, so a refused click must not fall through to it.
        return button == 0 && !available.getAsBoolean() ? Result.STOP : super.onMousePressed(button);
    }
}
