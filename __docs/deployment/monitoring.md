# Monitoring

Prometheus scrapes everything, Alertmanager mails the alerts, Grafana shows the metrics. The setup is intentionally small: static targets, two alert rules, one provisioned datasource. The architectural overview is in [observability](../architecture/observability.md).

## Two Prometheus configurations

| File | Active when | Service targets |
|---|---|---|
| `docker/prometheus/prometheus.yml` | `docker compose up` (dev mode) | `host.docker.internal:<management port>`, because the services run on the host |
| `docker/prometheus/prometheus-full.yml` | with `docker-compose.services.yml` (full mode) | container names: `admin-api:9083`, `client-api:9082`, ... |

The override file switches Prometheus to the second config by replacing its `--config.file` argument; that is the reason the services compose file is an override and not an `include` (see [docker-compose](docker-compose.md)). Both files share everything else: a 15 s scrape interval, `external_labels: { project: derechi }`, the same jobs, `rule_files: [alerts.yml]` and the Alertmanager at `alertmanager:9093`.

`extra_hosts: host.docker.internal:host-gateway` on the Prometheus container is what makes the dev-mode targets resolvable on Linux; Docker Desktop provides the name by itself.

## Jobs

| Job | Targets | Path |
|---|---|---|
| `prometheus` | itself | `/metrics` |
| `derechi-services` | management ports: Getaway 9080, Client-API 9082, Admin-API 9083, Worker 9085, Notification 9084 | `/actuator/prometheus` |
| `keycloak` | `keycloak:9000` | `/metrics` |
| `minio` | `minio:9000` | `/minio/v2/metrics/cluster` |
| `rabbitmq` | `rabbitmq:15692` | `/metrics`, per queue (`prometheus.return_per_object_metrics = true` in `docker/rabbitmq/rabbitmq.conf`) |

Each service target carries an `application` label (`Getaway`, `Client-API`, `Admin-API`, ...). Grafana's community dashboard **4701** ("JVM (Micrometer)") filters on exactly that label, so the label must be present on every new target or the service does not show up in the dropdown.

**A new service must be added to both files**, with its management port and `application` label, as part of the same change that adds the module; see [add-a-module](../extending/add-a-module.md). Keycloak, MinIO and RabbitMQ are separate jobs because their metrics paths differ from the actuator one.

## Actuator

Every module gets `spring-boot-starter-actuator` and `micrometer-registry-prometheus` from the root POM. Actuator does not share the public port: it listens on its own **management port, the service port + 1000**, which is never published by Compose and never routed by the gateway.

```yaml
management:
  server:
    port: ${MANAGEMENT_PORT:9082}     # Client-API; 9080 Getaway, 9083 Admin-API, 9084 Notification, 9085 Worker
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: never
```

`Admin-API` lets only `/actuator/health`, `/actuator/info` and `/actuator/prometheus` through without a login (`SecurityConfig.PUBLIC_ACTUATOR`); the other modules have no security filter, which is why the port itself must stay internal. `Getaway` used to expose `gateway` as well; that endpoint can add and delete routes at runtime and is gone. Do not expose `env`, `beans`, `heapdump` or `gateway`.

`show-details: never` keeps the health response to `{"status":"UP"}`. When a service "is up but does not work", read the component state from its log or temporarily from inside the network: `docker compose exec admin-api bash -c 'exec 3<>/dev/tcp/127.0.0.1/9083; printf "GET /actuator/health HTTP/1.0\r\n\r\n" >&3; cat <&3'`.

