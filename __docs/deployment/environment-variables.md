# Environment variables

Every value that differs between environments is a variable. Each one has two defaults, because
there are two places a service can run:

- **YAML default** (`${NAME:default}` in `application.yaml`): what a service started from IDEA
  uses. Addresses are `localhost`.
- **Compose default** (`${NAME:-default}` in the compose files): what a container gets when no
  env file is passed. Addresses are container names inside the compose network (`postgres`,
  `rabbitmq`, `discovery`).

Both defaults are the local values, so `docker compose up` and IDEA work without any file. The
values of another environment come from an env file at the repository root:

| File | For | Committed |
|---|---|---|
| `env.local` | the local stack, with personal keys (Fourthwall API user) | no |
| `env.test` | the test environment | no |
| `env.prod` | production | no |

```bash
docker compose --env-file env.prod -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

All three are git-ignored because they hold credentials, and all three list the same variables,
the ones on this page. `env.test` and `env.prod` were created with random secrets; every
`CHANGE_ME` in them (public addresses, SMTP, Fourthwall API user, Google Maps key) has to be
filled in before the first start. The env file only feeds the `${...}` in the compose files;
each container receives the variables its `environment:` lists, not the whole file. Services
started from IDEA do not read these files.

See [configuration](../conventions/configuration.md) for the rules behind this layout.

## Set once, on an empty volume

Some containers read their credentials only when their volume is initialised. Changing the
variable later changes what the **services** send, not what the container accepts, and the two
stop matching:

| Variable | Read on | To change it on an existing volume |
|---|---|---|
| `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | first start of `pgdata` | `ALTER USER ... PASSWORD ...` in `psql`, then the env file |
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | first start of `rabbitdata` | `rabbitmqctl change_password`, then the env file |
| `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` | first start of Keycloak's database | in the Keycloak console |
| `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` | first start of `grafanadata` | in Grafana |
| `PGADMIN_DEFAULT_EMAIL`, `PGADMIN_DEFAULT_PASSWORD` | first start of `pgadmindata` | in pgAdmin |

`MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` are read on every start. pgAdmin's
`docker/pgadmin/servers.json` and `pgpass` carry the local `derechi` credentials; pgAdmin is a
local tool.

## Shared by the services

| Variable | YAML default | Compose default | Read by | Purpose |
|---|---|---|---|---|
| `POSTGRES_HOST` | `localhost` | `postgres` | Client-API, Admin-API, Worker, DB-Postgres, Keycloak | database host |
| `POSTGRES_PORT` | `5432` | `5432` | same | database port inside the network |
| `POSTGRES_DB` | `derechi` | `derechi` | same, and the `postgres` container | application database |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `derechi` / `derechi` | same | same, and the `postgres` container | database credentials |
| `RABBITMQ_HOST` | `localhost` | `rabbitmq` | Client-API, Admin-API, Worker, Notification, `rabbitmq-init` | broker host |
| `RABBITMQ_PORT` | `5672` | `5672` | the services | AMQP port |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `derechi` / `derechi` | same | the services, `rabbitmq-init`, and the `rabbitmq` container | broker credentials |
| `EUREKA_URL` | `http://localhost:8761/eureka` | `http://discovery:8761/eureka` | every service except Discovery | registry address |
| `JAVA_OPTS` | empty | `-Xms64m -Xmx256m ... -XX:+UseSerialGC` | every service | JVM flags; the default is laptop sizing |

`spring.datasource.*`, `spring.rabbitmq.*` and `eureka.client.service-url.defaultZone` are
built from these in every `application.yaml`. Spring's relaxed binding still applies on top
(`SPRING_DATASOURCE_URL` would beat the YAML), but nothing sets those any more.

## Discovery

Only `JAVA_OPTS`. Port 8761.

## Getaway

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `KEYCLOAK_URI` | `http://localhost:8180` | `http://keycloak:8080` | target of the `keycloak` route (`/realms/**`, `/resources/**`) |
| `MINIO_URI` | `http://localhost:9000` | `http://minio:9000` | target of the `files` (GET) and `files-upload` (PUT) routes |
| `MINIO_BUCKET` | `derechi-files` | not set | bucket segment in the upload path and the `/files/**` rewrite |
| `MINIO_MAX_UPLOAD` | `5MB` | not set | `RequestSize` filter on presigned uploads |
| `WEB_ORIGIN_PATTERNS` | `http://localhost:[*],http://127.0.0.1:[*],http://192.168.*:[*],http://10.*:[*]` | same | CORS `allowed-origin-patterns`; the web client's real origin outside a LAN |

