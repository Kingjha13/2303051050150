package org.evallogger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;


public class AuthTokenManager {
    private static final String AUTH_URL = "http://4.224.186.213/evaluation-service/auth";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private final String email, name, rollNo, accessCode, clientID, clientSecret;
    private String cachedToken;
    private Instant expiresAt = Instant.MIN;

    public AuthTokenManager(String email, String name, String rollNo,
                            String accessCode, String clientID, String clientSecret) {
        this.email = email; this.name = name; this.rollNo = rollNo;
        this.accessCode = accessCode; this.clientID = clientID; this.clientSecret = clientSecret;
    }

    public static AuthTokenManager fromConfig(EvalConfig cfg) {
        return new AuthTokenManager(
                cfg.email(), cfg.name(), cfg.rollNo(),
                cfg.accessCode(), cfg.clientID(), cfg.clientSecret()
        );
    }

    public synchronized String getToken() throws Exception {
        if (cachedToken != null && Instant.now().isBefore(expiresAt.minusSeconds(30))) {
            return cachedToken;
        }
        String body = mapper.writeValueAsString(java.util.Map.of(
                "email", email, "name", name, "rollNo", rollNo,
                "accessCode", accessCode, "clientID", clientID, "clientSecret", clientSecret
        ));
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(AUTH_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            throw new RuntimeException("Auth failed: HTTP " + res.statusCode() + " - " + res.body());
        }
        JsonNode json = mapper.readTree(res.body());
        cachedToken = json.get("access_token").asText();
        long expiresIn = json.get("expires_in").asLong();
        expiresAt = Instant.ofEpochSecond(expiresIn);
        return cachedToken;
    }
}