# Observability

## Metrics

Every Maven module inherits `spring-boot-starter-actuator` and
`micrometer-registry-prometheus` from the root pom and exposes
`health`, `info` and `prometheus` over HTTP (`management.endpoints.web.exposure.include`
in each `application.yaml`). Actuator listens on a separate management port, the service
port + 1000 (`MANAGEMENT_PORT`: `Getaway` 9080, `Client-API` 9082, `Admin-API` 9083,
`Notification` 9084, `Worker` 9085). Compose does not publish those ports and the gateway
never routes `/actuator`, so only Prometheus reaches them.
Health shows no details (`show-details: never`).

Prometheus v3.14.0 runs as a container with a 15-day retention and `--web.enable-lifecycle`
(so `curl -X POST localhost:9090/-/reload` picks up a config change). Load-test metrics go to
the separate Prometheus of the test stack, see [stack tests](../developer-guide/stack-tests.md). There are two
configurations in `docker/prometheus/`, and which one is active depends on how the services
run:

| File | Used by | Service targets |
|---|---|---|
| `prometheus.yml` | `docker compose up` (infrastructure only, services in the IDE) | `host.docker.internal:<management port>` (9080, 9082, ...), the container reaches the host |
| `prometheus-full.yml` | `docker compose -f docker-compose.yml -f docker-compose.services.yml up` | container names on the management ports, `getaway:9080`, `client-api:9082`, ... |

The services override in `docker-compose.services.yml` swaps the `--config.file` argument;
that is why the full stack is an override file rather than a second compose project.

Both files define the same jobs:

| Job | Targets | Path |
|---|---|---|
| `prometheus` | itself | `/metrics` |
| `derechi-services` | all five Spring services, each with an `application` label (`Getaway`, `Client-API`, `Admin-API`, `Worker`, `Notification`) | `/actuator/prometheus` |
| `keycloak` | `keycloak:9000` (management port, `KC_METRICS_ENABLED=true`) | `/metrics` |
| `minio` | `minio:9000` | `/minio/v2/metrics/cluster` (public, `MINIO_PROMETHEUS_AUTH_TYPE=public`) |
| `rabbitmq` | `rabbitmq:15692` | `/metrics` (the `rabbitmq_prometheus` plugin) |

The `application` label matters: Grafana dashboard 4701 (JVM Micrometer) uses it as its
instance selector. A new service goes into both files with that label, see
[../extending/add-a-module.md](../extending/add-a-module.md).

Keycloak is a separate job and not a `derechi-services` target because its metrics are on
a different port and path and carry no `application` label.

## Alerts

Both files load `rule_files: [alerts.yml]` (`docker/prometheus/alerts.yml`) and send firing
alerts to Alertmanager v0.34.1 (`alertmanager:9093`, `http://localhost:9093`), which emails
them using the `ALERT_*` variables. Two rules:

| Alert | Fires when |
|---|---|
| `DeadLetters` | a `*.dlq` queue has ready messages for 1 minute |
| `TargetDown` | any target has `up == 0` for 2 minutes |

Details, and what to do with a dead-lettered message, are in
[../deployment/monitoring.md](../deployment/monitoring.md#dead-letter-queues).

## Grafana

Grafana 13.2.1 on `http://localhost:3000`, `admin` / `admin`, sign-up disabled. The only
provisioned thing is the Prometheus datasource
(`docker/grafana/provisioning/datasources/prometheus.yml`, `http://prometheus:9090`, set as
default). Dashboards are not provisioned; import 4701 for the JVM view, and the RabbitMQ and
MinIO official dashboards if you need them. Imported dashboards persist in the `grafanadata`
volume.

## Health

`/actuator/health` on each service includes the datasource and RabbitMQ contributors where
those are on the classpath. Compose health checks exist only where a `depends_on` waits on
them (PostgreSQL, RabbitMQ, MinIO, Mailpit); a service or Keycloak that stops answering is
caught by Prometheus's `TargetDown` alert instead. Long-running containers restart
`unless-stopped` and have memory limits.

## Logging

Plain SLF4J through Logback, Boot defaults. Loggers come from Lombok's `@Slf4j` in modules
that have Lombok, and from `LoggerFactory.getLogger(X.class)` in the rest
(see [../conventions/java-code-style.md](../conventions/java-code-style.md)). Logs go to
stdout, which in Compose means `docker compose logs -f <service>`. There is no log
aggregation and no tracing; Micrometer tracing is not on the classpath. What and how the
services log is in [../conventions/logging.md](../conventions/logging.md).
