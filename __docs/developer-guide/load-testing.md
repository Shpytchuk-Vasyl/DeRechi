# Load testing

Load tests for `Client-API`'s GraphQL endpoint, written for [k6](https://grafana.com/docs/k6/latest/)
and kept in [`load-tests/`](../../load-tests). They run against the local compose stack, push their
metrics into the same Prometheus that scrapes the services, so the latency a visitor sees and the
connection pool, JVM and RabbitMQ behind it are on one time axis in Grafana.

They are not part of `./mvnw test` and nothing runs them automatically.

## What runs

`load-tests/client-api.js` starts two scenarios side by side, each at its own arrival rate (an
open model: new visitors keep arriving even when the server is slow, the way real traffic does).

| Scenario | One iteration | Share |
|---|---|---|
| `browse` | 30%: `Categories` + `Countries` (the landing page). Then `LostItems` or `FoundItems` with a random filter; a third page on, up to three pages; half open one notice (`LostItem` / `FoundItem`); 10% look up a place, three keystrokes of `Places` | `RATE` visits per second |
| `publish` | `CreateLostItem` or `CreateFoundItem`; half of them get a response (`ClaimLostItem` / `ClaimFoundItem`, which sends emails through `Worker` and `Notification`); half of those then poll `LostItemClaim` / `FoundItemClaim` | `RATE x WRITE_SHARE` per second |

The filters on list queries, each reported under its own `filter` tag:

| `filter` | Share | Sends |
|---|---|---|
| `none` | 35% | no filter |
| `search` | 20% | a word from the seed vocabulary, 15% of the time one that matches nothing |
| `category` | 15% | `categoryId` |
| `near` | 15% | one of 12 cities, radius 5 to 100 km |
| `dates` | 10% | the last 7 or 14 days |
| `combined` | 5% | search + category + near |

The operations and their variables are copied from `Web-Client/src/graphql/documents.ts`, under
the same operation names. When the frontend's documents change, update
[`load-tests/lib/queries.js`](../../load-tests/lib/queries.js).

**Left out on purpose:**

- `unlockLostItemClaim` / `unlockFoundItemClaim`: every call creates a real product in the
  Fourthwall shop. Load-testing them needs `derechi.fourthwall.api-url` pointed at a stub first.
- `confirmReturn`: the token only arrives by email.
- The Fourthwall webhook: it needs a signed body, and it belongs to its own test.

## Profiles

`PROFILE` picks one:

| Profile | Shape | Use it for |
|---|---|---|
| `smoke` (default) | 1 visitor for 30 s, 3 writes | the script and the stack work at all |
| `load` | `RATE` visits/s for 10 min | the expected traffic, long enough for the pool and the JVM to settle |
| `stress` | ramps to `RATE`, then 2x, 4x, 8x over 12 min | where p95 bends and errors start |
| `soak` | `RATE` visits/s for 1 h | memory growth, a growing pool wait, a queue that never drains |

Thresholds (the run exits non-zero when one is crossed, the numbers are still printed):
`http_req_failed` and `graphql_errors` under 1%, `checks` over 99%, p95 under 300 ms for every
read operation and every `filter`, under 800 ms for writes.

`graphql_errors` matters more than `http_req_failed`: Spring for GraphQL answers HTTP 200 with an
`errors` array when an operation fails, so a run where every query failed validation would look
clean on HTTP status alone. Every failure is also counted by its `classification` in
`graphql_errors_by_class`, and each VU logs its first three failures with the message.

## Running

```bash
# 1. The stack, plus Prometheus and Grafana
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build

# 2. Data: 50 000 lost and 50 000 found notices on 10 000 places (about 5 s)
docker exec -i derechi-postgres psql -U derechi -d derechi -v ON_ERROR_STOP=1 -v items=50000 \
  < load-tests/seed/seed.sql

# 3. A run
docker compose -f docker-compose.yml -f docker-compose.services.yml run --rm \
  -e PROFILE=load -e RATE=20 -e TESTID=load-$(date +%H%M) k6

# 4. Afterwards, everything the seed and the runs created (about 25 s for 100 000 notices)
docker exec -i derechi-postgres psql -U derechi -d derechi -v ON_ERROR_STOP=1 \
  < load-tests/seed/cleanup.sql
```

Pass the same `-f` files the stack was started with. With `docker-compose.yml` alone, compose
warns about "orphan containers" for the services. The warning is harmless; **do not** follow its
advice to add `--remove-orphans`, that removes the service containers.

The `k6` service has the `load` profile, so `docker compose up` never starts it; `run` starts it
on demand. It has no `depends_on`, so a run never restarts anything else.

Without Docker, with k6 installed locally and the services on their usual ports:

```bash
k6 run -e PROFILE=smoke load-tests/client-api.js
```

### Options

