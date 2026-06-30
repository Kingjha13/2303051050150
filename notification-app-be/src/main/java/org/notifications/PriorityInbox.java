package org.notifications;

import org.notifications.model.Notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

public class PriorityInbox {
    private final int capacity;
    private final PriorityQueue<Notification> minHeap;

    public PriorityInbox(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.minHeap = new PriorityQueue<>(capacity, Notification.ASCENDING_PRIORITY);
    }

    public void offer(Notification n) {
        if (minHeap.size() < capacity) {
            minHeap.offer(n);
            return;
        }
        Notification weakest = minHeap.peek();
        if (Notification.ASCENDING_PRIORITY.compare(n, weakest) > 0) {
            minHeap.poll();
            minHeap.offer(n);
        }
    }

    public List<Notification> getTopN() {
        List<Notification> result = new ArrayList<>(minHeap);
        result.sort(Notification.ASCENDING_PRIORITY.reversed());
        return Collections.unmodifiableList(result);
    }
}
