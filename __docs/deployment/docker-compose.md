# Docker Compose

A base file and one override, one project named `derechi`. Production runs the same two files with `--env-file env.prod`; there is no separate production file.

| File | Contains | Command |
|---|---|---|
| `docker-compose.yml` | infrastructure only | `docker compose --profile dev up -d` |
| `docker-compose.services.yml` | the five Spring services, the one-shot `db-postgres` migration job, and an override for Prometheus | `docker compose --profile dev -f docker-compose.yml -f docker-compose.services.yml up -d --build` |

## Why the second file is an override

`docker-compose.services.yml` is not a complete compose file: it has no `postgres`, `rabbitmq` or `minio` definitions, only the services that run on top of them, and it **modifies** one service from the first file:

- `prometheus` gets a different `command` so it reads `prometheus-full.yml`, where targets are container names, instead of `prometheus.yml`, where targets are `host.docker.internal:<port>`.

Compose's `include:` directive cannot do this: a service defined in an included file cannot be redefined by the including file, and the attempt fails with `services.prometheus conflicts with imported resource`. Passing both files with `-f` merges them, with the second winning for the keys it sets. Keep it that way; do not duplicate the infrastructure into the second file to make it standalone.

## Production defaults, development by uncommenting

Mailpit and pgAdmin are behind the `dev` profile (`profiles: [dev]`), the same mechanism as `k6` and its `load` profile: a plain `docker compose up` does not start them, `--profile dev` or `COMPOSE_PROFILES=dev` (set in `env.local`) does. pgAdmin runs without a login and with the database password mounted, so it must never run in production. Production mails through a real SMTP server (`NOTIFY_EMAIL_*`, `ALERT_SMTP_*`). `notification` waits for Mailpit with `required: false`, so without the profile it starts on its own instead of failing on a missing dependency.


The files are production-safe as committed; what only development wants is commented out in `docker-compose.yml` for a developer to uncomment locally (and not commit):

- `keycloak` runs `start --import-realm`: hostname checks on (`KC_HOSTNAME` = `KEYCLOAK_PUBLIC_URI`, `KC_HOSTNAME_BACKCHANNEL_DYNAMIC` so containers still reach it as `keycloak:8080`), plain HTTP inside the network (`KC_HTTP_ENABLED`) and `KC_PROXY_HEADERS=xforwarded` for the TLS reverse proxy in front. It works locally as is at `http://localhost:8180`. The `start-dev` command above it is commented out.
- The dev users file (`docker/keycloak/dev/derechi-users-0.json`) is mounted by a commented-out volume line, so a fresh stack has no users until you uncomment it; see [keycloak](keycloak.md).
- In production, publish only what the proxy needs; Keycloak's 8180 should be reached through the proxy, not directly, because it trusts the `X-Forwarded-*` headers.

What production needs beyond that comes from `env.prod`: every secret and public URL listed in [environment-variables](environment-variables.md). Compose does not check that they are set: a missing one silently falls back to the local default, so compare `env.prod` against that list before a release.

The old, git-ignored `docker-compose.services-prod.yml` is obsolete; delete it if you still have it.

## Anchors

The services file uses YAML anchors to avoid repeating the same environment blocks:

| Anchor | Provides |
|---|---|
| `x-service-base` | `build` context and `Dockerfile`, `restart: unless-stopped`, memory limit `${SERVICE_MEMORY:-512m}` |
| `x-postgres` | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| `x-rabbitmq` | `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST`, `RABBITMQ_SSL` |
| `x-jvm` | `JAVA_OPTS`, by default a 256 MB heap and SerialGC |

A service merges what it needs: `environment: { <<: [*jvm, *postgres, *rabbitmq], ... }`. A new service copies one of the existing blocks and adds its own build `args: MODULE: <Name>`.

The base file has one anchor, `x-infra` (`restart: unless-stopped`), merged into every long-running infrastructure container.

## Environment files

Every value that differs between environments is a `${NAME:-default}` variable in both files, and the default is the local value. Without an env file compose runs the local stack; with one it runs that environment:

