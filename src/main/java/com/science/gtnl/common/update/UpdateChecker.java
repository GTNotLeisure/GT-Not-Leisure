package com.science.gtnl.common.update;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.science.gtnl.ScienceNotLeisure;
import com.science.gtnl.Tags;

import cpw.mods.fml.common.versioning.ComparableVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.VersionParser;

public final class UpdateChecker {

    private static CompletableFuture<Result> check;
    private static volatile String currentVersion = Tags.VERSION;

    private UpdateChecker() {}

    public static String currentVersion() {
        return currentVersion;
    }

    public static synchronized CompletableFuture<Result> start() {
        if (check != null) return check;
        CompletableFuture<Result> result = new CompletableFuture<>();
        check = result;
        Thread worker = new Thread(() -> {
            try {
                BuildIdentity build = BuildIdentity.load();
                currentVersion = build.version;
                ScienceNotLeisure.LOG.info(
                    "GTNL update check: channel={}, repository={}, branch={}, installed={}",
                    build.channel,
                    build.repository,
                    build.branch,
                    build.version);
                Result found;
                if ("stable".equals(build.channel)) {
                    found = stable(build);
                } else if ("development".equals(build.channel)) {
                    found = development(build);
                } else {
                    ScienceNotLeisure.LOG
                        .info("GTNL update check skipped: local build has no published update channel");
                    found = new Result(null, null, false, null);
                }
                if (found.hasUpdate()) {
                    ScienceNotLeisure.LOG
                        .info("GTNL update found: {} -> {}; {}", build.version, found.version, found.downloadUrl());
                } else {
                    ScienceNotLeisure.LOG.info(
                        "GTNL update check completed: {}",
                        found.failure == null ? "no newer published build in this channel" : found.failure);
                }
                result.complete(found);
            } catch (Exception exception) {
                String failure = exception.getClass()
                    .getSimpleName() + ": "
                    + exception.getMessage();
                ScienceNotLeisure.LOG.info("GTNL update check unavailable (no retry this launch): {}", failure);
                result.complete(new Result(null, null, false, failure));
            }
        }, "GTNL update check");
        worker.setDaemon(true);
        worker.start();
        return result;
    }

    private static Result stable(BuildIdentity build) throws Exception {
        JsonArray releases = api(build, "/releases?per_page=100").getAsJsonArray();
        String latest = null;
        String link = null;
        for (JsonElement entry : releases) {
            JsonObject release = entry.getAsJsonObject();
            if (release.get("draft")
                .getAsBoolean()
                || release.get("prerelease")
                    .getAsBoolean())
                continue;
            String tag = release.get("tag_name")
                .getAsString();
            if (!tag.matches("[vV]?[0-9]+(\\.[0-9]+)*(?:-hotfix[0-9]+)?")) continue;
            String normalized = tag.replaceFirst("^[vV]", "");
            if (!VersionParser.parseRange(build.releaseRange)
                .containsVersion(new DefaultArtifactVersion(normalized))) continue;
            if (compare(normalized, build.version.replaceFirst("^[vV]", "")) <= 0) continue;
            String asset = installAsset(
                release.getAsJsonArray("assets"),
                "sciencenotleisure-" + tag.replaceFirst("^[vV]", "") + ".jar");
            if (asset == null)
                asset = installAsset(release.getAsJsonArray("assets"), "sciencenotleisure-" + tag + ".jar");
            if (asset != null
                && (latest == null || compare(tag.replaceFirst("^[vV]", ""), latest.replaceFirst("^[vV]", "")) > 0)) {
                latest = tag;
                requireDownloadUrl(build, asset);
                link = asset;
            }
        }
        return new Result(latest, link, latest != null, null);
    }

