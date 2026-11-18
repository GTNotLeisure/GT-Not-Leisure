package com.science.gtnl.common.update;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.science.gtnl.Tags;

final class BuildIdentity {

    final String repository;
    final String branch;
    final String commit;
    final String channel;
    final String version;
    final String releaseRange;

    private BuildIdentity(Properties properties) {
        repository = properties.getProperty("repository", "");
        branch = properties.getProperty("branch", "");
        commit = properties.getProperty("commit", "");
        channel = properties.getProperty("channel", "local");
        version = properties.getProperty("version", Tags.VERSION);
        releaseRange = properties.getProperty("releaseRange", "");
    }

    static BuildIdentity load() throws Exception {
        Properties properties = new Properties();
        try (InputStream stream = BuildIdentity.class.getResourceAsStream("/gtnl-build.properties")) {
            if (stream == null) throw new IOException("Missing gtnl-build.properties");
            properties.load(stream);
        }
        BuildIdentity identity = new BuildIdentity(properties);
        if (!identity.repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IOException("Invalid build repository");
        }
        if (!"local".equals(identity.channel) && !identity.commit.matches("[a-fA-F0-9]{40}"))
            throw new IOException("Invalid build commit");
        return identity;
    }
}
