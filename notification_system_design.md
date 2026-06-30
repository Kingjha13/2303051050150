# Notification System Design

## Stage 1 — REST API Design & Contract

Core actions the notification platform needs to support for a logged-in student:

| Action | Method | Endpoint |
|---|---|---|
| List notifications (paginated, optionally filtered) | GET | `/api/v1/notifications` |
| Get a single notification | GET | `/api/v1/notifications/{notificationId}` |
| Mark one notification as read | PATCH | `/api/v1/notifications/{notificationId}/read` |
| Mark all notifications as read | PATCH | `/api/v1/notifications/read-all` |
| Get unread count (for a badge icon) | GET | `/api/v1/notifications/unread-count` |
| Real-time stream of new notifications | GET (SSE) | `/api/v1/notifications/stream` |

Naming conventions: plural nouns for collections, lower-kebab-case path
segments, no verbs in the path (the HTTP method is the verb), versioned
under `/api/v1`.

### Headers

All endpoints require:
```
Authorization: Bearer <jwt>
Accept: application/json
```

### GET /api/v1/notifications

Query params: `cursor` (opaque pagination cursor), `limit` (default 20, max
100), `type` (optional filter: `placement` | `result` | `event`), `unreadOnly`
(boolean).

Response `200`:
```json
{
  "items": [
    {
      "id": "8a7f4f5b-335c-4a2f-96d8-09c4a362e781",
      "type": "placement",
      "message": "Advanced Micro Devices Inc. hiring",
      "isRead": false,
      "createdAt": "2026-04-22T17:51:18Z"
    }
  ],
  "nextCursor": "eyJjcmVhdGVkQXQiOi4uLn0=",
  "unreadCount": 12
}
```

### PATCH /api/v1/notifications/{notificationId}/read

Response `204 No Content`.

### GET /api/v1/notifications/unread-count

Response `200`: `{ "unreadCount": 12 }`

### Real-time mechanism

Server-Sent Events (SSE) on `GET /api/v1/notifications/stream`
(`Accept: text/event-stream`). SSE is chosen over WebSockets because the
traffic is one-directional (server → client), it rides plain HTTP/1.1+
(simpler to load-balance and works through most corporate proxies), and
browsers auto-reconnect on drop. Each event:
```
event: notification
data: {"id":"...", "type":"placement", "message":"...", "createdAt":"..."}
```
A lightweight `unread-count` event is also pushed on the same stream so the
badge can update without a separate poll.

---

## Stage 2 — Persistence

**Choice: PostgreSQL** (relational). Notifications are structured,
high-write, read-heavy-by-recency records with a small fixed set of fields
and a natural foreign key to `students` — a relational model with proper
indexing fits this better than a schemaless document store, and we get
transactional guarantees for "insert notification + update unread counter"
type operations.

### Schema

```sql
CREATE TYPE notification_type AS ENUM ('placement', 'result', 'event');

CREATE TABLE students (
    id          BIGSERIAL PRIMARY KEY,
    roll_no     VARCHAR(32) UNIQUE NOT NULL,
    email       VARCHAR(255) UNIQUE NOT NULL
);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      BIGINT NOT NULL REFERENCES students(id),
    notification_type notification_type NOT NULL,
    message         TEXT NOT NULL,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Primary access pattern: "give me a student's unread notifications, newest first"
CREATE INDEX idx_notifications_student_unread_recent
    ON notifications (student_id, is_read, created_at DESC);

-- Secondary access pattern: "students notified of a given type in a date range"
CREATE INDEX idx_notifications_type_created_at
    ON notifications (notification_type, created_at DESC);
```

### Scale problems & mitigations

- **Hot table growth**: at 5M+ rows the table keeps growing unbounded.
  Mitigate with monthly range partitioning on `created_at`
  (`PARTITION BY RANGE`), so old partitions can be archived/dropped and
  queries that filter by recent time only scan recent partitions.
- **Index bloat / write amplification**: every insert updates two indexes.
  Acceptable here since reads (badge checks, inbox loads) vastly outnumber
  writes, but worth monitoring `pg_stat_user_indexes` as volume grows.
- **Read replicas**: notification reads are eventually-consistent-tolerant
  (a few seconds of staleness on a notification feed is fine), so route GET
  traffic to read replicas and keep writes on the primary.

### Example queries

