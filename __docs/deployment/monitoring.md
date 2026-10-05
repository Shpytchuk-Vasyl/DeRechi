# Monitoring

Prometheus scrapes everything, Grafana shows it. The setup is intentionally small: static targets, one provisioned datasource, no alerting yet. The architectural overview is in [observability](../architecture/observability.md).

## Two Prometheus configurations

| File | Active when | Service targets |
|---|---|---|
| `docker/prometheus/prometheus.yml` | `docker compose up` (dev mode) | `host.docker.internal:<port>`, because the services run on the host |
| `docker/prometheus/prometheus-full.yml` | with `docker-compose.services.yml` (full mode) | container names: `admin-api:8083`, `client-api:8082`, ... |

The override file switches Prometheus to the second config by replacing its `--config.file` argument; that is the reason the services compose file is an override and not an `include` (see [docker-compose](docker-compose.md)). Both files share everything else: a 15 s scrape interval, `external_labels: { project: derechi }`, and the same set of jobs.

`extra_hosts: host.docker.internal:host-gateway` on the Prometheus container is what makes the dev-mode targets resolvable on Linux; Docker Desktop provides the name by itself.

## Jobs

| Job | Targets | Path |
|---|---|---|
| `prometheus` | itself | `/metrics` |
| `derechi-services` | Getaway 8080, Client-API 8082, Admin-API 8083, Worker 8085, Notification 8084 | `/actuator/prometheus` |
| `keycloak` | `keycloak:9000` | `/metrics` |
| `minio` | `minio:9000` | `/minio/v2/metrics/cluster` |
| `rabbitmq` | `rabbitmq:15692` | `/metrics` |

Each service target carries an `application` label (`Getaway`, `Client-API`, `Admin-API`, ...). Grafana's community dashboard **4701** ("JVM (Micrometer)") filters on exactly that label, so the label must be present on every new target or the service does not show up in the dropdown.

**A new service must be added to both files**, with its port and `application` label, as part of the same change that adds the module; see [add-a-module](../extending/add-a-module.md). Keycloak, MinIO and RabbitMQ are separate jobs because their metrics paths differ from the actuator one.

## Actuator

Every module gets `spring-boot-starter-actuator` and `micrometer-registry-prometheus` from the root POM and exposes:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: always
```

`Getaway` additionally exposes `gateway` (`/actuator/gateway/routes` lists the resolved routes, useful to check which `CLIENT_API_URI` the gateway actually got). `Admin-API` permits `/actuator/**` without login in its security filter chain; the other modules have no security filter at all. Do not expose `env`, `beans` or `heapdump` without putting authentication in front of them.

`/actuator/health` is what you check first when a service "is up but does not work": it reports the database, RabbitMQ and disk state with `show-details: always`.

## Grafana

Provisioned from `docker/grafana/provisioning/datasources/prometheus.yml`: one datasource named `Prometheus` at `http://prometheus:9090`, default, editable. Sign-up is disabled; log in as `admin` / `admin`.

No dashboards are provisioned from files. Import 4701 through the UI (Dashboards, New, Import, enter the id) and it is kept in the `grafanadata` volume. If a dashboard should survive `docker compose down -v`, export its JSON and add a `dashboards` provider under `docker/grafana/provisioning`.

## Checking that scraping works

1. Open `http://localhost:9090/targets`. Every target should be `UP`. A service that is not running shows as `DOWN` with a connection error; that is expected in dev mode when you only started some modules.
2. If a running service is `DOWN` in dev mode, check it answers on the host: `curl http://localhost:8083/actuator/prometheus | head`. If it does, the container cannot reach `host.docker.internal`; on Linux make sure the `extra_hosts` entry is present.
3. In full mode a `DOWN` target with "no such host" means the container name in `prometheus-full.yml` and the `container_name`/service name in compose do not match.
4. In Grafana, Explore, query `up{job="derechi-services"}` to see the same information per `application`.

## Retention

`--storage.tsdb.retention.time=15d` in both modes. `--web.enable-lifecycle` is on, so after editing a config file you can reload without a restart:

```bash
curl -X POST http://localhost:9090/-/reload
```

## What is not there yet

No alert rules, no Alertmanager, no log aggregation. Logs are read with `docker compose logs` or from the IDEA console. Tracing is not configured.
