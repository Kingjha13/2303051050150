# Logging Middleware

A reusable Java logging package that sends every log event to the evaluation
server's Log API instead of using `System.out`/`System.err` or a built-in
logging framework.

## Usage

```java
EvalConfig config = new EvalConfig(); // reads config.properties (see repo root)
AuthTokenManager tokenManager = AuthTokenManager.fromConfig(config);
LogClient log = new LogClient(tokenManager);

log.Log("backend", "error", "handler", "received string, expected bool");
log.Log("backend", "fatal", "db", "Critical database connection failure.");
```

`Log(stack, level, package, message)`:
- `stack`: `backend` | `frontend`
- `level`: `debug` | `info` | `warn` | `error` | `fatal`
- `package`: one of the backend-only / frontend-only / shared package names
  defined in the evaluation spec (see `LogClient.java`)

The token is fetched once, cached, and transparently refreshed before expiry.

## Setup

1. Copy `../config.properties.example` to `../config.properties` (repo root) and
   fill in your own registered `email`, `name`, `rollNo`, `accessCode`,
   `clientID`, and `clientSecret`. This file is gitignored — never commit it.
2. Build: `mvn -q -pl logging-middleware install` (installs the jar to your
   local Maven repo so other modules in this project can depend on it).
3. Smoke test: `mvn -q -pl logging-middleware exec:java -Dexec.mainClass=org.evallogger.TestRun`
   (or just run `TestRun.main` from your IDE).
