# Infrastructure

The supporting services started by `docker compose up -d`. All of them publish their ports on the host, so the same addresses work whether the Spring services run in IDEA or in containers.

| Service | Image | Ports (host) | Credentials | Console |
|---|---|---|---|---|
| PostgreSQL + PostGIS | `derechi/postgis:17-3.5-uk` (built from `docker/postgres`) | 5432 | `derechi` / `derechi`, database `derechi` | none; use `psql` or IDEA |
| pgAdmin | `dpage/pgadmin4:9` | 5050 | `admin@derechi.local` / `admin` | `http://localhost:5050` |
| RabbitMQ | `rabbitmq:4.3.5-management` | 5672 AMQP, 15672 UI, 15692 metrics | `derechi` / `derechi` | `http://localhost:15672` |
| Keycloak | `quay.io/keycloak/keycloak:26.4` | 8180 (container 8080) | `admin` / `admin`, realm `derechi` | `http://localhost:8180` |
| MinIO | `quay.io/minio/minio:RELEASE.2025-09-07T16-13-09Z` | 9000 S3, 9001 console | root (console): `derechi` / `derechi123`; application account `derechi-app` / `derechi-app-secret`; bucket `derechi-files` | `http://localhost:9001` |
| Caddy (API entry) | `caddy:2.11.4-alpine` | 8080 | none | none; `http://localhost:8080/graphiql` goes through it |
| Mailpit | `axllent/mailpit:v1.28` | 1025 SMTP, 8025 UI | none | `http://localhost:8025` |
| Prometheus | `prom/prometheus:v3.14.0` | 9090 | none | `http://localhost:9090` |
| Alertmanager | `prom/alertmanager:v0.34.1` | 9093 | none | `http://localhost:9093` |
| Grafana | `grafana/grafana:13.2.1` | 3000 | `admin` / `admin` | `http://localhost:3000` |

## PostgreSQL

One container, two databases:

- `derechi` is the application database shared by every service. Its schema is owned by `DB-Postgres` and applied with Liquibase; see [database](../architecture/database.md).
- `keycloak` is created by `docker/postgres/initdb/01-keycloak.sh` (as `POSTGRES_USER`) on the first start of an empty volume and is managed entirely by Keycloak.

The image is a custom build of `postgis/postgis:17-3.5` with the Ukrainian and Polish hunspell dictionaries and stop-word lists, needed by the `ukrainian` and `polish` full-text search configurations that `Worker` uses for ranking and `Client-API` for the public search; see [docker-image](docker-image.md).

```bash
docker exec -it derechi-postgres psql -U derechi -d derechi
```

## RabbitMQ

The topology is not declared by the applications. The one-shot `rabbitmq-init` container imports `docker/rabbitmq/definitions.json` over the management API (`POST /api/definitions`) after every start of the broker, and the services wait for it to finish:

One queue per consuming service; every queue dead-letters into `dead-letter.exchange` (direct) with its own name as the key:

| Queue (quorum) | Exchange (topic) and binding | Dead letter |
|---|---|---|
| `worker.queue` | `notice.status.exchange`: `notice.*.created`, `notice.*.updated`, `notice.*.returned`; `claims.exchange`: `claim.*.claimed`, `claim.*.paid`; `notice.action.exchange`: `notice.*.archive` | `dead-letter.exchange` -> `worker.dlq` |
| `notification.queue` | `notification.exchange`: `notification.#` | `dead-letter.exchange` -> `notification.dlq` |
| `ingest.queue` | `notice.status.exchange`: `notice.*.created`, `notice.*.updated`, `notice.*.archived`, `notice.*.deleted` | `dead-letter.exchange` -> `ingest.dlq` |

`ingest.queue` is read by `Ingest`, which mirrors notices from the site into the Telegram channel. An earlier version of the file bound it with `notice.*.returned`; on a broker where that binding was imported, delete it in the UI, or `Ingest` rejects every returned notice into `ingest.dlq`.

A new queue or binding is a change to `definitions.json` and takes effect on the next `docker compose up`. The import only adds and updates: a removed or narrowed binding stays until it is deleted in the UI, the exchanges and queues of the previous topology (`derechi.items`, `derechi.notifications`, `worker.items`, `worker.claims`, `worker.archive`, `notification.events` and their `.dlx`/`.dlq`) stay on an old volume and on a hosted broker until they are deleted by hand, and a queue whose arguments changed makes the import fail (`docker compose logs rabbitmq-init`).

`definitions.json` holds no users. The broker's only user is `RABBITMQ_USER` / `RABBITMQ_PASSWORD`, created by the image on the first start of an empty volume. That is why the import does not happen at boot through `rabbitmq.conf`: when definitions are imported at boot, RabbitMQ skips creating the default user, so the password could not come from the environment.

 The applications only publish to exchanges and listen on queues by name; see [messaging](../architecture/messaging.md) and [add-an-event](../extending/add-an-event.md).

Port 15692 serves Prometheus metrics (`prometheus.return_per_object_metrics = true`).

## Keycloak

