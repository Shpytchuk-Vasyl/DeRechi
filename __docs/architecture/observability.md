# Observability

## Metrics

Every Maven module inherits `spring-boot-starter-actuator` and
`micrometer-registry-prometheus` from the root pom and exposes
`health`, `info` and `prometheus` over HTTP (`management.endpoints.web.exposure.include`
in each `application.yaml`; `Getaway` adds `gateway`). Health shows details
(`show-details: always`), which is convenient on a dev box and something to revisit before
exposing a service publicly.

Prometheus v3.14.0 runs as a container with a 15-day retention and `--web.enable-lifecycle`
(so `curl -X POST localhost:9090/-/reload` picks up a config change). It also runs with
`--web.enable-remote-write-receiver`, so k6 load tests can push their metrics into it
(`k6_*` series, see [load testing](../developer-guide/load-testing.md)). There are two
configurations in `docker/prometheus/`, and which one is active depends on how the services
run:

| File | Used by | Service targets |
|---|---|---|
| `prometheus.yml` | `docker compose up` (infrastructure only, services in the IDE) | `host.docker.internal:<port>`, the container reaches the host |
| `prometheus-full.yml` | `docker compose -f docker-compose.yml -f docker-compose.services.yml up` | container names, `getaway:8080`, `client-api:8082`, ... |

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

## Grafana

Grafana 13.2.1 on `http://localhost:3000`, `admin` / `admin`, sign-up disabled. The only
provisioned thing is the Prometheus datasource
(`docker/grafana/provisioning/datasources/prometheus.yml`, `http://prometheus:9090`, set as
default). Dashboards are not provisioned; import 4701 for the JVM view, and the RabbitMQ and
MinIO official dashboards if you need them. Imported dashboards persist in the `grafanadata`
volume.

## Health

`/actuator/health` on each service includes the datasource and RabbitMQ contributors where
those are on the classpath. Compose health checks exist for PostgreSQL, RabbitMQ, MinIO and
Mailpit so dependent containers wait for them; the Spring services have no container
health check.

## Logging

Plain SLF4J through Logback, Boot defaults. Loggers are declared as
`private static final Logger log = LoggerFactory.getLogger(X.class);`; `@Slf4j` is not used
(see [../conventions/java-code-style.md](../conventions/java-code-style.md)). Logs go to
stdout, which in Compose means `docker compose logs -f <service>`. There is no log
aggregation and no tracing; Micrometer tracing is not on the classpath.

Some existing log messages are in Ukrainian and some in English; grep for both when
searching logs.