```bash
docker compose --profile dev -f docker-compose.yml -f docker-compose.services.yml up -d --build         # local defaults
docker compose --env-file env.local -f docker-compose.yml -f docker-compose.services.yml up -d --build  # local, with personal keys
docker compose --env-file env.prod  -f docker-compose.yml -f docker-compose.services.yml up -d --build  # production
```

`env.local` is committed and holds only dev values; `env.test` and `env.prod` are git-ignored. The variables are listed in [environment-variables](environment-variables.md); addresses in them are as the containers see each other (`postgres`, not `localhost`). The env file only feeds the `${...}` in the compose files; each container gets exactly the variables its `environment:` lists, not the whole file.

The `x-jvm` limits exist so the whole stack fits on a laptop. They are **not** production settings; a real deployment sets `JAVA_OPTS` per service from the outside.

## Dependencies and health

Only the containers something waits for declare a `healthcheck`: PostgreSQL, RabbitMQ, MinIO and Mailpit; see the next section. Services use `depends_on` with `condition: service_healthy` for PostgreSQL and Mailpit, `service_completed_successfully` for `db-postgres` (Client-API, Admin-API and Worker: the schema must be migrated before they query it; `db-postgres` itself waits for PostgreSQL to be healthy), for `rabbitmq-init` (the queues must exist before a listener starts; it waits for RabbitMQ to be healthy itself) and for `minio-init` (Admin-API needs the bucket to exist). No service waits for another Spring service.

Keycloak depends on `postgres` only; it creates its own schema in the `keycloak` database on first start.

## Healthchecks, restarts and memory

Every long-running container restarts on failure (`restart: unless-stopped`, from `x-infra` and `x-service-base`) and has a memory ceiling (`deploy.resources.limits.memory`), so one leaking container cannot take the host down. The one-shot `rabbitmq-init` and `minio-init` and the on-demand `k6` have neither. The one-shot `db-postgres` merges `x-service-base` for the build and the memory limit, then overrides it with `restart: "no"`: it migrates, validates and exits, and restarting it would only run the same no-op again.

A healthcheck exists only where a `depends_on: condition: service_healthy` waits on it. Plain Docker (no Swarm) does not restart an `unhealthy` container, so a healthcheck nothing waits for only adds a status to `docker compose ps` and a request every few seconds. Whether a service or Keycloak answers is Prometheus's job: `TargetDown` mails after two minutes, see [monitoring](monitoring.md#alerts). Do not add healthchecks to the Spring services or the monitoring stack unless something starts to depend on them.

| Container | Healthcheck (who waits on it) | Memory limit (variable, default) |
|---|---|---|
| postgres | `pg_isready` (pgAdmin with the `dev` profile, Keycloak, `db-postgres`) | `POSTGRES_MEMORY`, 1g |
| pgadmin (`dev` profile) | none | `PGADMIN_MEMORY`, 512m |
| rabbitmq | `rabbitmq-diagnostics ping` (`rabbitmq-init`) | `RABBITMQ_MEMORY`, 512m |
| keycloak | none | `KEYCLOAK_MEMORY`, 768m |
| prometheus | none | `PROMETHEUS_MEMORY`, 512m |
| alertmanager | none | `ALERTMANAGER_MEMORY`, 128m |
| grafana | none | `GRAFANA_MEMORY`, 256m |
| minio | `mc ready local` (`minio-init`, Getaway) | `MINIO_MEMORY`, 512m |
| mailpit | `mailpit readyz` (Notification) | `MAILPIT_MEMORY`, 128m |
| db-postgres (one-shot) | none; Client-API, Admin-API and Worker wait for it to exit with 0 | `SERVICE_MEMORY`, 512m |
| the five services | none | `SERVICE_MEMORY`, 512m each |

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

`docker compose down -v` removes all of them. The Keycloak realm is re-imported on the next start. The schema is re-created by `db-postgres` on the next `up` with `docker-compose.services.yml`; with the base file alone, run `./mvnw -pl DB-Postgres liquibase:update`.

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

The services' management ports (9080, 9082 to 9085, actuator) are **not** published: inside the network Prometheus reaches them, from the host only a service run in IDEA answers on `localhost:908x`. Keycloak's management port 9000 is not published either.
