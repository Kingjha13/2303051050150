package org.notifications;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evallogger.AuthTokenManager;
import org.evallogger.LogClient;
import org.notifications.model.NotificationsResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class EvalApiClient {
    private static final String NOTIFICATIONS_URL = "http://4.224.186.213/evaluation-service/notifications";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AuthTokenManager tokenManager;
    private final LogClient log;

    public EvalApiClient(AuthTokenManager tokenManager, LogClient log) {
        this.tokenManager = tokenManager;
        this.log = log;
    }

    public NotificationsResponse fetchNotifications() throws Exception {
        log.Log("backend", "info", "service", "Fetching notifications from evaluation server");
        String token = tokenManager.getToken();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(NOTIFICATIONS_URL))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.Log("backend", "error", "service", "GET notifications failed with status " + res.statusCode());
            throw new RuntimeException("Request failed: HTTP " + res.statusCode() + " - " + res.body());
        }
        NotificationsResponse resp = mapper.readValue(res.body(), NotificationsResponse.class);
        log.Log("backend", "info", "service", "Fetched " + resp.notifications.size() + " notifications");
        return resp;
    }
}
