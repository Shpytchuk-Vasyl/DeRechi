# Docker Compose

Two files, one project named `derechi`.

| File | Contains | Command |
|---|---|---|
| `docker-compose.yml` | infrastructure only | `docker compose up -d` |
| `docker-compose.services.yml` | the six Spring services plus overrides for Prometheus and Keycloak | `docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build` |

## Why the second file is an override

`docker-compose.services.yml` is not a complete compose file: it has no `postgres`, `rabbitmq` or `minio` definitions, only the services that run on top of them, and it **modifies** two services from the first file:

- `prometheus` gets a different `command` so it reads `prometheus-full.yml`, where targets are container names, instead of `prometheus.yml`, where targets are `host.docker.internal:<port>`.
- `keycloak` gets `KC_HOSTNAME` and `KC_HOSTNAME_BACKCHANNEL_DYNAMIC` so that browser redirects use `localhost:8180` while containers talk to `keycloak:8080`; see [keycloak](keycloak.md).

Compose's `include:` directive cannot do this: a service defined in an included file cannot be redefined by the including file, and the attempt fails with `services.prometheus conflicts with imported resource`. Passing both files with `-f` merges them, with the second winning for the keys it sets. Keep it that way; do not duplicate the infrastructure into the second file to make it standalone.

## Anchors in the services file

The services file uses YAML anchors to avoid repeating the same environment blocks:

| Anchor | Provides |
|---|---|
| `x-service-base` | `build` context and `Dockerfile`, `restart: unless-stopped`, `depends_on: discovery` |
| `x-eureka` | `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE=http://discovery:8761/eureka` |
| `x-postgres` | `SPRING_DATASOURCE_USERNAME` / `PASSWORD` |
| `x-rabbitmq` | `SPRING_RABBITMQ_HOST/PORT/USERNAME/PASSWORD` |
| `x-jvm` | `JAVA_OPTS` with a 256 MB heap and SerialGC |

A service merges what it needs: `environment: { <<: [*jvm, *eureka, *postgres, *rabbitmq], SPRING_DATASOURCE_URL: ... }`. A new service copies one of the existing blocks and adds its own build `args: MODULE: <Name>`.

The `x-jvm` limits exist so the whole stack fits on a laptop. They are **not** production settings; a real deployment sets `JAVA_OPTS` per service from the outside.

## Dependencies and health

Infrastructure services declare `healthcheck`s (`pg_isready`, `rabbitmq-diagnostics ping`, `mc ready`, Mailpit `readyz`). Services use `depends_on` with `condition: service_healthy` for PostgreSQL, RabbitMQ and Mailpit, `service_completed_successfully` for `minio-init` (Admin-API needs the bucket to exist), and plain `service_started` for `discovery` (it has no healthcheck; Eureka clients retry registration by themselves).

Keycloak depends on `postgres` only; it creates its own schema in the `keycloak` database on first start.

## Volumes

| Volume | Holds |
|---|---|
| `pgdata` | both databases, `derechi` and `keycloak` |
| `rabbitdata` | queues and the imported definitions |
| `prometheusdata` | 15 days of metrics |
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
