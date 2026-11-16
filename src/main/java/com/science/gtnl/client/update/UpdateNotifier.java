package com.science.gtnl.client.update;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.science.gtnl.Tags;
import com.science.gtnl.common.update.UpdateChecker;
import com.science.gtnl.config.ClientConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Consumes completed results without waiting for network or disk I/O on the client thread. */
public final class UpdateNotifier {

    private final CompletableFuture<UpdateChecker.Result> check;
    private int ticks;

    private UpdateNotifier(File configDirectory) {
        check = UpdateChecker.start(configDirectory);
    }

    public static void init(File configDirectory) {
        if (ClientConfig.enableUpdateCheck) {
            FMLCommonHandler.instance()
                .bus()
                .register(new UpdateNotifier(configDirectory));
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks % 20 != 0) return;
        if (!ClientConfig.enableUpdateCheck) {
            FMLCommonHandler.instance()
                .bus()
                .unregister(this);
            return;
        }
        UpdateChecker.Result result = check.getNow(null);
        if (result == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (result.hasUpdate()) {
            if (minecraft.thePlayer == null || minecraft.theWorld == null) return;
            minecraft.thePlayer.addChatMessage(
                new ChatComponentTranslation("gtnl.update.available", Tags.VERSION, result.version).setChatStyle(
                    new ChatStyle().setColor(EnumChatFormatting.YELLOW)
                        .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, UpdateChecker.RELEASES))));
            minecraft.thePlayer.addChatMessage(new ChatComponentTranslation("gtnl.update.compatibility"));
        }
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
    }
}
