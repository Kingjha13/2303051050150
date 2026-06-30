# Vehicle Maintenance Scheduler Microservice

For each depot, selects the subset of vehicle maintenance tasks that
maximises total operational impact score without exceeding that depot's
daily mechanic-hour budget. This is the classic 0/1 knapsack problem:

- **capacity** = depot's `MechanicHours`
- **weight** of each item = task's `Duration`
- **value** of each item = task's `Impact`

Solved with a bottom-up dynamic programming table (`KnapsackSolver.java`),
O(n × capacity) time, no external algorithm libraries.

Depot and vehicle-task data are fetched live from the evaluation server's
protected APIs (never hard-coded or stored in a database):

- `GET /evaluation-service/depots`
- `GET /evaluation-service/vehicles`

Every significant step (auth, fetch, per-depot solve result) is logged via
the shared `logging-middleware` package — no `System.out`/console loggers are
used for diagnostics, only for the final JSON report.

## Setup & run

1. Make sure you've registered with the test server and have
   `config.properties` set up at the repo root (see root `README.md` /
   `config.properties.example`).
2. Build the logging middleware first (only needed once):
   ```
   mvn -q -pl logging-middleware install
   ```
3. Run:
   ```
   mvn -q -pl vehicle_scheduling_be compile exec:java -Dexec.mainClass=org.vehiclescheduler.Main
   ```
   or build a runnable jar:
   ```
   mvn -q -pl vehicle_scheduling_be package
   java -jar vehicle_scheduling_be/target/vehicle-scheduling-be-1.0.0.jar
   ```

## Sample output

```json
[
  {
    "depotId": 1,
    "mechanicHoursBudget": 60,
    "mechanicHoursUsed": 59,
    "totalImpactScore": 47,
    "selectedTasks": [
      { "taskId": "73ce9dca-...", "duration": 6, "impact": 2 },
      { "taskId": "4b6e22ee-...", "duration": 1, "impact": 3 }
    ]
  }
]
```
