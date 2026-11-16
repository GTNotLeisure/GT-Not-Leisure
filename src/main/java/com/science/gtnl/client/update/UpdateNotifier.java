package com.science.gtnl.client.update;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.Tags;
import com.science.gtnl.common.update.UpdateChecker;
import com.science.gtnl.config.ClientConfig;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class UpdateNotifier {

    private final File configDirectory;
    private CompletableFuture<UpdateChecker.Result> check;
    private boolean enabled;
    private boolean finished;
    private int ticks;

    private UpdateNotifier(File configDirectory) {
        this.configDirectory = configDirectory;
        applyConfig();
    }

    public static void init(File configDirectory) {
        // Remain registered so a check disabled at startup can be enabled from the config GUI.
        FMLCommonHandler.instance()
            .bus()
            .register(new UpdateNotifier(configDirectory));
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.PostConfigChangedEvent event) {
        if (ScienceNotLeisure.MODID.equals(event.modID)) {
            applyConfig();
        }
    }

    private void applyConfig() {
        enabled = ClientConfig.enableUpdateCheck;
        ticks = 0;
        if (enabled && !finished && check == null) {
            check = UpdateChecker.start(configDirectory);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!enabled || finished) {
            return;
        }
        ticks++;
        if (ticks < 20) {
            return;
        }
        ticks = 0;
        UpdateChecker.Result result = check.getNow(null);
        if (result == null) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (result.hasUpdate()) {
            if (minecraft.thePlayer == null || minecraft.theWorld == null) return;
            minecraft.thePlayer.addChatMessage(
                new ChatComponentTranslation("gtnl.update.available", Tags.VERSION, result.version).setChatStyle(
                    new ChatStyle().setColor(EnumChatFormatting.YELLOW)
                        .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, result.downloadUrl()))));
        }
        // Keep the config listener, but stop consuming results for this launch.
        finished = true;
    }
}
