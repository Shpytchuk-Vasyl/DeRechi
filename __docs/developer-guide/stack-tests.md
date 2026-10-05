# Stack tests

Tests that need the services running together, kept in [`tests/`](../../tests) outside the Maven
reactor, so `./mvnw test` does not run them:

| Folder | What | Tool |
|---|---|---|
| `tests/integration` | flows across services: Client-API → RabbitMQ → Worker → Notification → e-mail | JUnit 5, Awaitility, JDBC, the Mailpit API |
| `tests/load` | Client-API's GraphQL under load | k6 |

## One command

```bash
tests/run.sh                         # integration + load (profile `load`, 2 min, 10 visits/s)
tests/run.sh --profile smoke         # what CI runs
tests/run.sh --only integration      # or --only load
tests/run.sh --profile stress --rate 20 --items 100000 --keep
```

It builds and starts an isolated compose project, `derechi-tests`
([`tests/docker-compose.yml`](../../tests/docker-compose.yml)), runs the tests, writes the report and
removes the stack. Needs Docker only; nothing from the main stack is used or touched.

| Option | Default | Meaning |
|---|---|---|
| `--profile` | `load` | k6 profile: `smoke`, `load`, `stress`, `soak` |
| `--rate` | `10` | browse visits started per second at the `load` level |
| `--duration` | `2m` for `load`, the profile's own otherwise | k6 run length |
| `--items` | `50000` | lost and found notices seeded before the load test |
| `--only` | both | `integration` or `load` |
| `--keep` | off | leave the stack running to look at Grafana and Mailpit afterwards |

The exit code is non-zero when an integration test fails or a k6 threshold is crossed.

## The stack

| Runs | Does not run |
|---|---|
| PostgreSQL (in tmpfs), Liquibase migrations, RabbitMQ + `rabbitmq-init`, Client-API, Worker, Notification, Mailpit, Prometheus, Grafana | Keycloak, Admin-API, Getaway, MinIO, pgAdmin |

- No `container_name` and no shared host ports, so it runs next to the main `derechi` project.
  The only host ports are Grafana `13000`, Prometheus `19090` and Mailpit `18025`
  (`TESTS_GRAFANA_PORT`, `TESTS_PROMETHEUS_PORT`, `TESTS_MAILPIT_PORT`).
- Migrations run through `liquibase/liquibase:5.0.4` plus the PostgreSQL driver
  (`tests/infra/migrations`), on the same `DB-Postgres/changelog` as everywhere else; Liquibase 5
  images ship without JDBC drivers.
- Services are built from the root `Dockerfile`, so the tests always run against the working tree.
- Worker and Notification are part of it because the flows under test go through them.
- `TESTS_JAVA_OPTS` overrides the services' JVM flags (`-Xms128m -Xmx384m`).
- Maven's cache lives in the external volume `derechi-tests-m2`, which survives runs.

## Integration tests

Black-box: requests go to Client-API over HTTP, effects are read from PostgreSQL and from Mailpit.
Classes are `*IT`, as in [testing conventions](../conventions/testing.md), and skip themselves
outside the stack.

| Class | Checks |
|---|---|
| `ItemMatchingIT` | a new lost item is matched with a found one nearby, and the other way round (`similar_item`) |
| `ClaimNotificationIT` | a response to a lost and to a found item e-mails the author in Ukrainian with the responder's contacts; a repeated response is not e-mailed again |
| `ReturnIT` | `confirmReturn` moves the notice and its claim to history; an unknown token is `NOT_FOUND` |
| `PaymentWebhookIT` | a signed `ORDER_PLACED` webhook marks the claim paid and e-mails the author's phone to the responder; a wrong signature is 401 |

The payment test never calls Fourthwall: it writes the variant id that `unlock*` would have
created straight into the claim, then sends the webhook signed with the stack's secret.

Checked against a broken stack: with Worker stopped, the seven tests that depend on it fail after
their 30 s wait, and the two that do not (unknown token, wrong signature) pass.

A new test extends `StackIT` (GraphQL, JDBC, Mailpit and webhook helpers), creates its own data
with unique contacts and places, and waits with `await()` rather than `sleep`.

## Load tests

[`tests/load/client-api.js`](../../tests/load/client-api.js) runs two scenarios at their own
arrival rates: `browse` (lists with filters, paging, a notice, a place lookup) and `publish`
(create a notice, sometimes respond to it). The operations are copied from
`Web-Client/src/graphql/documents.ts`; `unlock*` is left out because each call creates a real
product on Fourthwall.

| Profile | Shape |
|---|---|
| `smoke` | 1 visitor for 30 s, 3 writes |
| `load` | `RATE` visits/s for `DURATION` |
| `stress` | ramps to `RATE`, then 2x, 4x, 8x over 12 min |
| `soak` | `RATE` visits/s for 1 h |

Thresholds: HTTP and GraphQL errors under 1% and checks over 99% for every profile; for `load`,
`stress` and `soak` also p95 under 300 ms per read operation and per filter, under 800 ms for
writes. `smoke` runs on a cold JVM and only answers "does it work", so it has no latency
thresholds; that keeps CI red for broken code rather than for slow queries. A failed GraphQL operation still answers HTTP 200, so
`graphql_errors` is the number to trust, not `http_req_failed`.

`tests/load/seed/seed.sql` fills the database before the run (`--items`). The load test can also
run against the main stack by hand; `seed/cleanup.sql` then removes everything it created (rows
marked `load-` / `@load.test`).

## The report

`tests/reports/<run>/` (git-ignored; `tests/reports/latest` points at the last run):

| File | Contents |
|---|---|
| `index.html` | overall result, every integration test with its time and failure message, load numbers and every threshold |
| `summary.md` | the same in short; CI puts it on the job page |
| `load/report.html` | k6's own report: charts over time, per-request breakdown |
| `load/summary.json`, `integration/TEST-*.xml` | raw k6 and surefire output |

With `--keep`, Grafana (`http://localhost:13000`, anonymous view, `admin` / `admin`) has the run's
`k6_*` series next to the JVM metrics of Client-API, Worker and Notification. Import dashboard
**19665** ("k6 Prometheus") and pick the run by `testid`, which is the run id.

## CI

[`.github/workflows/tests.yml`](../../.github/workflows/tests.yml) runs the same command on pull
requests to `main` (`--profile smoke`) and on demand with a chosen profile and rate, puts
`summary.md` on the job page and uploads `tests/reports/` as an artifact. Load numbers from a
shared runner are a smoke check, not a benchmark.
