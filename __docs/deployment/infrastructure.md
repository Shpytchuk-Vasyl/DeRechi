# Infrastructure

The supporting services started by `docker compose up -d`. All of them publish their ports on the host, so the same addresses work whether the Spring services run in IDEA or in containers.

| Service | Image | Ports (host) | Credentials | Console |
|---|---|---|---|---|
| PostgreSQL + PostGIS | `derechi/postgis:17-3.5-uk` (built from `docker/postgres`) | 5432 | `derechi` / `derechi`, database `derechi` | none; use `psql` or IDEA |
| pgAdmin | `dpage/pgadmin4:9` | 5050 | `admin@derechi.local` / `admin` | `http://localhost:5050` |
| RabbitMQ | `rabbitmq:4.3.5-management` | 5672 AMQP, 15672 UI, 15692 metrics | `derechi` / `derechi` | `http://localhost:15672` |
| Keycloak | `quay.io/keycloak/keycloak:26.4` | 8180 (container 8080) | `admin` / `admin`, realm `derechi` | `http://localhost:8180` |
| MinIO | `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` | 9000 S3, 9001 console | root (console): `derechi` / `derechi123`; application account `derechi-app` / `derechi-app-secret`; bucket `derechi-files` | `http://localhost:9001` |
| Mailpit | `axllent/mailpit:v1.28` | 1025 SMTP, 8025 UI | none | `http://localhost:8025` |
| Prometheus | `prom/prometheus:v3.14.0` | 9090 | none | `http://localhost:9090` |
| Alertmanager | `prom/alertmanager:v0.34.1` | 9093 | none | `http://localhost:9093` |
| Grafana | `grafana/grafana:13.2.1` | 3000 | `admin` / `admin` | `http://localhost:3000` |

## PostgreSQL

One container, two databases:

- `derechi` is the application database shared by every service. Its schema is owned by `DB-Postgres` and applied with Liquibase; see [database](../architecture/database.md).
- `keycloak` is created by `docker/postgres/initdb/01-keycloak.sh` (as `POSTGRES_USER`) on the first start of an empty volume and is managed entirely by Keycloak.

The image is a custom build of `postgis/postgis:17-3.5` with the Ukrainian hunspell dictionary and stop-word list, needed by the `ukrainian` full-text search configuration that `Worker` uses for ranking; see [docker-image](docker-image.md).

```bash
docker exec -it derechi-postgres psql -U derechi -d derechi
```

## RabbitMQ

The topology is not declared by the applications. The one-shot `rabbitmq-init` container imports `docker/rabbitmq/definitions.json` over the management API (`POST /api/definitions`) after every start of the broker, and the services wait for it to finish:

| Exchange (topic) | Queue (quorum) | Binding | Dead letter |
|---|---|---|---|
| `derechi.items` | `worker.items` | `item.*.created` | `derechi.items.dlx` -> `worker.items.dlq` |
| `derechi.items` | `worker.claims` | `item.*.claimed`, `item.*.returned`, `item.*.paid` | `derechi.items.dlx` -> `worker.claims.dlq` |
| `derechi.items` | `worker.archive` | `item.*.archive` | `derechi.items.dlx` -> `worker.archive.dlq` |
| `derechi.notifications` | `notification.events` | `notification.#` | `derechi.notifications.dlx` -> `notification.events.dlq` |

A new queue or binding is a change to `definitions.json` and takes effect on the next `docker compose up`. The import only adds and updates: a removed or narrowed binding stays until it is deleted in the UI, and a queue whose arguments changed makes the import fail (`docker compose logs rabbitmq-init`).

`definitions.json` holds no users. The broker's only user is `RABBITMQ_USER` / `RABBITMQ_PASSWORD`, created by the image on the first start of an empty volume. That is why the import does not happen at boot through `rabbitmq.conf`: when definitions are imported at boot, RabbitMQ skips creating the default user, so the password could not come from the environment.

 The applications only publish to exchanges and listen on queues by name; see [messaging](../architecture/messaging.md) and [add-an-event](../extending/add-an-event.md).

