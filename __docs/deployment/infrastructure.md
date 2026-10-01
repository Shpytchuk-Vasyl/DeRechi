# Infrastructure

The supporting services started by `docker compose up -d`. All of them publish their ports on the host, so the same addresses work whether the Spring services run in IDEA or in containers.

| Service | Image | Ports (host) | Credentials | Console |
|---|---|---|---|---|
| PostgreSQL + PostGIS | `derechi/postgis:17-3.5-uk` (built from `docker/postgres`) | 5432 | `derechi` / `derechi`, database `derechi` | none; use `psql` or IDEA |
| RabbitMQ | `rabbitmq:4.3.5-management` | 5672 AMQP, 15672 UI, 15692 metrics | `derechi` / `derechi` | `http://localhost:15672` |
| Keycloak | `quay.io/keycloak/keycloak:26.4` | 8180 (container 8080) | `admin` / `admin`, realm `derechi` | `http://localhost:8180` |
| MinIO | `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` | 9000 S3, 9001 console | `derechi` / `derechi123`, bucket `derechi-files` | `http://localhost:9001` |
| Mailpit | `axllent/mailpit:v1.28` | 1025 SMTP, 8025 UI | none | `http://localhost:8025` |
| Prometheus | `prom/prometheus:v3.14.0` | 9090 | none | `http://localhost:9090` |
| Grafana | `grafana/grafana:13.2.1` | 3000 | `admin` / `admin` | `http://localhost:3000` |

## PostgreSQL

One container, two databases:

- `derechi` is the application database shared by every service. Its schema is owned by `DB-Postgres` and applied with Liquibase; see [database](../architecture/database.md).
- `keycloak` is created by `docker/postgres/initdb/01-keycloak.sql` on the first start of an empty volume and is managed entirely by Keycloak.

The image is a custom build of `postgis/postgis:17-3.5` with the Ukrainian hunspell dictionary and stop-word list, needed by the `ukrainian` full-text search configuration that `Automatic-Search` uses for ranking; see [docker-image](docker-image.md).

```bash
docker exec -it derechi-postgres psql -U derechi -d derechi
```

## RabbitMQ

The topology is not declared by the applications. It is loaded from `docker/rabbitmq/definitions.json` through `docker/rabbitmq/rabbitmq.conf` (`definitions.import_backend = local_filesystem`):

| Exchange (topic) | Queue (quorum) | Binding | Dead letter |
|---|---|---|---|
| `derechi.items` | `automatic-search.items` | `item.*.created` | `derechi.items.dlx` -> `automatic-search.items.dlq` |
| `derechi.notifications` | `notification.events` | `notification.#` | `derechi.notifications.dlx` -> `notification.events.dlq` |

A new queue or binding is a change to `definitions.json` and takes effect on the next container start with a fresh volume (or by importing the file through the management UI). The applications only publish to exchanges and listen on queues by name; see [messaging](../architecture/messaging.md) and [add-an-event](../extending/add-an-event.md).

Port 15692 serves Prometheus metrics (`prometheus.return_per_object_metrics = true`).

## Keycloak

Runs `start-dev --import-realm` and reads `docker/keycloak/realms/derechi-realm.json` on the first start. Health and metrics are enabled; metrics are on the management port 9000 **inside** the container, which is not published. Details and the export procedure are in [keycloak](keycloak.md).

In full mode it also receives `KC_HOSTNAME=http://localhost:8180` and `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true`.

## MinIO

S3-compatible object storage for item images. `minio-init` is a one-shot container that waits for MinIO to be healthy, creates the `derechi-files` bucket and sets anonymous **download** on it, so image URLs work without signing. `Admin-API` uploads through the S3 API with the root credentials; the web client uploads through the Gateway with presigned PUTs. See [file-storage](../architecture/file-storage.md).

`MINIO_PROMETHEUS_AUTH_TYPE=public` lets Prometheus scrape `/minio/v2/metrics/cluster` without a token.

## Mailpit

Catches every e-mail the `Notification` service sends. SMTP on 1025 accepts any or no credentials; the UI on 8025 shows the messages. Nothing leaves the machine.

## Prometheus and Grafana

Prometheus keeps 15 days of data and scrapes the services, Keycloak, MinIO and RabbitMQ. Grafana is provisioned with Prometheus as its default datasource from `docker/grafana/provisioning`. See [monitoring](monitoring.md).

## Resource footprint

Keycloak and the Spring services in full mode run with small heaps (`JAVA_OPTS` / `JAVA_OPTS_APPEND` with 256 MB and SerialGC) so the complete stack fits in a few gigabytes. Those limits are for a laptop; they are the first thing to change for a server deployment.
