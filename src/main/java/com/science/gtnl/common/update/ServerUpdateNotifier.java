package com.science.gtnl.common.update;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.config.MessageConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ServerUpdateNotifier {

    private static ServerUpdateNotifier instance;
    private final MinecraftServer server;
    private final CompletableFuture<UpdateChecker.Result> check;
    private final Set<UUID> notified = new HashSet<>();
    private UpdateChecker.Result result;
    private boolean finished;
    private int ticks;

    private ServerUpdateNotifier(MinecraftServer server) {
        this.server = server;
        check = UpdateChecker.start();
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
            "GTNL server update available: {} -> {}. {}",
            UpdateChecker.currentVersion(),
            result.version,
            result.downloadUrl());
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
            new UpdateNotice(UpdateChecker.currentVersion(), result.version).toChatMessage(result.downloadUrl()));
    }
}
