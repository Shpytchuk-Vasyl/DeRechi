# Docker Compose

A base file and two overrides, one project named `derechi`.

| File | Contains | Command |
|---|---|---|
| `docker-compose.yml` | infrastructure only | `docker compose up -d` |
| `docker-compose.services.yml` | the five Spring services plus overrides for Prometheus and Keycloak | `docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build` |
| `docker-compose.prod.yml` | production overrides on top of the other two: Keycloak in `start` mode without dev users, required secrets | `docker compose --env-file env.prod -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.prod.yml up -d --build` |

## Why the second file is an override

`docker-compose.services.yml` is not a complete compose file: it has no `postgres`, `rabbitmq` or `minio` definitions, only the services that run on top of them, and it **modifies** two services from the first file:

- `prometheus` gets a different `command` so it reads `prometheus-full.yml`, where targets are container names, instead of `prometheus.yml`, where targets are `host.docker.internal:<port>`.
- `keycloak` gets `KC_HOSTNAME` and `KC_HOSTNAME_BACKCHANNEL_DYNAMIC` so that browser redirects use `localhost:8180` while containers talk to `keycloak:8080`; see [keycloak](keycloak.md).

Compose's `include:` directive cannot do this: a service defined in an included file cannot be redefined by the including file, and the attempt fails with `services.prometheus conflicts with imported resource`. Passing both files with `-f` merges them, with the second winning for the keys it sets. Keep it that way; do not duplicate the infrastructure into the second file to make it standalone.

## The production override

`docker-compose.prod.yml` is the third `-f` and works the same way. It assumes TLS ends at a reverse proxy in front of the host, which forwards `X-Forwarded-*` headers:

- `keycloak` runs `start --import-realm` instead of `start-dev`, with `KC_HTTP_ENABLED=true` and `KC_PROXY_HEADERS=xforwarded`, and its `volumes` are replaced (`!override`) by the realm file alone, so the dev users from `docker/keycloak/dev` are not imported; see [keycloak](keycloak.md).
- The secrets are required (`${NAME:?}`): Keycloak's admin, public URI and realm placeholders, MinIO's root and application accounts, and the Alertmanager recipient and SMTP host. A missing one stops `docker compose` instead of falling back to the local default. The list is in [environment-variables](environment-variables.md).
- `ALERT_SMTP_REQUIRE_TLS` defaults to `true`.

`!override` needs Docker Compose 2.24 or newer; an older one fails to parse the file.

It supersedes the old, git-ignored `docker-compose.services-prod.yml`; delete that file if you still have it.

## Anchors

The services file uses YAML anchors to avoid repeating the same environment blocks:

