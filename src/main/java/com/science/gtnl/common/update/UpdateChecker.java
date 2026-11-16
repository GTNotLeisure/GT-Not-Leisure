package com.science.gtnl.common.update;

import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.Tags;

import cpw.mods.fml.common.versioning.ComparableVersion;

/** Shared lookup. Network and cache I/O run exclusively on a daemon worker. */
public final class UpdateChecker {

    public static final String RELEASES = "https://github.com/GTNotLeisure/GT-Not-Leisure/releases";
    private static CompletableFuture<Result> check;

    private UpdateChecker() {}

    public static synchronized CompletableFuture<Result> start(File configDirectory) {
        if (check != null) return check;
        CompletableFuture<Result> result = new CompletableFuture<>();
        check = result;
        if (!isStableVersion(Tags.VERSION)) {
            result.complete(new Result(null, "Development version; update check skipped"));
            return result;
        }
        File cache = new File(configDirectory, "GTNotLeisure/update-check.json");
        Thread worker = new Thread(() -> result.complete(lookup(cache)), "GTNL update check");
        worker.setDaemon(true);
        worker.start();
        return result;
    }

    private static Result lookup(File cache) {
        try {
            if (cache.isFile()) {
                JsonObject saved = new JsonParser()
                    .parse(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8))
                    .getAsJsonObject();
                long age = System.currentTimeMillis() - saved.get("checkedAt")
                    .getAsLong();
                if (age >= 0 && age < TimeUnit.HOURS.toMillis(24)) {
                    String version = saved.get("version")
                        .getAsString();
                    if (isStableVersion(version)) return new Result(version, null);
                }
            }
        } catch (Exception e) {
            ScienceNotLeisure.LOG.debug("Ignoring unreadable GTNL update cache", e);
        }

        HttpURLConnection connection = null;
        try {
            // /latest may point to a non-versioned dev-build release.
            connection = (HttpURLConnection) new URL(
                "https://api.github.com/repos/GTNotLeisure/GT-Not-Leisure/releases?per_page=100").openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "GTNotLeisure-update-check");
            int status = connection.getResponseCode();
            if (status != 200) throw new java.io.IOException("GitHub HTTP " + status);
            String latest = null;
            try (
                InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                for (JsonElement element : new JsonParser().parse(reader)
                    .getAsJsonArray()) {
                    JsonObject release = element.getAsJsonObject();
                    if (release.get("draft")
                        .getAsBoolean()
                        || release.get("prerelease")
                            .getAsBoolean())
                        continue;
                    String version = release.get("tag_name")
                        .getAsString();
                    if (isStableVersion(version) && (latest == null || compare(version, latest) > 0)) latest = version;
                }
            }
            if (latest == null) throw new java.io.IOException("No stable numeric release in the latest 100 releases");
            try {
                JsonObject saved = new JsonObject();
                saved.addProperty("checkedAt", System.currentTimeMillis());
                saved.addProperty("version", latest);
                Files.createDirectories(
                    cache.toPath()
                        .getParent());
                Files.write(
                    cache.toPath(),
                    saved.toString()
                        .getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                ScienceNotLeisure.LOG.debug("Could not save GTNL update cache", e);
            }
            return new Result(latest, null);
        } catch (Exception e) {
            String failure = e.getClass()
                .getSimpleName() + ": "
                + e.getMessage();
            ScienceNotLeisure.LOG.info("GTNL update check unavailable (no retry this launch): {}", failure);
            return new Result(null, failure);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isStableVersion(String version) {
        return version.matches("[vV]?[0-9]+(\\.[0-9]+)*(?:-hotfix[0-9]+)?");
    }

    private static int compare(String left, String right) {
        return new ComparableVersion(left.replaceFirst("^[vV]", ""))
            .compareTo(new ComparableVersion(right.replaceFirst("^[vV]", "")));
    }

    public static final class Result {

        public final String version;
        public final String failure;

        private Result(String version, String failure) {
            this.version = version;
            this.failure = failure;
        }

        public boolean hasUpdate() {
            return version != null && compare(version, Tags.VERSION) > 0;
        }
    }
}
