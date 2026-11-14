package com.science.gtnl.common.gui.modularui.widget;

import java.util.function.Supplier;

import net.minecraft.client.gui.FontRenderer;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.text.TextRenderer;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.widgets.TextWidget;

public class FittedTextWidget extends TextWidget<FittedTextWidget> {

    public FittedTextWidget(Supplier<String> text, float textScale) {
        super(IKey.dynamic(text));
        scale(textScale);
        textAlign(Alignment.CenterLeft);
        color(0xFF202020);
        shadow(false);
        height(12);
        tooltipBuilder(tooltip -> tooltip.addLine(IKey.dynamic(text))).tooltipAutoUpdate(true);
    }

    @Override
    protected String checkString() {
        String full = super.checkString();
        FontRenderer font = TextRenderer.getFontRenderer();
        int width = Math.max(1, (int) (getArea().paddedWidth() / getScale()));
        if (font.getStringWidth(full) <= width) return full;
        int ellipsisWidth = font.getStringWidth("…");
        return width > ellipsisWidth ? font.trimStringToWidth(full, width - ellipsisWidth) + "…"
            : font.trimStringToWidth(full, width);
    }
}