```sql
-- Unread notifications for a student, most recent first
SELECT id, notification_type, message, created_at
FROM notifications
WHERE student_id = $1 AND is_read = FALSE
ORDER BY created_at DESC
LIMIT 20;

-- Mark all as read
UPDATE notifications
SET is_read = TRUE
WHERE student_id = $1 AND is_read = FALSE;
```

---

## Stage 3 — Query Performance

```sql
SELECT * FROM notifications
WHERE studentID = 1042 AND isRead = false
ORDER BY createdAt DESC;
```

**Is it accurate?** Functionally yes — it returns the right rows. **Why is
it slow?** At 5,000,000 rows: (1) `SELECT *` pulls every column including
the (potentially large) `message` text even though most consumers just need
a handful of fields; (2) without a composite index on
`(studentID, isRead, createdAt)`, Postgres falls back to a sequential scan
(or scans an index on `studentID` alone and then sorts the matched rows by
`createdAt` in memory/on disk) — both are O(n) or worse against a
multi-million-row table; (3) there's no `LIMIT`, so even a "give me the
recent ones" query is forced to materialize and sort the student's *entire*
notification history, which only gets worse as that student accumulates
more notifications over time.

**Is "add indexes on every column" effective?** No. Indexing every column
individually doesn't help this query — Postgres can only efficiently use
one index per table per query in most plans (or it has to intersect several
indexes, which is usually worse than a single well-chosen composite index).
It also actively hurts: every extra index slows down every `INSERT`/`UPDATE`
(each write has to update every index) and bloats storage. The right fix is
the *one* composite index that matches this query's filter + sort pattern,
already defined in Stage 2:
```sql
CREATE INDEX idx_notifications_student_unread_recent
    ON notifications (student_id, is_read, created_at DESC);
```
Combined with selecting only needed columns and adding a `LIMIT`:
```sql
SELECT id, notification_type, message, created_at
FROM notifications
WHERE student_id = 1042 AND is_read = FALSE
ORDER BY created_at DESC
LIMIT 50;
```

### Students notified of a placement in the last 7 days

```sql
SELECT DISTINCT student_id
FROM notifications
WHERE notification_type = 'placement'
  AND created_at >= now() - INTERVAL '7 days';
```
This uses the `idx_notifications_type_created_at` index from Stage 2 to
range-scan only the last week's placement rows instead of touching the
whole table.

---

## Stage 4 — Reducing DB Load From Polling On Every Page Load

Fetching from the DB on every single page load doesn't scale once traffic
grows — most of those reads are for data that hasn't changed since the last
load a few seconds ago.

**Approach: cache the unread feed + count, push updates instead of
re-polling the DB.**

1. **Cache layer (Redis)**: cache each student's unread notification list
   and unread count keyed by `student_id`, with a short TTL (e.g. 30–60s) as
   a safety net, but more importantly **invalidate/update the cache
   explicitly** whenever a new notification is written or one is marked
   read, so the cache is correct, not just eventually-fresh.
2. **Push over poll**: replace "fetch on every page load" with the SSE
   stream from Stage 1 — the client gets new notifications pushed to it and
   only needs to hit the REST endpoint once on initial page load (which can
   now be served from cache) plus pagination for older history.
3. **Read replicas** for the cache-miss / pagination path, so heavy reads
   never compete with write traffic on the primary.
4. **CDN/edge caching** is not applicable here since the data is
   per-user/private, but client-side caching (don't re-fetch on every
   navigation within the same session, use the SSE stream to keep local
   state fresh) further cuts redundant calls.

**Trade-offs**: Redis adds an operational component and a small risk of
stale data if invalidation is ever missed (mitigated by the TTL safety
net); SSE requires holding open connections per active client, which is a
manageable resource cost compared to a DB hit per page load, and is
significantly cheaper than WebSockets' bidirectional connection overhead
for what is a one-directional feed.

---

## Stage 5 — Critique & Redesign of `notify_all`

```
function notify_all(student_ids: array, message: string):
    for student_id in student_ids:
        send_email(student_id, message)   # calls Email API
        save_to_db(student_id, message)   # DB insert
        push_to_app(student_id, message)  # implementation is based on whatever
                                           # real time notification mechanism you have chosen in Stage 1
```

### Shortcomings

- **No fault isolation**: it's a single synchronous loop. If `send_email`
  fails for student #200 (as in the scenario), the loop's behavior on
  failure is undefined here — at best it aborts the remaining 49,800
  students, at worst the whole batch silently stops with no record of who
  was actually notified.
