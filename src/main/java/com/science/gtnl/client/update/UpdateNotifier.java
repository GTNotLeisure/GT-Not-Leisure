package com.science.gtnl.client.update;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;

import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.common.update.UpdateChecker;
import com.science.gtnl.common.update.UpdateNotice;
import com.science.gtnl.config.ClientConfig;

import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class UpdateNotifier {

    private static final long JOIN_DELAY_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long CHAT_QUIET_NANOS = TimeUnit.SECONDS.toNanos(1);
    private static final long MAX_JOIN_DELAY_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static UpdateNotifier instance;

    private final Map<UpdateNotice, IChatComponent> pending = new LinkedHashMap<>();
    private final Set<UpdateNotice> notified = new HashSet<>();
    private CompletableFuture<UpdateChecker.Result> check;
    private NetHandlerPlayClient connection;
    private boolean enabled;
    private boolean finished;
    private long joinedAt;
    private long lastChatAt;

    private UpdateNotifier() {
        applyConfig();
    }

    public static void init() {
        if (instance != null) return;
        instance = new UpdateNotifier();
        // Remain registered so a check disabled at startup can be enabled from the config GUI.
        FMLCommonHandler.instance()
            .bus()
            .register(instance);
        MinecraftForge.EVENT_BUS.register(instance);
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.PostConfigChangedEvent event) {
        if (ScienceNotLeisure.MODID.equals(event.modID)) {
            applyConfig();
        }
    }

    private void applyConfig() {
        enabled = ClientConfig.enableUpdateCheck;
        if (enabled && !finished && check == null) {
            check = UpdateChecker.start();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChatReceived(ClientChatReceivedEvent event) {
        if (event.message == null) return;
        long now = System.nanoTime();
        synchronizeConnection(Minecraft.getMinecraft(), now);
        if (connection == null) return;

        UpdateNotice notice = UpdateNotice.fromChat(event.message);
        if (notice == null) {
            lastChatAt = now;
            return;
        }

        // Server notices use the same queue even when the local update check is disabled.
        enqueue(notice, event.message);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        long now = System.nanoTime();
        synchronizeConnection(minecraft, now);
        boolean ready = connection != null && minecraft.theWorld != null && isReady(now);

        if (enabled && !finished && check != null) {
            UpdateChecker.Result result = check.getNow(null);
            if (result != null && (!result.hasUpdate() || ready)) {
                if (result.hasUpdate()) {
                    UpdateNotice notice = new UpdateNotice(UpdateChecker.currentVersion(), result.version);
                    enqueue(notice, notice.toChatMessage(result.downloadUrl()));
                }
                // Consume the local result only when it can be displayed or has no update.
                finished = true;
            }
        }

        if (!ready) return;
        for (Map.Entry<UpdateNotice, IChatComponent> entry : pending.entrySet()) {
            minecraft.thePlayer.addChatMessage(entry.getValue());
            notified.add(entry.getKey());
        }
        pending.clear();
    }

    private void synchronizeConnection(Minecraft minecraft, long now) {
        NetHandlerPlayClient current = minecraft.getNetHandler();
        if (connection == current) return;
        connection = current;
        pending.clear();
        notified.clear();
        joinedAt = now;
        lastChatAt = now;
    }

    private boolean isReady(long now) {
        long joinedFor = now - joinedAt;
        // Bound quiet-time waiting so a busy server cannot indefinitely hide the notice.
        return joinedFor >= JOIN_DELAY_NANOS
            && (now - lastChatAt >= CHAT_QUIET_NANOS || joinedFor >= MAX_JOIN_DELAY_NANOS);
    }

    private void enqueue(UpdateNotice notice, IChatComponent message) {
        if (!notified.contains(notice)) pending.putIfAbsent(notice, message);
    }
}
