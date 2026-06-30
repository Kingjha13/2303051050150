# Notification App Backend — Stage 6

Code for the design doc's Stage 6 task: a Priority Inbox that always
surfaces the top-N most important unread notifications (priority =
type weight `placement > result > event`, tiebroken by recency), updated
efficiently as new notifications stream in (see `PriorityInbox.java`).

Notification data is fetched live from the evaluation server's protected
`GET /evaluation-service/notifications` API — not stored in a DB, not
hard-coded — via `EvalApiClient.java`, authenticated through the same
`logging-middleware` package used elsewhere in this repository, and every
significant step is logged through it as well.

Full design rationale (Stages 1–6) lives in `../notification_system_design.md`.

## Setup & run

1. `config.properties` at the repo root must be filled in (see root
   `README.md`).
2. Build the logging middleware once: `mvn -q -pl logging-middleware install`
3. Run, optionally passing the desired top-N (default 10):
   ```
   mvn -q -pl notification-app-be compile exec:java -Dexec.mainClass=org.notifications.Main
   mvn -q -pl notification-app-be compile exec:java -Dexec.mainClass=org.notifications.Main -Dexec.args="15"
   ```
   or build a runnable jar:
   ```
   mvn -q -pl notification-app-be package
   java -jar notification-app-be/target/notification-app-be-1.0.0.jar 10
   ```