- **No retry / no idempotency**: a transient email-provider blip has no
  retry path, and there's no way to safely re-run the job without
  potentially double-emailing/double-inserting students who already
  succeeded.
- **Synchronous and serial**: looping over 50,000 students one at a time,
  each doing a network call (email), a DB write, and a push call, is far
  too slow to be a reasonable HTTP-handler-driven operation, and ties up a
  request thread for the entire duration.
- **Tightly coupled steps**: email, DB, and push are bundled into one
  unit per student instead of being independent, retryable units of work.
- **No observability**: nothing here logs which students succeeded/failed,
  making "logs indicate the call failed for 200 students" hard to act on
  ("which 200? have they been retried?").

### Redesign

Don't process this inline — enqueue and process asynchronously, with
per-step idempotency and retries, decoupled from any single API call's
reliability:

```
function notify_all(student_ids: array, message: string):
    batch_id = create_notification_batch(message)   # 1 DB insert: the batch metadata
    bulk_insert_notifications(student_ids, batch_id) # 1 bulk DB insert, NOT N inserts
    enqueue_job("dispatch_notifications", batch_id)  # hand off to a queue, return immediately

# Run by background workers, many in parallel, each handling one student:
function dispatch_notification_worker(batch_id, student_id):
    if already_delivered(batch_id, student_id):      # idempotency check
        return
    try:
        send_email(student_id, message)              # has its own internal retry/backoff
        push_to_app(student_id, message)
        mark_delivered(batch_id, student_id)
    except TransientError:
        requeue_with_backoff(batch_id, student_id)    # retried later, doesn't block others
    except PermanentError as e:
        mark_failed(batch_id, student_id, e)          # logged, surfaced to a dashboard, not silently dropped
```

**Should saving to DB and sending the email happen together (atomically)?**
No — and the original pseudocode's order (email → DB → push) makes it worse,
since a successful email with a failed DB write means the notification
exists nowhere to be recovered or shown in-app, and a failed email after a
successful DB write needs to retry only the email, not redo the insert.
The fix is what's shown above: persist first (the row marked
`pending`/`delivered`/`failed`), then attempt delivery and update status —
each side effect is independently retryable and the DB row is always the
source of truth for what still needs to happen, rather than a true
distributed transaction across an email provider, a database, and a push
mechanism (which isn't realistically achievable). Throughput is restored by
having many workers process the queue in parallel instead of one serial
loop, and by bulk-inserting notification rows instead of one DB round trip
per student.

---

## Stage 6 — Priority Inbox (Top-N Unread Notifications)

Implemented in `notification-app-be/` (`PriorityInbox.java`, `Main.java`).

**Requirement**: always be able to show the top *n* most important unread
notifications (n configurable, e.g. 10/15/20), where priority is a
combination of type weight (`placement > result > event`) and recency, and
new notifications keep arriving continuously — re-sorting the entire
notification set on every arrival would not scale.

**Approach — bounded min-heap of size n:**

- Keep a `PriorityQueue<Notification>` of fixed capacity `n`, ordered
  ascending by priority (`Notification.ASCENDING_PRIORITY`: type weight
  first, then timestamp), so `heap.peek()` is always the *weakest* member
  currently in the top-n.
- When a new notification arrives:
  - If the heap has fewer than `n` items, just add it — `O(log n)`.
  - Otherwise, compare it against `heap.peek()`. If it outranks the current
    weakest top-n member, evict that member and insert the new one —
    `O(log n)`. If it doesn't outrank the weakest member, it's discarded
    from the top-n entirely (still exists in the full notification store,
    just not "important enough" to be in the priority view right now) in
    `O(1)`.
- To display the ranked list, drain the heap into a list and sort
  descending — `O(n log n)`, but `n` is small and fixed (e.g. 10), so this
  is cheap regardless of how many total notifications have streamed
  through.

This means maintaining the top-n is `O(log n)` per incoming notification —
independent of the total notification volume — instead of re-sorting an
ever-growing list on every arrival, which is what makes it viable to keep
this "always up to date" as new notifications keep coming in.

Priority ordering itself: `typeWeight()` maps
`placement=3 > result=2 > event=1`, used as the primary sort key; recency
(parsed notification timestamp, newest first) is the tiebreaker.

Code only — no DB query was used to compute the top-n, per the
requirement; notification data is fetched live from the evaluation
server's `/evaluation-service/notifications` API as required (see
`EvalApiClient.java`).

(Output screenshots showing the computed top-10 list are included alongside
this file in the GitHub repository.)
