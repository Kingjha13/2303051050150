package org.evallogger;

public class TestRun {
    public static void main(String[] args) throws Exception {
        EvalConfig config = new EvalConfig();
        AuthTokenManager tokenManager = AuthTokenManager.fromConfig(config);
        LogClient logClient = new LogClient(tokenManager);
        logClient.Log("backend", "info", "service", "Verifying logging middleware setup");
        System.out.println("Done - check the evaluation server dashboard / response codes above for errors.");
    }
}