Port 15692 serves Prometheus metrics (`prometheus.return_per_object_metrics = true`).

## Keycloak

Runs `start --import-realm` (production mode, plain HTTP inside the network, `KC_PROXY_HEADERS=xforwarded` for the TLS proxy) and reads `docker/keycloak/realms/derechi-realm.json` on the first start. For local work a developer uncomments the `start-dev` command and the volume line with the dev users from `docker/keycloak/dev/derechi-users-0.json`. Health and metrics are enabled; metrics are on the management port 9000 **inside** the container, which is not published. Details and the export procedure are in [keycloak](keycloak.md).

`KC_HOSTNAME` is `KEYCLOAK_PUBLIC_URI` (`http://localhost:8180` locally) and `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` lets the containers reach it as `keycloak:8080`.

## MinIO

S3-compatible object storage for item images. `minio-init` is a one-shot container that waits for MinIO to be healthy, creates the `derechi-files` bucket and sets anonymous **download** on it, so image URLs work without signing. It also creates the application account `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` with the policy `docker/minio/derechi-app-policy.json` (get, put and delete objects in `derechi-files`, list the bucket). The root account (`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`) is used only by `minio-init` and for the console. `Admin-API` uploads through the S3 API with the application account, and the web client signs with the same account (`S3_ACCESS_KEY` / `S3_SECRET_KEY`); the web client uploads through the Gateway with presigned PUTs. See [file-storage](../architecture/file-storage.md).

`MINIO_PROMETHEUS_AUTH_TYPE=public` lets Prometheus scrape `/minio/v2/metrics/cluster` without a token.

## pgAdmin

A browser client for the two databases above, for when `psql` or IDEA is not at hand. It runs
in desktop mode (no master password) and comes with both servers registered from
`docker/pgadmin/servers.json`; the password is read from `docker/pgadmin/pgpass`, which pgAdmin
copies into its own storage on the first start. Both files hold the dev credentials only.
**Development only**: it is behind the `dev` compose profile (`--profile dev`, or
`COMPOSE_PROFILES=dev` from `env.local`), because desktop mode has no login and the mounted
`pgpass` holds the database password; production does not start it.

## Mailpit

Catches every e-mail the `Notification` service and Alertmanager send. SMTP on 1025 accepts any or no credentials; the UI on 8025 shows the messages. Nothing leaves the machine. **Development only**: it is behind the `dev` compose profile (`--profile dev`, or `COMPOSE_PROFILES=dev` from `env.local`), so production does not start it and has to set `NOTIFY_EMAIL_*` and `ALERT_SMTP_*` to a real SMTP server.

## Prometheus, Alertmanager and Grafana

Prometheus keeps 15 days of data and scrapes the services (on their management ports), Keycloak, MinIO and RabbitMQ. It evaluates the rules in `docker/prometheus/alerts.yml` (dead letters in a `*.dlq`, a target down) and sends firing alerts to Alertmanager, which mails them to `ALERT_EMAIL_TO`; locally they land in Mailpit. Alertmanager's config is `docker/alertmanager/alertmanager.yml.tmpl`, filled from the `ALERT_*` variables at start. Grafana is provisioned with Prometheus as its default datasource from `docker/grafana/provisioning`. See [monitoring](monitoring.md).

## Resource footprint

Keycloak and the Spring services in full mode run with small heaps (`JAVA_OPTS` / `JAVA_OPTS_APPEND` with 256 MB and SerialGC) so the complete stack fits in a few gigabytes. Every long-running container also has a memory limit (`*_MEMORY` variables, `SERVICE_MEMORY` for the services); see [docker-compose](docker-compose.md#healthchecks-restarts-and-memory). Those limits are for a laptop; they are the first thing to change for a server deployment.
