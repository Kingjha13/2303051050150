package org.notifications.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;

public class Notification {
    @JsonProperty("ID")
    private String id;

    @JsonProperty("Type")
    private String type;

    @JsonProperty("Message")
    private String message;

    @JsonProperty("Timestamp")
    private String timestamp;

    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String getId() { return id; }
    public String getType() { return type; }
    public String getMessage() { return message; }
    public String getTimestamp() { return timestamp; }

    public int typeWeight() {
        if (type == null) return 0;
        return switch (type) {
            case "Placement" -> 3;
            case "Result" -> 2;
            case "Event" -> 1;
            default -> 0;
        };
    }

    public long timestampEpochSeconds() {
        try {
            return java.time.LocalDateTime.parse(timestamp, TS_FORMAT)
                    .atZone(java.time.ZoneOffset.UTC)
                    .toInstant()
                    .getEpochSecond();
        } catch (DateTimeParseException | NullPointerException e) {
            return 0L;
        }
    }

    public static final Comparator<Notification> ASCENDING_PRIORITY =
            Comparator.comparingInt(Notification::typeWeight)
                      .thenComparingLong(Notification::timestampEpochSeconds);

    @Override
    public String toString() {
        return "[" + type + "] " + message + " (" + timestamp + ", id=" + id + ")";
    }
}