| Anchor | Provides |
|---|---|
| `x-service-base` | `build` context and `Dockerfile`, `restart: unless-stopped`, memory limit `${SERVICE_MEMORY:-512m}` |
| `x-healthcheck` | interval, timeout, retries and `start_period` of the service healthchecks; each service adds its own `test` |
| `x-postgres` | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| `x-rabbitmq` | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` |
| `x-jvm` | `JAVA_OPTS`, by default a 256 MB heap and SerialGC |

A service merges what it needs: `environment: { <<: [*jvm, *postgres, *rabbitmq], ... }`. A new service copies one of the existing blocks and adds its own build `args: MODULE: <Name>` and healthcheck port.

The base file has one anchor, `x-infra` (`restart: unless-stopped`), merged into every long-running infrastructure container.

## Environment files

Every value that differs between environments is a `${NAME:-default}` variable in both files, and the default is the local value. Without an env file compose runs the local stack; with one it runs that environment:

```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build                       # local defaults
docker compose --env-file env.local -f docker-compose.yml -f docker-compose.services.yml up -d --build  # local, with personal keys
docker compose --env-file env.prod  -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.prod.yml up -d --build  # production
```

`env.local` is committed and holds only dev values; `env.test` and `env.prod` are git-ignored. The variables are listed in [environment-variables](environment-variables.md); addresses in them are as the containers see each other (`postgres`, not `localhost`). The env file only feeds the `${...}` in the compose files; each container gets exactly the variables its `environment:` lists, not the whole file.

The `x-jvm` limits exist so the whole stack fits on a laptop. They are **not** production settings; a real deployment sets `JAVA_OPTS` per service from the outside.

## Dependencies and health

Every long-running container declares a `healthcheck`; see the next section. Services use `depends_on` with `condition: service_healthy` for PostgreSQL and Mailpit, `service_completed_successfully` for `rabbitmq-init` (the queues must exist before a listener starts; it waits for RabbitMQ to be healthy itself) and for `minio-init` (Admin-API needs the bucket to exist). No service waits for another Spring service.

Keycloak depends on `postgres` only; it creates its own schema in the `keycloak` database on first start.

## Healthchecks, restarts and memory

Every long-running container restarts on failure (`restart: unless-stopped`, from `x-infra` and `x-service-base`) and has a memory ceiling (`deploy.resources.limits.memory`), so one leaking container cannot take the host down. The one-shot `rabbitmq-init` and `minio-init` and the on-demand `k6` have neither.

| Container | Healthcheck | Memory limit (variable, default) |
|---|---|---|
| postgres | `pg_isready` | `POSTGRES_MEMORY`, 1g |
| pgadmin | `/misc/ping` | `PGADMIN_MEMORY`, 512m |
| rabbitmq | `rabbitmq-diagnostics ping` | `RABBITMQ_MEMORY`, 512m |
| keycloak | `/health/ready` on the management port 9000, over bash `/dev/tcp` (the image has no curl or wget) | `KEYCLOAK_MEMORY`, 768m |
| prometheus | `/-/healthy` | `PROMETHEUS_MEMORY`, 512m |
| alertmanager | `/-/healthy` | `ALERTMANAGER_MEMORY`, 128m |
| grafana | `/api/health` | `GRAFANA_MEMORY`, 256m |
| minio | `mc ready local` | `MINIO_MEMORY`, 512m |
| mailpit | `mailpit readyz` | `MAILPIT_MEMORY`, 128m |
| the five services | `/actuator/health` on the management port (service port + 1000), over bash `/dev/tcp` | `SERVICE_MEMORY`, 512m each |

The memory limits and the `x-jvm` heap belong together: a container limit below what the JVM is allowed to take gets the service killed instead of throwing `OutOfMemoryError`.

## Volumes

| Volume | Holds |
|---|---|
| `pgdata` | both databases, `derechi` and `keycloak` |
| `pgadmindata` | pgAdmin's settings and the copied `pgpass` |
| `rabbitdata` | queues and the imported definitions |
| `prometheusdata` | 15 days of metrics |
| `alertmanagerdata` | silences and notification state |
| `grafanadata` | dashboards and users created in the UI |
| `miniodata` | uploaded images in `derechi-files` |
| `mailpitdata` | captured e-mails |

`docker compose down -v` removes all of them. The Keycloak realm is re-imported on the next start, the schema is not (run `liquibase:update`).

## Everyday commands

```bash
docker compose ps                                   # state and health
docker compose logs -f admin-api                    # follow one service (full mode)
docker compose logs -f postgres rabbitmq            # several
docker compose restart keycloak
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build admin-api   # rebuild one image
docker compose -f docker-compose.yml -f docker-compose.services.yml down                      # stop full mode, keep infra volumes
```

Rebuilding one service still runs the whole reactor build in the image's build stage (that is how the Dockerfile works, see [docker-image](docker-image.md)), but the Maven cache mount makes the second build fast.

## Ports

Every service and infrastructure component publishes its port on the host in both modes, so the URLs in [local-development](local-development.md) are the same whether a service runs in IDEA or in a container. Because of that, do not start a service in IDEA while its container is running; the port is already taken and the second instance fails to bind.

The services' management ports (9080, 9082 to 9085, actuator) are **not** published: inside the network Prometheus and the healthchecks reach them, from the host only a service run in IDEA answers on `localhost:908x`. Keycloak's management port 9000 is not published either.
