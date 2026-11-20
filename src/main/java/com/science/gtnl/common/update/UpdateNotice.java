package com.science.gtnl.common.update;

import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

public record UpdateNotice(String installedVersion, String availableVersion) {

    public static final String TRANSLATION_KEY = "gtnl.update.available";

    public IChatComponent toChatMessage(String downloadUrl) {
        return new ChatComponentTranslation(TRANSLATION_KEY, installedVersion, availableVersion).setChatStyle(
            new ChatStyle().setColor(EnumChatFormatting.YELLOW)
                .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, downloadUrl)));
    }

    public static UpdateNotice fromChat(IChatComponent component) {
        if (!(component instanceof ChatComponentTranslation translation)
            || !TRANSLATION_KEY.equals(translation.getKey())) return null;
        Object[] arguments = translation.getFormatArgs();
        if (arguments.length != 2 || !(arguments[0] instanceof String installed)
            || !(arguments[1] instanceof String available)) return null;
        return new UpdateNotice(installed, available);
    }
}
