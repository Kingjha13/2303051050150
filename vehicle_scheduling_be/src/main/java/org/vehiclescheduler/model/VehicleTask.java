package org.vehiclescheduler.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class VehicleTask {
    @JsonProperty("TaskID")
    private String taskId;

    @JsonProperty("Duration")
    private int duration;

    @JsonProperty("Impact")
    private int impact;

    public String getTaskId() { return taskId; }
    public int getDuration() { return duration; }
    public int getImpact() { return impact; }

    @Override
    public String toString() {
        return "VehicleTask{taskId='" + taskId + "', duration=" + duration + ", impact=" + impact + "}";
    }
}
