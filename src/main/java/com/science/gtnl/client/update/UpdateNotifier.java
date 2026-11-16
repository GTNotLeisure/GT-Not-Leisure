package com.science.gtnl.client.update;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import net.minecraft.client.Minecraft;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.Tags;
import com.science.gtnl.config.ClientConfig;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.versioning.ComparableVersion;

/** Client-only, best-effort release check. Results are consumed on the client tick thread. */
public final class UpdateNotifier {

    private static final String RELEASES = "https://github.com/GTNotLeisure/GT-Not-Leisure/releases/latest";
    private static volatile String latestVersion;
    private boolean notified;

    public static void init() {
        if (!ClientConfig.enableUpdateCheck || !isStableVersion(Tags.VERSION)) return;
        FMLCommonHandler.instance()
            .bus()
            .register(new UpdateNotifier());
        Thread worker = new Thread(UpdateNotifier::check, "GTNL update check");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean isStableVersion(String version) {
        return version.matches("[vV]?[0-9]+(\\.[0-9]+)*");
    }

    private static String normalize(String version) {
        return version.replaceFirst("^[vV]", "");
    }

    private static void check() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                "https://api.github.com/repos/GTNotLeisure/GT-Not-Leisure/releases/latest").openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "GTNotLeisure-update-check");
            if (connection.getResponseCode() != 200) return;
            try (
                InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject release = new JsonParser().parse(reader)
                    .getAsJsonObject();
                if (release.get("draft")
                    .getAsBoolean()
                    || release.get("prerelease")
                        .getAsBoolean())
                    return;
                String version = release.get("tag_name")
                    .getAsString();
                if (isStableVersion(version) && new ComparableVersion(normalize(version))
                    .compareTo(new ComparableVersion(normalize(Tags.VERSION))) > 0) {
                    latestVersion = version;
                }
            }
        } catch (Exception e) {
            ScienceNotLeisure.LOG.debug("GTNL update check unavailable", e);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || notified || !ClientConfig.enableUpdateCheck) return;
        String version = latestVersion;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (version == null || minecraft.thePlayer == null || minecraft.theWorld == null) return;
        notified = true;
        minecraft.thePlayer.addChatMessage(
            new ChatComponentTranslation("gtnl.update.available", Tags.VERSION, version).setChatStyle(
                new ChatStyle().setColor(EnumChatFormatting.YELLOW)
                    .setChatClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, RELEASES))));
        minecraft.thePlayer.addChatMessage(new ChatComponentTranslation("gtnl.update.compatibility"));
    }
}