    private static Result development(BuildIdentity build) throws Exception {
        if (build.branch.isEmpty()) throw new java.io.IOException("Development build has no branch");
        JsonObject release = api(build, "/releases/tags/dev-build").getAsJsonObject();
        if (release.get("draft")
            .getAsBoolean()) return new Result(null, null, false, null);
        JsonArray assets = release.getAsJsonArray("assets");
        String key = branchKey(build.branch);
        String manifestUrl = installAsset(assets, "gtnl-update-" + key + ".json");
        String candidate = null;
        String display = null;
        String link = null;
        if (manifestUrl != null) {
            requireDownloadUrl(build, manifestUrl);
            JsonObject manifest = readJson(manifestUrl).getAsJsonObject();
            if (manifest.get("schema")
                .getAsInt() != 1
                || !"development".equals(
                    manifest.get("channel")
                        .getAsString())
                || !build.repository.equals(
                    manifest.get("repository")
                        .getAsString())
                || !build.branch.equals(
                    manifest.get("branch")
                        .getAsString())) {
                throw new java.io.IOException("Development manifest identity mismatch");
            }
            candidate = manifest.get("commit")
                .getAsString();
            if (!candidate.matches("[a-fA-F0-9]{40}")) throw new java.io.IOException("Invalid manifest commit");
            display = manifest.get("version")
                .getAsString();
            link = installAsset(
                assets,
                manifest.get("artifact")
                    .getAsString());
        } else {
            // Older published builds already encode the exact branch hash and source commit.
            String branchName = build.branch.replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
            Pattern name = Pattern.compile(
                "sciencenotleisure-" + Pattern.quote(branchName + "-" + key + "-") + "([a-fA-F0-9]{8,40})\\.jar");
            for (JsonElement entry : assets) {
                JsonObject asset = entry.getAsJsonObject();
                Matcher match = name.matcher(
                    asset.get("name")
                        .getAsString());
                if (!match.matches()) continue;
                if (candidate != null) throw new java.io.IOException("Ambiguous legacy branch assets");
                candidate = match.group(1);
                link = asset.get("browser_download_url")
                    .getAsString();
            }
            if (candidate != null) {
                candidate = api(build, "/commits/" + candidate).getAsJsonObject()
                    .get("sha")
                    .getAsString();
                display = build.branch + " (" + candidate.substring(0, 8) + ")";
            }
        }
        if (candidate == null || link == null)
            return new Result(null, null, false, "No downloadable build for branch " + build.branch);
        requireDownloadUrl(build, link);
        if (candidate.equalsIgnoreCase(build.commit)) return new Result(null, null, false, null);
        JsonObject comparison = api(build, "/compare/" + build.commit + "..." + candidate).getAsJsonObject();
        boolean newer = "ahead".equals(
            comparison.get("status")
                .getAsString())
            && comparison.get("ahead_by")
                .getAsInt() > 0;
        return new Result(
            display,
            link,
            newer,
            newer ? null : "Published branch build is not ahead of installed commit");
    }

    private static void requireDownloadUrl(BuildIdentity build, String url) throws Exception {
        if (!url.startsWith("https://github.com/" + build.repository + "/releases/download/")) {
            throw new java.io.IOException("Unexpected release download URL");
        }
    }

    private static String installAsset(JsonArray assets, String name) {
        for (JsonElement entry : assets) {
            JsonObject asset = entry.getAsJsonObject();
            if (name.equals(
                asset.get("name")
                    .getAsString())
                && "uploaded".equals(
                    asset.get("state")
                        .getAsString())) {
                return asset.get("browser_download_url")
                    .getAsString();
            }
        }
        return null;
    }

    private static String branchKey(String branch) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
            .digest(branch.getBytes(StandardCharsets.UTF_8));
        StringBuilder key = new StringBuilder();
        for (int index = 0; index < 6; index++) key.append(String.format("%02x", hash[index] & 255));
        return key.toString();
    }

    private static JsonElement api(BuildIdentity build, String path) throws Exception {
        return readJson("https://api.github.com/repos/" + build.repository + path);
    }

    private static JsonElement readJson(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "GTNotLeisure-update-check");
            int status = connection.getResponseCode();
            if (status != 200) {
                String detail = "HTTP " + status;
                if ("0".equals(connection.getHeaderField("X-RateLimit-Remaining"))) {
                    String reset = connection.getHeaderField("X-RateLimit-Reset");
                    detail += "; GitHub API rate limit exhausted; reset epoch=" + reset;
                }
                throw new java.io.IOException(detail + " at " + address);
            }
            try (
                InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    if (text.length() + count > 4 * 1024 * 1024)
                        throw new java.io.IOException("Update response too large");
                    text.append(buffer, 0, count);
                }
                return new JsonParser().parse(text.toString());
            }
        } finally {
            connection.disconnect();
        }
    }

    private static int compare(String left, String right) {
        return new ComparableVersion(left).compareTo(new ComparableVersion(right));
    }

    public static final class Result {

        public final String version;
        public final String failure;
        private final String url;
        private final boolean newer;

        private Result(String version, String url, boolean newer, String failure) {
            this.version = version;
            this.url = url;
            this.newer = newer;
            this.failure = failure;
        }

        public boolean hasUpdate() {
            return newer;
        }

        public String downloadUrl() {
            return url;
        }
    }
}