Runs `start --import-realm` (production mode, plain HTTP inside the network, `KC_PROXY_HEADERS=xforwarded` for the TLS proxy) and reads `docker/keycloak/realms/derechi-realm.json` on the first start. For local work a developer uncomments the `start-dev` command and the volume line with the dev users from `docker/keycloak/dev/derechi-users-0.json`. Health and metrics are enabled; metrics are on the management port 9000 **inside** the container, which is not published. Details and the export procedure are in [keycloak](keycloak.md).

`KC_HOSTNAME` is `KEYCLOAK_PUBLIC_URI` (`http://localhost:8180` locally) and `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` lets the containers reach it as `keycloak:8080`.

## MinIO

S3-compatible object storage for item images. `minio-init` is a one-shot container that waits for MinIO to be healthy, creates the `derechi-files` bucket and sets anonymous **download** on it, so image URLs work without signing. It also creates the application account `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` with the policy `docker/minio/derechi-app-policy.json` (get, put and delete objects in `derechi-files`, list the bucket). The root account (`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`) is used only by `minio-init` and for the console. `Admin-API` uploads through the S3 API with the application account, and the web client signs with the same account (`S3_ACCESS_KEY` / `S3_SECRET_KEY`); the web client uploads through [Caddy](#caddy-api-entry) with presigned PUTs. See [file-storage](../architecture/file-storage.md).

`MINIO_PROMETHEUS_AUTH_TYPE=public` lets Prometheus scrape `/minio/v2/metrics/cluster` without a token.

## Caddy (API entry)

**For now the public API entry on port 8080 is Caddy, not the `Getaway` module.** The gateway's job was three routes, a body cap, a cache header and CORS, all of which Caddy covers in configuration, and in production a Caddy sits in front anyway; dropping the gateway saves a JVM and a hop. This is temporary: the module's code stays in the repository, commented out in the root `<modules>`, in the `Dockerfile` and in `tests.yml`, and compose, `images.yml` and the Prometheus configs no longer mention it. It comes back when we need what Caddy does not give, see [Getaway](../architecture/modules/getaway.md#bringing-it-back).

The routes are in `docker/caddy/api.Caddyfile`:

| Match | Upstream | What happens |
|---|---|---|
| `/graphql`, `/graphiql`, `/api/client/**`, any method | `CLIENT_API_URI` | passed through |
| `PUT` and `OPTIONS` on `/<MINIO_BUCKET>/**` | `MINIO_URI` | presigned uploads; body capped at 5 MiB (`413` above it, the same 5242880 bytes as the web client's limit). For an HTTP upstream Caddy keeps the `Host` header, so the signature over the public host still matches |
| `GET /files/**` | `MINIO_URI` | rewritten to `/<MINIO_BUCKET>/**`, response gets `Cache-Control: public, max-age=31536000, immutable` |
| anything else | | `404`: no bucket listing, no `DELETE`, no MinIO admin or health paths |

- **No CORS in Caddy.** The only cross-origin request a browser makes here is the presigned `PUT`, and the storage answers it: MinIO allows any origin by default, and in production the browser uploads straight to Backblaze B2 (`S3_PUBLIC_ENDPOINT` in the web client), where the bucket's own CORS rules apply. GraphQL is called only from the Next.js server, and images load as `<img>`. `WEB_ORIGIN_PATTERNS` is read by nothing that runs today.
- **No access log and no metrics**, by decision. There is no Prometheus target for it, so `TargetDown` does not cover it either.
- **Compose.** The `caddy` service lives in `docker-compose.yml` and starts with the infrastructure, because in development mode nothing else serves `:8080`. It runs `api.Caddyfile` alone in plain HTTP on `:8080` (`API_DOMAIN` is not passed to it) and reaches `Client-API` in IDEA at `host.docker.internal:8082`; `docker-compose.services.yml` switches `CLIENT_API_URI` to `client-api:8082`. `MINIO_URI` and `MINIO_BUCKET` come from the environment as for the other services. Memory limit `CADDY_MEMORY`, 128m.
- **Production.** `docker/caddy/Caddyfile` does `import api.Caddyfile` for `API_DOMAIN` (TLS by Caddy) and serves `ADMIN_DOMAIN` and `AUTH_DOMAIN` next to it. Its container needs the whole `docker/caddy` directory mounted (the import is relative) and `API_DOMAIN`, `CLIENT_API_URI`, `MINIO_URI`, `MINIO_BUCKET` in its environment. Missing variables fall back to the container defaults silently, and a missing `MINIO_URI` means `/files` goes to `http://minio:9000` instead of B2. For an HTTPS upstream like B2, Caddy sends the upstream's own `Host`; that is what `/files` needs, and production does not upload through Caddy.
- **Checking a change.** `docker compose exec caddy caddy validate --config /etc/caddy/api.Caddyfile --adapter caddyfile`, then `docker compose restart caddy`. For the production file, run `caddy adapt` in a throwaway container with `API_DOMAIN`, `ADMIN_DOMAIN` and `AUTH_DOMAIN` set.

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
