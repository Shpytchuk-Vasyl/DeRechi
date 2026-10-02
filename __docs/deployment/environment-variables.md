# Environment variables

Everything a container reads from its environment, per service. Two kinds exist:

- **Spring properties** mapped through relaxed binding (`SPRING_DATASOURCE_URL` is `spring.datasource.url`). Any Spring property can be set this way; the table lists the ones compose actually sets.
- **Placeholders** referenced explicitly in `application.yaml` as `${NAME:default}`. The default is the dev value used when running from IDEA.

Values in the "Compose" column are what `docker-compose.services.yml` sets in full mode. See [configuration](../conventions/configuration.md) for the rules behind this layout.

## Shared by every service

| Variable | Default (IDEA) | Compose | Purpose |
|---|---|---|---|
| `JAVA_OPTS` | empty | `-Xms64m -Xmx256m ... -XX:+UseSerialGC` | JVM flags, from the `x-jvm` anchor; local-only sizing |
| `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE` | `http://localhost:8761/eureka` | `http://discovery:8761/eureka` | registry address (not set for Discovery itself) |

## Discovery

Only `JAVA_OPTS`. Port 8761.

## Getaway

| Variable | Default | Compose | Purpose |
|---|---|---|---|
| `KEYCLOAK_URI` | `http://localhost:8180` | `http://keycloak:8080` | target of the `keycloak` route (`/realms/**`, `/resources/**`) |
| `MINIO_URI` | `http://localhost:9000` | `http://minio:9000` | target of the `files` (GET) and `files-upload` (PUT) routes |
| `MINIO_BUCKET` | `derechi-files` | not set | bucket segment in the upload path and the `/files/**` rewrite |
| `MINIO_MAX_UPLOAD` | `5MB` | not set | `RequestSize` filter on presigned uploads |
| `WEB_ORIGIN_PATTERNS` | `http://localhost:[*],http://127.0.0.1:[*],http://192.168.*:[*],http://10.*:[*]` | not set | CORS `allowed-origin-patterns`; set it to the real web origin outside a LAN |

## Client-API

| Variable | Default | Compose | Purpose |
|---|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/derechi` | `jdbc:postgresql://postgres:5432/derechi` | shared database |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `derechi` / `derechi` | same | from the `x-postgres` anchor |
| `SPRING_RABBITMQ_HOST` / `SPRING_RABBITMQ_PORT` | `localhost` / `5672` | `rabbitmq` / `5672` | from the `x-rabbitmq` anchor |
| `SPRING_RABBITMQ_USERNAME` / `SPRING_RABBITMQ_PASSWORD` | `derechi` / `derechi` | same | |

Supported countries are not an environment variable; they are `derechi.countries.supported` in the YAML and must match Admin-API's list.

## Admin-API

| Variable | Default | Compose | Purpose |
|---|---|---|---|
| `SPRING_DATASOURCE_*`, `SPRING_RABBITMQ_*` | as for Client-API | as for Client-API | |
| `SPRING_PROFILES_ACTIVE` | none (`!docker` branch of the YAML applies) | `docker` | switches Keycloak from a single `issuer-uri` to split URIs |
| `KEYCLOAK_URI` | `http://keycloak:8080` (only read under the `docker` profile) | `http://keycloak:8080` | token, JWKS and user-info endpoints, reached from inside the Docker network |
| `KEYCLOAK_PUBLIC_URI` | `http://localhost:8180` | `${KEYCLOAK_PUBLIC_URI:-http://localhost:8180}` from the host environment | authorization endpoint the **browser** is redirected to |
| `MINIO_ENDPOINT` | `http://localhost:9000` | `http://minio:9000` | S3 endpoint used to upload images |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `derechi` / `derechi123` | not set (defaults) | S3 credentials |
| `MINIO_PUBLIC_URL` | `http://localhost:9000/derechi-files` | `${MINIO_PUBLIC_URL:-http://localhost:8080/files}` from the host environment | base of image URLs rendered in the admin UI; in full mode they go through the Gateway's `/files` route |
| `GOOGLE_MAPS_API_KEY` | a committed dev key (known issue, see [configuration](../conventions/configuration.md#rule-7-no-secrets-in-the-repository)) | not set | Google Places widget in the item form |

`KEYCLOAK_PUBLIC_URI` and `MINIO_PUBLIC_URL` are read from the **host** shell when compose starts, so to expose the stack on another machine run for example:

```bash
KEYCLOAK_PUBLIC_URI=http://192.168.1.10:8180 MINIO_PUBLIC_URL=http://192.168.1.10:8080/files \
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d
```

## Worker

| Variable | Default | Compose | Purpose |
|---|---|---|---|
| `SPRING_DATASOURCE_*`, `SPRING_RABBITMQ_*` | as for Client-API | as for Client-API | |

The queue names `worker.items`, `worker.claims` and `worker.archive` are `derechi.items.queue`, `derechi.claims.queue` and `derechi.archive.queue` in the YAML, not environment variables. `DERECHI_SITE_URL` (default `http://localhost:3000`) is the public address of the web client, used for the links in claim messages.

## Notification

| Variable | Default | Compose | Purpose |
|---|---|---|---|
| `SPRING_RABBITMQ_*` | as for Client-API | as for Client-API | no database access |
| `NOTIFY_EMAIL_HOST` | `localhost` | `mailpit` | SMTP host for the e-mail channel |
| `NOTIFY_EMAIL_PORT` | `1025` | not set | SMTP port |
| `NOTIFY_EMAIL_USERNAME` / `NOTIFY_EMAIL_PASSWORD` | empty | not set | SMTP auth; Mailpit accepts anything |

The presence of `notify.channels.email.host` is what enables the channel in NotifyHub, so `NOTIFY_EMAIL_HOST` must not be set to an empty string; an empty value counts as "configured" and fails validation at startup. Other channels (Telegram bot token, SMS provider) are added as new placeholders when they are enabled; see [add-a-notification-channel](../extending/add-a-notification-channel.md).

## Infrastructure containers

Set in `docker-compose.yml`, not meant to be changed per environment without also changing the service defaults above:

| Container | Variables |
|---|---|
| postgres | `POSTGRES_USER=derechi`, `POSTGRES_PASSWORD=derechi`, `POSTGRES_DB=derechi` |
| rabbitmq | `RABBITMQ_DEFAULT_USER=derechi`, `RABBITMQ_DEFAULT_PASS=derechi` |
| keycloak | `KC_BOOTSTRAP_ADMIN_USERNAME/PASSWORD=admin`, `KC_DB=postgres`, `KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak`, `KC_DB_USERNAME/PASSWORD=derechi`, `KC_HEALTH_ENABLED`, `KC_METRICS_ENABLED`; full mode adds `KC_HOSTNAME=http://localhost:8180`, `KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true` |
| minio | `MINIO_ROOT_USER=derechi`, `MINIO_ROOT_PASSWORD=derechi123`, `MINIO_PROMETHEUS_AUTH_TYPE=public` |
| grafana | `GF_SECURITY_ADMIN_USER/PASSWORD=admin`, `GF_USERS_ALLOW_SIGN_UP=false` |
| mailpit | `MP_MAX_MESSAGES=500`, `MP_SMTP_AUTH_ACCEPT_ANY=true`, `MP_SMTP_AUTH_ALLOW_INSECURE=true` |
