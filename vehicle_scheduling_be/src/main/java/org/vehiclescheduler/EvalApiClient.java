package org.vehiclescheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.evallogger.AuthTokenManager;
import org.evallogger.LogClient;
import org.vehiclescheduler.model.DepotsResponse;
import org.vehiclescheduler.model.VehiclesResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class EvalApiClient {
    private static final String DEPOTS_URL = "http://4.224.186.213/evaluation-service/depots";
    private static final String VEHICLES_URL = "http://4.224.186.213/evaluation-service/vehicles";

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final AuthTokenManager tokenManager;
    private final LogClient log;

    public EvalApiClient(AuthTokenManager tokenManager, LogClient log) {
        this.tokenManager = tokenManager;
        this.log = log;
    }

    public DepotsResponse fetchDepots() throws Exception {
        log.Log("backend", "info", "service", "Fetching depots from evaluation server");
        String body = getAuthenticated(DEPOTS_URL);
        DepotsResponse resp = mapper.readValue(body, DepotsResponse.class);
        log.Log("backend", "info", "service", "Fetched " + resp.depots.size() + " depots");
        return resp;
    }

    public VehiclesResponse fetchVehicles() throws Exception {
        log.Log("backend", "info", "service", "Fetching vehicle maintenance tasks from evaluation server");
        String body = getAuthenticated(VEHICLES_URL);
        VehiclesResponse resp = mapper.readValue(body, VehiclesResponse.class);
        log.Log("backend", "info", "service", "Fetched " + resp.vehicles.size() + " vehicle tasks");
        return resp;
    }

    private String getAuthenticated(String url) throws Exception {
        String token = tokenManager.getToken();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) {
            log.Log("backend", "error", "service", "GET " + url + " failed with status " + res.statusCode());
            throw new RuntimeException("Request to " + url + " failed: HTTP " + res.statusCode() + " - " + res.body());
        }
        return res.body();
    }
}
