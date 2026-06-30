package org.vehiclescheduler;

import org.vehiclescheduler.model.VehicleTask;

import java.util.ArrayList;
import java.util.List;

public final class KnapsackSolver {

    public static final class Result {
        public final List<VehicleTask> selected;
        public final int totalDuration;
        public final int totalImpact;

        Result(List<VehicleTask> selected, int totalDuration, int totalImpact) {
            this.selected = selected;
            this.totalDuration = totalDuration;
            this.totalImpact = totalImpact;
        }
    }

    private KnapsackSolver() {}

    public static Result solve(List<VehicleTask> tasks, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("capacity (mechanic hours) cannot be negative");
        }
        int n = tasks.size();
        // dp[i][c] = best total impact achievable using the first i tasks with budget c
        int[][] dp = new int[n + 1][capacity + 1];

        for (int i = 1; i <= n; i++) {
            VehicleTask t = tasks.get(i - 1);
            int duration = t.getDuration();
            int impact = t.getImpact();
            for (int c = 0; c <= capacity; c++) {
                dp[i][c] = dp[i - 1][c];
                if (duration <= c) {
                    int withTask = dp[i - 1][c - duration] + impact;
                    if (withTask > dp[i][c]) {
                        dp[i][c] = withTask;
                    }
                }
            }
        }

        List<VehicleTask> selected = new ArrayList<>();
        int c = capacity;
        for (int i = n; i > 0; i--) {
            if (dp[i][c] != dp[i - 1][c]) {
                VehicleTask t = tasks.get(i - 1);
                selected.add(t);
                c -= t.getDuration();
            }
        }

        int totalDuration = selected.stream().mapToInt(VehicleTask::getDuration).sum();
        int totalImpact = dp[n][capacity];

        return new Result(selected, totalDuration, totalImpact);
    }
}
