package org.evallogger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class EvalConfig {

    private final Properties props = new Properties();

    public EvalConfig() throws IOException {
        this(resolvePath());
    }

    public EvalConfig(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException(
                    "Could not find " + path.toAbsolutePath() +
                            ". Copy config.properties.example to config.properties (repo root or module root) " +
                            "and fill in your registered credentials, or set EVAL_CONFIG_PATH."
            );
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        }
    }

    private static Path resolvePath() {
        String envPath = System.getenv("EVAL_CONFIG_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return Path.of(envPath);
        }
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            Path candidate = dir.resolve("config.properties");
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path parent = dir.getParent();
            if (parent == null) break;
            dir = parent;
        }
        return Path.of("config.properties");
    }

    public String email()        { return require("email"); }
    public String name()         { return require("name"); }
    public String rollNo()       { return require("rollNo"); }
    public String accessCode()   { return require("accessCode"); }
    public String clientID()     { return require("clientID"); }
    public String clientSecret() { return require("clientSecret"); }

    private String require(String key) {
        String v = props.getProperty(key);
        if (v == null || v.isBlank()) {
            throw new IllegalStateException("Missing required config key: " + key);
        }
        return v;
    }
}