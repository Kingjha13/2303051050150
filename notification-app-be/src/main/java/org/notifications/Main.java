package org.notifications;

import org.evallogger.AuthTokenManager;
import org.evallogger.EvalConfig;
import org.evallogger.LogClient;
import org.notifications.model.Notification;
import org.notifications.model.NotificationsResponse;

import java.util.List;


public class Main {
    public static void main(String[] args) throws Exception {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 10;

        EvalConfig config = new EvalConfig();
        AuthTokenManager tokenManager = AuthTokenManager.fromConfig(config);
        LogClient log = new LogClient(tokenManager);
        EvalApiClient api = new EvalApiClient(tokenManager, log);

        log.Log("backend", "info", "service", "Priority inbox run starting (top " + n + ")");

        NotificationsResponse resp = api.fetchNotifications();
        PriorityInbox inbox = new PriorityInbox(n);

        // Simulates a live stream: notifications arrive one at a time and the
        // inbox keeps itself up to date in O(log n) per arrival rather than
        // re-sorting everything fetched so far.
        for (Notification notification : resp.notifications) {
            inbox.offer(notification);
        }

        List<Notification> topN = inbox.getTopN();

        log.Log("backend", "info", "service",
                "Priority inbox computed: " + topN.size() + " of " + resp.notifications.size()
                        + " notifications retained as top " + n);

        System.out.println("Top " + n + " priority notifications:");
        int rank = 1;
        for (Notification notif : topN) {
            System.out.printf("%2d. %s%n", rank++, notif);
        }
    }
}