The containers have no Docker healthcheck on actuator: nothing waits for a service, and plain Docker does not restart an unhealthy container, so `TargetDown` is what notices a service that stops answering; see [docker-compose](docker-compose.md#healthchecks-restarts-and-memory).

## Alerts

`docker/prometheus/alerts.yml`, evaluated by Prometheus every 15 s and sent to Alertmanager:

| Alert | Fires when | Severity |
|---|---|---|
| `DeadLetters` | any `*.dlq` queue has a ready message for 1 minute (`rabbitmq_queue_messages_ready{queue=~".+\\.dlq"} > 0`), one alert per queue | warning |
| `TargetDown` | a scrape target (service, Keycloak, MinIO, RabbitMQ, Prometheus) has been unreachable for 2 minutes | critical |

Alertmanager (`prom/alertmanager`, port 9093) groups by `alertname`, `queue` and `application`, repeats an open alert every 4 hours and sends the resolution too. It has a single receiver, email, configured by environment variables:

| Variable | Local default | Meaning |
|---|---|---|
| `ALERT_EMAIL_TO` | `alerts@derechi.local` | who gets the alerts; a list is comma-separated |
| `ALERT_SMTP_HOST` | `mailpit:1025` | `host:port` of the SMTP server |
| `ALERT_SMTP_FROM` | `alertmanager@derechi.local` | sender |
| `ALERT_SMTP_USERNAME`, `ALERT_SMTP_PASSWORD` | empty | SMTP login; empty means none |
| `ALERT_SMTP_REQUIRE_TLS` | `false` (set `true` in `env.prod`) | STARTTLS |

Alertmanager cannot read environment variables, so the container renders `docker/alertmanager/alertmanager.yml.tmpl` with `awk` at start, replacing every `${VAR}`. Values must not contain a double quote or a backslash. Locally the alerts land in Mailpit (`http://localhost:8025`). In dev mode `TargetDown` fires for every service you did not start in IDEA; that is expected.

To try a rule after editing it: `curl -X POST http://localhost:9090/-/reload`, then `http://localhost:9090/alerts` shows its state and `http://localhost:9093` what Alertmanager holds.

## Dead-letter queues

Every consumer queue has a `*.dlq` twin (`docker/rabbitmq/definitions.json`). A listener retries three times; after that the message is rejected without requeue and RabbitMQ moves it to the DLQ. **Nothing consumes the DLQs on purpose**: a message there failed for a reason a retry did not fix, so a person looks at it, fixes the cause and then replays it. The `DeadLetters` alert is the signal.

1. Find the reason. The service log has the exception around the time of the alert (`docker compose logs worker`, `notification`). In the RabbitMQ UI (`http://localhost:15672`, Queues, the `*.dlq` queue, Get messages with "Ack mode: Nack message requeue true") the `x-death` header shows the original queue and the reason; the body shows the event.
2. Fix the cause: the bug, the missing configuration, the unreachable SMTP server.
3. Replay. In the RabbitMQ UI open the DLQ, "Move messages", destination queue = the original queue (`worker.items.dlq` → `worker.items`, `notification.events.dlq` → `notification.events`, ...). This needs the `rabbitmq_shovel` and `rabbitmq_shovel_management` plugins; without them, use "Get messages" to copy the payload and "Publish message" on the original queue with the same `__TypeId__` header.
4. A message that should not be replayed (a test, a duplicate) is purged from the DLQ: "Purge Messages". The alert resolves once the queue is empty.

The item and claim handlers in Worker tolerate a redelivered event (`ItemCreatedHandlerTests`, `ClaimHandlersTest` cover it); check the handler before replaying anything else. For `notification.events` a replay sends the email again unless the same Notification instance still holds the deduplication key in memory (one hour, lost on restart), see [messaging](../architecture/messaging.md).

## Grafana

Provisioned from `docker/grafana/provisioning/datasources/prometheus.yml`: one datasource named `Prometheus` at `http://prometheus:9090`, default, editable. Sign-up is disabled; log in as `admin` / `admin` locally.

No dashboards are provisioned from files. Import 4701 through the UI (Dashboards, New, Import, enter the id) and it is kept in the `grafanadata` volume. If a dashboard should survive `docker compose down -v`, export its JSON and add a `dashboards` provider under `docker/grafana/provisioning`.

## Checking that scraping works

1. Open `http://localhost:9090/targets`. Every target should be `UP`. A service that is not running shows as `DOWN` with a connection error; that is expected in dev mode when you only started some modules.
2. If a running service is `DOWN` in dev mode, check it answers on the host's management port: `curl http://localhost:9083/actuator/prometheus | head`. If it does, the container cannot reach `host.docker.internal`; on Linux make sure the `extra_hosts` entry is present.
3. In full mode a `DOWN` target with "no such host" means the container name in `prometheus-full.yml` and the `container_name`/service name in compose do not match.
4. In Grafana, Explore, query `up{job="derechi-services"}` to see the same information per `application`.

## Retention

`--storage.tsdb.retention.time=15d` in both modes. `--web.enable-lifecycle` is on, so after editing a config or rule file you can reload without a restart:

```bash
curl -X POST http://localhost:9090/-/reload
```

## What is not there yet

No log aggregation: logs are read with `docker compose logs`, from the IDEA console, or for the web client in the hosting's log view. No tracing. Alerts go by email only.
