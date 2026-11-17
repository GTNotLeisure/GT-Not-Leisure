package com.science.gtnl.common.gui.modularui.widget;

import java.util.Objects;
import java.util.function.Consumer;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;

public class DescribedItemSlot extends ItemSlot {

    private final Consumer<RichTooltip> description;

    public DescribedItemSlot(Consumer<RichTooltip> description) {
        this.description = Objects.requireNonNull(description);
        tooltipBuilder(description);
    }

    @Override
    public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
        super.buildTooltip(stack, tooltip);
        description.accept(tooltip);
    }
}
