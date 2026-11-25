package com.science.gtnl.common.update;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import com.science.gtnl.Tags;

public class BuildIdentity {

    private static final Pattern REPOSITORY_NAME = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");
    private static final Pattern COMMIT_ID = Pattern.compile("[a-fA-F0-9]{40}");

    public final String repository;
    public final String branch;
    public final String commit;
    public final String channel;
    public final String version;
    public final String releaseRange;

    private BuildIdentity(Properties properties) {
        repository = properties.getProperty("repository", "");
        branch = properties.getProperty("branch", "");
        commit = properties.getProperty("commit", "");
        channel = properties.getProperty("channel", "local");
        version = properties.getProperty("version", Tags.VERSION);
        releaseRange = properties.getProperty("releaseRange", "");
    }

    public static BuildIdentity load() throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = BuildIdentity.class.getResourceAsStream("/gtnl-build.properties")) {
            if (stream == null) throw new IOException("Missing gtnl-build.properties");
            properties.load(stream);
        }
        BuildIdentity identity = new BuildIdentity(properties);
        if (!REPOSITORY_NAME.matcher(identity.repository)
            .matches()) {
            throw new IOException("Invalid build repository");
        }
        if (!"local".equals(identity.channel) && !COMMIT_ID.matcher(identity.commit)
            .matches()) throw new IOException("Invalid build commit");
        return identity;
    }
}