All are environment variables, `-e NAME=value` on `docker compose run` or `k6 run`.

| Variable | Default | Meaning |
|---|---|---|
| `PROFILE` | `smoke` | `smoke`, `load`, `stress`, `soak` |
| `BASE_URL` | `http://host.docker.internal:8080` in compose, `http://localhost:8080` otherwise | Getaway. `http://host.docker.internal:8082` hits Client-API directly, which tells you how much the gateway adds |
| `RATE` | `20` | browse visits started per second at the `load` level. One visit is 1 to 6 requests, about 2.7 on average |
| `WRITE_SHARE` | `0.05` | publish iterations per browse visit |
| `DURATION` | per profile | overrides the length of `smoke`, `load` and `soak` |
| `THINK` | `1` | multiplier for the pauses between a visitor's requests; `0` removes them |
| `WRITES` | `true` | `false` runs `browse` only |
| `ALLOW_REMOTE_WRITES` | unset | writes are refused unless `BASE_URL` is a local host; `true` overrides that |
| `TESTID` | the profile name | tag on every metric; the Grafana dashboard selects a run by it |
| `K6_OUT` | `experimental-prometheus-rw` in compose | empty (`-e K6_OUT=`) when Prometheus is not running |

For a self-contained HTML report next to the console summary:
`-e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_EXPORT=results/report.html` (written to
`load-tests/results/`, which is git-ignored).

## Test data

Without data the numbers mean nothing: a list query over three notices is fast whatever the
indexes. `setup()` warns when the database holds fewer than 100 lost items.

[`seed/seed.sql`](../../load-tests/seed/seed.sql) inserts `items` lost and `items` found notices
(default 50 000 each) on `items / 5` places across 19 cities, mostly Ukrainian, with titles
built from the same vocabulary the searches use, dates over the last 60 days and half of them
with a reward. It refuses to run twice; clean up first.

The seeded rows go straight into the tables, so the Worker never sees an `ItemCreatedEvent` for
them and does not match them. The notices the `publish` scenario creates go through the API and
are matched like any other, which is part of what is being measured.

Everything the load tests create is marked, and [`seed/cleanup.sql`](../../load-tests/seed/cleanup.sql)
deletes exactly that: place ids start with `load-`, contact emails end with `@load.test` (a reserved
domain, so a misdirected run cannot email anybody real). It removes the claims, matches, archived
copies, contacts and places too, and leaves everything else alone. It is idempotent; if it stops
halfway, run it again.

Locally the emails land in Mailpit (`http://localhost:8025`), which keeps the last 500.

## Reading the results

The console summary breaks `http_req_duration` down by `op` (operation), `filter` and `kind`
(`read` / `write`). In Grafana (`http://localhost:3000`), import dashboard **19665** ("k6
Prometheus") and pick the run by `testid`; next to it, the JVM dashboard 4701 for `Client-API`
(see [observability](../architecture/observability.md)).

What to look at while a run is going, in Prometheus:

| Question | Query |
|---|---|
| Is the connection pool the queue? | `hikaricp_connections_pending{application="Client-API"}` |
| How long does a request hold a connection? | `rate(hikaricp_connections_usage_seconds_sum{application="Client-API"}[1m]) / rate(hikaricp_connections_usage_seconds_count{application="Client-API"}[1m])` |
| Did requests give up waiting for one? | `increase(hikaricp_connections_timeout_total{application="Client-API"}[5m])` |
| Is the Worker keeping up with new notices? | `rabbitmq_queue_messages_ready{queue="worker.items"}` |

Throughput is capped at roughly pool size / connection hold time: with Hikari's default of 10
connections and 0.5 s per checkout, that is 20 requests per second whatever the JVM does.

A latency number means something only next to the database size it was measured on and the
JVM settings. The compose override runs every service with `-Xmx256m` and SerialGC, which is a
laptop setting, not a production one.

Which queries the database is busy with, without changing its configuration: sample
`pg_stat_activity` during a run.

```bash
for i in $(seq 1 60); do
  docker exec derechi-postgres psql -U derechi -d derechi -tAc \
    "select left(query, 120) from pg_stat_activity where datname = 'derechi' and state = 'active' and pid <> pg_backend_pid()"
  sleep 0.3
done | sort | uniq -c | sort -rn | head
```

## Changing the tests

- A new GraphQL operation the frontend sends: add it to `lib/queries.js` and to the scenario in
  `client-api.js`, and add its name to `READ_OPERATIONS` or `WRITE_OPERATIONS` in `lib/config.js`
  so it gets a threshold and its own line in the summary.
- New words or cities: keep `lib/data.js` and `seed/seed.sql` in step, or searches stop finding
  seeded rows.
- A new table that references notices, contacts or places: add it to `seed/cleanup.sql` before
  the rows it references, or the cleanup fails on the foreign key.
