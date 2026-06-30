package org.evallogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.Set;


public class LogClient {
    private static final String LOG_URL = "http://4.224.186.213/evaluation-service/logs";
    private static final Set<String> STACKS = Set.of("backend", "frontend");
    private static final Set<String> LEVELS = Set.of("debug", "info", "warn", "error", "fatal");
    private static final Set<String> BACKEND_PKGS = Set.of(
            "cache", "controller", "cron_job", "db", "domain", "handler", "repository", "route", "service");
    private static final Set<String> FRONTEND_PKGS = Set.of("api", "component", "hook", "page", "state");
    private static final Set<String> SHARED_PKGS = Set.of("auth", "config", "middleware", "utils");

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AuthTokenManager tokenManager;

    public LogClient(AuthTokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    public void Log(String stack, String level, String pkg, String message) {
        try {
            validate(stack, level, pkg);
            String token = tokenManager.getToken();
            String body = mapper.writeValueAsString(java.util.Map.of(
                    "stack", stack, "level", level, "package", pkg, "message", message));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(LOG_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
        }
    }

    private void validate(String stack, String level, String pkg) {
        if (!STACKS.contains(stack)) throw new IllegalArgumentException("invalid stack: " + stack);
        if (!LEVELS.contains(level)) throw new IllegalArgumentException("invalid level: " + level);
        boolean validPkg = SHARED_PKGS.contains(pkg)
                || (stack.equals("backend") && BACKEND_PKGS.contains(pkg))
                || (stack.equals("frontend") && FRONTEND_PKGS.contains(pkg));
        if (!validPkg) throw new IllegalArgumentException("invalid package '" + pkg + "' for stack '" + stack + "'");
    }
}