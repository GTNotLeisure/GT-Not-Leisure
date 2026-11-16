package com.science.gtnl.common.update;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.event.ClickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.Tags;
import com.science.gtnl.config.MessageConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/** Server-thread notifications for dedicated servers; integrated servers use the client notifier. */
public final class ServerUpdateNotifier {

    private static ServerUpdateNotifier instance;
    private final MinecraftServer server;
    private final CompletableFuture<UpdateChecker.Result> check;
    private final Set<UUID> notified = new HashSet<>();
    private UpdateChecker.Result result;
    private boolean finished;
    private int ticks;

    private ServerUpdateNotifier(MinecraftServer server) {
        this.server = server;
        check = UpdateChecker.start(server.getFile("config"));
    }

    public static void start(MinecraftServer server) {
        stop();
        if (!server.isDedicatedServer() || !MessageConfig.enableServerUpdateCheck) return;
        instance = new ServerUpdateNotifier(server);
        FMLCommonHandler.instance()
            .bus()
            .register(instance);
    }

    public static void stop() {
        if (instance != null) {
            FMLCommonHandler.instance()
                .bus()
                .unregister(instance);
            instance = null;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (finished) {
            return;
        }
        ticks++;
        if (ticks < 20) {
            return;
        }
        ticks = 0;
        result = check.getNow(null);
        if (result == null) return;
        finished = true;
        if (!result.hasUpdate()) {
            return;
        }
        ScienceNotLeisure.LOG.info(
            "GTNL server update available: {} -> {}. Check modpack compatibility and GTNLLib requirements. {}",
            Tags.VERSION,
            result.version,
            UpdateChecker.RELEASES);
        for (Object player : server.getConfigurationManager().playerEntityList) {
            notifyOperator((EntityPlayerMP) player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) notifyOperator((EntityPlayerMP) event.player);
    }

    private void notifyOperator(EntityPlayerMP player) {
        if (result == null || !result.hasUpdate()) {
            return;
        }
        if (!player.canCommandSenderUseCommand(2, "")) {
            return;
        }
        if (!notified.add(player.getUniqueID())) {
            return;
        }
        player.addChatMessage(
            new ChatComponentTranslation("gtnl.update.server_available", Tags.VERSION, result.version).setChatStyle(
                new ChatStyle().setColor(EnumChatFormatting.YELLOW)
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, UpdateChecker.RELEASES))));
        player.addChatMessage(new ChatComponentTranslation("gtnl.update.compatibility"));
    }
}
