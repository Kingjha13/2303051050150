package org.vehiclescheduler.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Depot {
    @JsonProperty("ID")
    private int id;

    @JsonProperty("MechanicHours")
    private int mechanicHours;

    public int getId() { return id; }
    public int getMechanicHours() { return mechanicHours; }

    @Override
    public String toString() {
        return "Depot{id=" + id + ", mechanicHours=" + mechanicHours + "}";
    }
}