## Client-API

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `POSTGRES_*`, `RABBITMQ_*`, `EUREKA_URL` | see above | see above | |
| `FOURTHWALL_SHOP_URL` | `https://derechi-shop.fourthwall.com` | same | the shop whose checkout the unlock dialog opens |
| `FOURTHWALL_API_USERNAME` / `FOURTHWALL_API_PASSWORD` | `dev-user` / `dev-password` | same | the API user from Settings, For developers, Open API (basic auth); creates the per-response products. The real values live in `env.local` / `env.prod`, never in the repository |
| `FOURTHWALL_WEBHOOK_SECRET` | `dev-secret` | same | the secret of the `ORDER_PLACED` webhook; every webhook body is signed with it (`X-Fourthwall-Hmac-SHA256`). On a server it must equal the secret set in Fourthwall |

Supported countries are not an environment variable; they are `derechi.countries.supported` in
the YAML and must match Admin-API's list.

## Admin-API

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `POSTGRES_*`, `RABBITMQ_*`, `EUREKA_URL` | see above | see above | |
| `SPRING_PROFILES_ACTIVE` | none (`!docker` branch of the YAML) | `docker` (fixed, not a variable) | switches Keycloak from a single `issuer-uri` to split URIs |
| `KEYCLOAK_URI` | `http://keycloak:8080` (read only under `docker`) | `http://keycloak:8080` | token, JWKS and user-info endpoints, reached from inside the network |
| `KEYCLOAK_PUBLIC_URI` | `http://localhost:8180` | same | the address browsers reach Keycloak at: the authorization endpoint under `docker`, the issuer otherwise; also Keycloak's `KC_HOSTNAME` in full mode |
| `MINIO_ENDPOINT` | `http://localhost:9000` | `http://minio:9000` | S3 endpoint used to upload images; also `minio-init`'s target |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `derechi` / `derechi123` | same | S3 credentials, and the `minio` container's root user |
| `MINIO_PUBLIC_URL` | `http://localhost:9000/derechi-files` | `http://localhost:8080/files` | base of image URLs rendered in the admin UI; in full mode they go through the Gateway's `/files` route |
| `GOOGLE_MAPS_API_KEY` | a committed dev key (known issue, see [configuration](../conventions/configuration.md#rule-7-no-secrets-in-the-repository)) | passed on only when the env file sets it | Google Places widget in the item form |

## Worker

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `POSTGRES_*`, `RABBITMQ_*`, `EUREKA_URL` | see above | see above | |
| `DERECHI_SITE_URL` | `http://localhost:3000` | same | public address of the web client, used for the links in claim messages |

The queue names `worker.items`, `worker.claims` and `worker.archive` are `derechi.items.queue`,
`derechi.claims.queue` and `derechi.archive.queue` in the YAML, not environment variables.

## Notification

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `RABBITMQ_*`, `EUREKA_URL` | see above | see above | no database access |
| `NOTIFY_EMAIL_HOST` | `localhost` | `mailpit` | SMTP host for the e-mail channel |
| `NOTIFY_EMAIL_PORT` | `1025` | `1025` | SMTP port |
| `NOTIFY_EMAIL_USERNAME` / `NOTIFY_EMAIL_PASSWORD` | empty | empty | SMTP auth; Mailpit accepts anything |
| `NOTIFY_EMAIL_FROM` | `no-reply@derechi.local` | same | sender address |

The presence of `notify.channels.email.host` is what enables the channel in NotifyHub, so
`NOTIFY_EMAIL_HOST` must not be set to an empty string; an empty value counts as "configured"
and fails validation at startup. Other channels (Telegram bot token, SMS provider) are added as
new placeholders when they are enabled; see
[add-a-notification-channel](../extending/add-a-notification-channel.md).

## Infrastructure containers

| Container | Reads |
|---|---|
| postgres | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`; `initdb/01-keycloak.sh` creates the `keycloak` database owned by `POSTGRES_USER` |
| rabbitmq | `RABBITMQ_USER`, `RABBITMQ_PASSWORD` as `RABBITMQ_DEFAULT_USER` / `_PASS` |
| rabbitmq-init | `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`; imports `docker/rabbitmq/definitions.json` |
| keycloak | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_JAVA_OPTS`, and `POSTGRES_HOST` / `_PORT` / `_USER` / `_PASSWORD` for its `keycloak` database; full mode adds `KEYCLOAK_PUBLIC_URI` as `KC_HOSTNAME` |
| minio | `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` as the root user |
| minio-init | `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` |
| grafana | `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` |
| pgadmin | `PGADMIN_DEFAULT_EMAIL`, `PGADMIN_DEFAULT_PASSWORD` |
| mailpit | nothing environment-specific |
