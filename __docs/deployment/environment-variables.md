# Environment variables

Every value that differs between environments is a variable. Each one has two defaults, because
there are two places a service can run:

- **YAML default** (`${NAME:default}` in `application.yaml`): what a service started from IDEA
  uses. Addresses are `localhost`.
- **Compose default** (`${NAME:-default}` in the compose files): what a container gets when no
  env file is passed. Addresses are container names inside the compose network (`postgres`,
  `rabbitmq`, `client-api`).

Both defaults are the local values, so `docker compose up` and IDEA work without any file. The
values of another environment come from an env file at the repository root:

| File | For | Committed |
|---|---|---|
| `env.local` | the local stack, dev values only; do not commit personal keys (Fourthwall API user) into it | yes |
| `env.test` | the test environment | no |
| `env.prod` | production | no |

```bash
docker compose --env-file env.prod -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

`env.local` is in Git and holds only the local values. `env.test` and `env.prod` are
git-ignored because they hold credentials. All three list the same variables, the ones on this
page. `env.test` and `env.prod` were created with random secrets; every
`CHANGE_ME` in them (public addresses, SMTP, Fourthwall API user, Google Maps key) has to be
filled in before the first start. The env file only feeds the `${...}` in the compose files;
each container receives the variables its `environment:` lists, not the whole file. Services
started from IDEA do not read these files.

Compose has no production file and does not check these: every variable falls back to its local
default when `env.prod` lacks it. Before a release make sure `env.prod` sets at least
`KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_PUBLIC_URI`, `DERECHI_SITE_URL`,
`DERECHI_ADMIN_URL`, `KEYCLOAK_SERVICES_SECRET`, `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD`,
`MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`, `ALERT_EMAIL_TO`, `ALERT_SMTP_HOST` and
`ALERT_SMTP_REQUIRE_TLS=true`, `NOTIFY_EMAIL_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` /
`_FROM` (the defaults point at Mailpit, which production does not run), besides the database,
RabbitMQ and Fourthwall secrets. On the machine that runs Ingest also `TELEGRAM_API_ID` /
`TELEGRAM_API_HASH`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_PUBLISH_CHAT`, `TELEGRAM_REVIEW_CHAT`,
`OPENROUTER_API_KEY`, `GOOGLE_PLACES_API_KEY`, a random `INGEST_HASH_KEY` and the public
`DERECHI_API_URI`; see [Ingest](#ingest).

`COMPOSE_PROFILES=dev` in `env.local` turns on the development-only containers (Mailpit, pgAdmin);
`env.prod` leaves it unset.

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
| `DERECHI_SITE_URL`, `DERECHI_ADMIN_URL`, `KEYCLOAK_SERVICES_SECRET` (in Keycloak) | first realm import | in the Keycloak console (redirect URIs, client secret) |

`MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` are MinIO's root account, read by `minio` and
`minio-init` on every start; nothing else uses them. `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`
are the application's account, which `minio-init` creates with the root credentials on every
`up`. pgAdmin's
`docker/pgadmin/servers.json` and `pgpass` carry the local `derechi` credentials; pgAdmin is a
local tool and runs only with the `dev` profile.

## Shared by the services

| Variable | YAML default | Compose default | Read by | Purpose |
|---|---|---|---|---|
| `POSTGRES_HOST` | `localhost` | `postgres` | Client-API, Admin-API, Worker, Ingest, DB-Postgres, Keycloak | database host |
| `POSTGRES_PORT` | `5432` | `5432` | same | database port inside the network |
| `POSTGRES_DB` | `derechi` | `derechi` | same, and the `postgres` container | application database |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `derechi` / `derechi` | same | same, and the `postgres` container | database credentials |
| `RABBITMQ_HOST` | `localhost` | `rabbitmq` | Client-API, Admin-API, Worker, Notification, `rabbitmq-init` | broker host |
| `RABBITMQ_PORT` | `5672` | `5672` | the services | AMQP port |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | `derechi` / `derechi` | same | the services, `rabbitmq-init`, and the `rabbitmq` container | broker credentials |
| `RABBITMQ_VHOST` | `/` | `/` | the services | virtual host; on a hosted broker (CloudAMQP) it is the username, not `/` |
| `RABBITMQ_SSL` | `false` | `false` | the services | AMQP over TLS; `true` with port `5671` for a hosted broker |
| `JAVA_OPTS` | empty | `-Xms64m -Xmx256m ... -XX:+UseSerialGC` | every service except Ingest (`INGEST_JAVA_OPTS`) | JVM flags; the default is laptop sizing |
| `MANAGEMENT_PORT` | service port + 1000 (`9080`, `9082`, `9083`, `9084`, `9085`, `9086`) | not set | every service | actuator port (`health`, `info`, `prometheus`); not published by compose, see [monitoring](monitoring.md#actuator) |
| `SERVICE_MEMORY` | n/a | `512m` | compose | memory limit of each service container |

`spring.datasource.*` and `spring.rabbitmq.*` are
built from these in every `application.yaml`. Spring's relaxed binding still applies on top
(`SPRING_DATASOURCE_URL` would beat the YAML), but nothing sets those any more.

## Caddy (API entry)

For now the API entry on 8080 is Caddy, not the `Getaway` module; see
[infrastructure](infrastructure.md#caddy-api-entry). Its defaults live in the Caddyfile
(`{$NAME:default}`), not in an `application.yaml`.

| Variable | Caddyfile default | Compose default | Purpose |
|---|---|---|---|
| `CLIENT_API_URI` | `http://client-api:8082` | `http://host.docker.internal:8082` in `docker-compose.yml` (fixed, `Client-API` runs in IDEA); `http://client-api:8082` from `docker-compose.services.yml` | upstream of `/graphql`, `/graphiql`, `/api/client/**` |
| `MINIO_URI` | `http://minio:9000` | `http://minio:9000` | upstream of the presigned uploads and of `/files/**`; the B2 endpoint in production |
| `MINIO_BUCKET` | `derechi-files` | `derechi-files` | bucket segment of the upload path and of the `/files/**` rewrite; Admin-API reads it too |
| `API_DOMAIN` | `:8080`, plain HTTP | not passed | production only: the public API host, TLS by Caddy |
| `ADMIN_DOMAIN`, `AUTH_DOMAIN` | none | not passed | production only: the other two sites of `docker/caddy/Caddyfile`, which fails to start without them |
| `CADDY_MEMORY` | n/a | `128m` | memory limit of the container |

Caddy reads neither `WEB_ORIGIN_PATTERNS` (no CORS at the entry) nor `MINIO_MAX_UPLOAD`: the
upload cap is fixed at 5 MiB in `api.Caddyfile`, and `MINIO_MAX_UPLOAD` is Ingest's own limit.
Both stay in the gateway's `application.yaml` for the day the module comes back.

No route leads to Keycloak; browsers reach Keycloak directly at `KEYCLOAK_PUBLIC_URI`.

## Client-API

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `POSTGRES_*`, `RABBITMQ_*` | see above | see above | |
| `FOURTHWALL_SHOP_URL` | `https://derechi-shop.fourthwall.com` | same | the shop whose checkout the unlock dialog opens |
| `FOURTHWALL_API_USERNAME` / `FOURTHWALL_API_PASSWORD` | `dev-user` / `dev-password` | same | the API user from Settings, For developers, Open API (basic auth); creates the per-response products. The real values live in `env.local` / `env.prod`, never in the repository |
| `FOURTHWALL_WEBHOOK_SECRET` | `dev-secret` | same | the secret of the `ORDER_PLACED` webhook; every webhook body is signed with it (`X-Fourthwall-Hmac-SHA256`). On a server it must equal the secret set in Fourthwall |
| `FOURTHWALL_PRODUCT_IMAGE_URL` | the shop's product image | same | the checkout thumbnail attached to every per-response product: the `uri` (`https://cdn.fourthwall.com/...`) of an image already in the shop's media library. Compose replaces an empty value with the default; only a blank `derechi.fourthwall.product-image.url` in the YAML sells products without an image. It must be absolute: a URL without `https:` is stored as given and the image never renders. Its size is YAML only, `derechi.fourthwall.product-image.width`/`height` (600x800, the shop's 3:4 product photos), and must match the uploaded file |
| `DISPOSABLE_EMAILS_URL` | the raw `disposable_email_blocklist.conf` of disposable-email-domains on GitHub | same | blocklist of throwaway mail domains for claimants (`derechi.disposable-emails.url`), one domain per line. Client-API downloads it on start-up and then every `refresh-interval` (24h); a failed download keeps the previous list, and until the first succeeds nothing is blocked. Empty turns the check off. Needs outbound HTTPS from the container |
| `DERECHI_SITE_URL` | `http://localhost:3000` | same | public address of the web client (`derechi.site.url`); a paid Fourthwall order gets the notice page as its "download", which closes the buyer's 30-day self-service cancel |

Supported countries are not an environment variable; they are `derechi.countries.supported` in
the YAML and must match Admin-API's list. The Fourthwall client's timeouts are YAML only too:
`derechi.fourthwall.connect-timeout` (3s) and `read-timeout` (10s).

## Admin-API

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `POSTGRES_*`, `RABBITMQ_*` | see above | see above | |
| `SPRING_PROFILES_ACTIVE` | none (`!docker` branch of the YAML) | `docker` (fixed, not a variable) | switches Keycloak from a single `issuer-uri` to split URIs |
| `KEYCLOAK_URI` | `http://keycloak:8080` (read only under `docker`) | `http://keycloak:8080` | token, JWKS and user-info endpoints, reached from inside the network |
| `KEYCLOAK_PUBLIC_URI` | `http://localhost:8180` | same | the address browsers reach Keycloak at: the authorization endpoint under `docker`, the issuer otherwise; also Keycloak's `KC_HOSTNAME` in full mode |
| `MINIO_ENDPOINT` | `http://localhost:9000` | `http://minio:9000` | S3 endpoint used to upload images; also `minio-init`'s target |
| `MINIO_REGION` | `us-east-1` | `us-east-1` | region the uploads are signed for; MinIO ignores it|
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | `derechi-app` / `derechi-app-secret` | same | S3 credentials of the application's account (objects in `derechi-files` only, `docker/minio/derechi-app-policy.json`), created by `minio-init`; not the root user. The web client uses the same account as `S3_ACCESS_KEY` / `S3_SECRET_KEY` |
| `MINIO_PUBLIC_URL` | `http://localhost:9000/derechi-files` | `http://localhost:9000/derechi-files` | base of image URLs rendered in the admin UI; read straight from MinIO, not through Caddy's `/files` |
| `GOOGLE_MAPS_API_KEY` | a committed dev key (known issue, see [configuration](../conventions/configuration.md#rule-7-no-secrets-in-the-repository)) | passed on only when the env file sets it | Google Places widget in the item form |

## Worker

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `POSTGRES_*`, `RABBITMQ_*` | see above | see above | |
| `DERECHI_SITE_URL` | `http://localhost:3000` | same | public address of the web client, used for the links in claim messages |

The queue names `worker.items`, `worker.claims` and `worker.archive` are `derechi.items.queue`,
`derechi.claims.queue` and `derechi.archive.queue` in the YAML, not environment variables.

## Notification

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `RABBITMQ_*` | see above | see above | no database access |
| `NOTIFY_EMAIL_HOST` | `localhost` | `mailpit` | SMTP host for the e-mail channel |
| `NOTIFY_EMAIL_PORT` | `1025` | `1025` | SMTP port |
| `NOTIFY_EMAIL_USERNAME` / `NOTIFY_EMAIL_PASSWORD` | empty | empty | SMTP auth; Mailpit accepts anything |
| `NOTIFY_EMAIL_FROM` | `no-reply@derechi.local` | same | sender address |
| `SMS_FLY_API_KEY` | empty | empty | SMS-fly API key; empty switches the SMS channel off |
| `SMS_FLY_API_URL` | `https://sms-fly.ua/api/v2/api.php` | same | API URL, the cabinet shows the exact one under "API settings" |
| `SMS_FLY_SOURCE` | `DeRechi` | same | SMS sender (alpha-name), must be approved in the SMS-fly cabinet |

The presence of `notify.channels.email.host` is what enables the channel in NotifyHub, so
`NOTIFY_EMAIL_HOST` must not be set to an empty string; an empty value counts as "configured"
and fails validation at startup. SMS is the opposite: it is our own channel (`SmsFlyChannel`),
not NotifyHub's, and it registers only when `SMS_FLY_API_KEY` is non-blank. Other channels
(Telegram bot token) are added as new placeholders when they are enabled; see
[add-a-notification-channel](../extending/add-a-notification-channel.md).

## Ingest

Imports lost-and-found notices from Telegram chats. Compose defaults equal the YAML defaults
except where the table says otherwise. An empty `TELEGRAM_API_ID`/`TELEGRAM_API_HASH`,
`OPENROUTER_API_KEY` or `GOOGLE_PLACES_API_KEY` fails the start. Durations are ISO-8601.

| Variable | YAML default | Compose default | Purpose |
|---|---|---|---|
| `POSTGRES_*` | see above | see above | the `ingest_post` table; no RabbitMQ |
| `INGEST_JAVA_OPTS` | n/a | `-Xms64m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:-UseG1GC -XX:+UseSerialGC --enable-native-access=ALL-UNNAMED` | this service's `JAVA_OPTS` instead of the shared one. TDLib, ONNX Runtime and Tesseract are native and live outside the heap, so keep `-Xmx` well below the memory limit. `--enable-native-access` silences the JDK's warnings for JNI/JNA |
| `INGEST_MEMORY` | n/a | `1g` | memory limit of the container, instead of `SERVICE_MEMORY` |
| `INGEST_PUBLISHERS` | `review` | same | where notices go, comma-separated, any order: `site` (Client-API through Caddy, runs first), `channel` (our channel, links to the site so needs `site`), `review` (`TELEGRAM_REVIEW_CHAT`, also gets skips). `review` alone is a dry run; an unknown name fails the start |
| `INGEST_HASH_KEY` | `dev-hash-key-change-me` | same | HMAC key of `ingest_post.phone_hash`, used to spot duplicates; a random secret in production. Changing it forgets every phone seen so far |
| `INGEST_DUPLICATE_WINDOW` | `P14D` | same | how long a contact phone marks later posts as duplicates |
| `INGEST_MAX_PHOTOS` | `3` | same | photos per post that are downloaded, redacted and published |
| `INGEST_MAX_AGE` | `P30D` | same | older notices are skipped (`too_old`); Client-API accepts 30 days |
| `INGEST_SITE_LOCALE` | `uk` | same | locale segment of the notice links in captions |
| `INGEST_MIN_CONFIDENCE` | `0.6` | same | extractions with a lower model confidence are skipped as `rejected` |
| `DERECHI_SITE_URL` | `http://localhost:3000` | same | base of the notice links in captions |
| `TELEGRAM_API_ID` / `TELEGRAM_API_HASH` | `0` / empty | same | the application from my.telegram.org; `0` means not configured |
| `TELEGRAM_PHONE` | empty | same | phone of the Telegram account, needed only by the one-off `--login` |
| `TDLIB_DATA_DIR` | `tdlib` (relative to the working directory, git-ignored) | `/app/tdlib` (fixed, the `tdlib-data` volume) | TDLib database: the session and downloads. Losing it means another `--login` |
| `TELEGRAM_FOLDER` | `DeRechi` | same | name of the account's chat folder whose channels and groups are read |
| `TELEGRAM_CHATS` | empty | same | per-chat overrides, `username=city[\|region[\|locale]]` separated by `;` or new lines, e.g. `zagubleno911=Львів` |
| `TELEGRAM_DEFAULT_REGION` | `UA` | same | region for chats without an override. The region also gives the authors' time zone (`UA` Kyiv, `PL` Warsaw, `DE` Berlin, `FR` Paris) for dates like "yesterday"; any other region fails the start |
| `TELEGRAM_DEFAULT_LOCALE` | `uk` | same | geocoding language for chats without an override |
| `TELEGRAM_ALBUM_WAIT` | `PT3S` | same | how long to wait for the rest of a post after its last message: album parts, or a text and its photos sent as separate messages by the same sender. Every message is held this long; a larger value catches people who send the photo a few seconds after the text |
| `TELEGRAM_FOLDER_REFRESH` | `PT10M` | same | how often the folder's chat list is re-read |
| `TELEGRAM_CATCH_UP_MAX_AGE` / `TELEGRAM_CATCH_UP_MAX_MESSAGES` | `PT48H` / `200` | same | on start, history read back per chat to pick up posts missed while down |
| `FACEBOOK_COOKIES` | empty (Facebook off) | same | **secret**: the `Cookie` header of a logged-in `www.facebook.com` request (`c_user=...; xs=...; datr=...`), from a separate account |
| `FACEBOOK_GROUPS` | empty | same | groups to read, `group=city[\|region[\|locale]]` separated by `;` or new lines; the group is its name or id from the URL, e.g. `zzter=Тернопіль` |
| `FACEBOOK_USER_AGENT` | a desktop Chrome | same | the user agent of the browser the cookies were copied from |
| `FACEBOOK_POLL_EVERY` / `FACEBOOK_POLL_JITTER` | `PT1H` / `PT20M` | same | the groups are read every `POLL_EVERY` plus a random part of `POLL_JITTER`; the first poll comes a minute after the start |
| `FACEBOOK_REQUEST_PAUSE` | `PT10S` | same | a random 0.5–1.5 × this pause between two requests to Facebook |
| `FACEBOOK_MAX_PAGES` | `10` | same | feed pages (three posts each) read per group and poll at most |
| `FACEBOOK_CATCH_UP_MAX_AGE` | `PT48H` | same | how far back the first poll after a start reads |
| `FACEBOOK_TIMEOUT` | `PT30S` | same | per request to Facebook |
| `FACEBOOK_ACTIVE_HOURS` | empty (all day) | `07:00-22:00` | Facebook is read only within these hours, in each group's local time (the time zone of its region); outside them no request is made and the next poll waits for the start plus up to `FACEBOOK_POLL_JITTER`. A range may cross midnight (`22:00-02:00`); a malformed value fails the start |
| `TELEGRAM_BOT_API_URL` | `https://api.telegram.org` | same | Bot API base URL |
| `TELEGRAM_BOT_TOKEN` | empty | same | the bot that posts to our channel and to the review chat |
| `TELEGRAM_PUBLISH_CHAT` | empty | same | our public channel: id (`-100...`) or `@username` |
| `TELEGRAM_REVIEW_CHAT` | empty | same | private chat or group for previews |
| `TELEGRAM_PUBLISH_LOCALE` | `uk` | same | language of the captions in the public channel |
| `TELEGRAM_BOT_TIMEOUT` / `TELEGRAM_BOT_MAX_ATTEMPTS` / `TELEGRAM_BOT_RETRY_DELAY` | `PT30S` / `3` / `PT3S` | same | Bot API timeout, attempts and the first back-off (doubled each retry); retried only when the message certainly did not arrive |
| `OPENROUTER_URL` | `https://openrouter.ai/api/v1` | same | OpenRouter API base URL |
| `OPENROUTER_API_KEY` | empty | same | OpenRouter key |
| `OPENROUTER_MODEL` | `google/gemini-3.1-flash-lite` | same | extraction model |
| `OPENROUTER_FALLBACK_MODEL` | `qwen/qwen3.5-flash-02-23` | same | sent after `OPENROUTER_MODEL` in OpenRouter's `models` list; empty for none |
| `OPENROUTER_ROUTING` | `true` | same | OpenRouter-only request fields (`models`, `provider.require_parameters`); `false` for another OpenAI-compatible API under `OPENROUTER_URL`, which rejects them with 400. The fallback model is then not sent |
| `OPENROUTER_TIMEOUT` / `OPENROUTER_MAX_ATTEMPTS` / `OPENROUTER_RETRY_DELAY` | `PT60S` / `3` / `PT1S` | same | model call timeout, attempts and the first back-off |
| `OPENROUTER_DAILY_LIMIT` | `300` | same | model calls per UTC day, a safety net; a post over it is postponed to the next UTC day without a ledger row (`budget`); this counts toward the 3 queue retries of an unavailable stage |
| `GOOGLE_PLACES_API` | `LEGACY` | same | `LEGACY`: Places API text search (`textsearch/json`); `NEW`: Places API (New) `places:searchText`. Switching is only this variable |
| `GOOGLE_PLACES_URL` | empty | same | base URL; empty takes the chosen API's own (`https://maps.googleapis.com/maps/api/place` or `https://places.googleapis.com/v1`) |
| `GOOGLE_PLACES_API_KEY` | empty | same | a server key restricted by IP, not the admin panel's browser key (`GOOGLE_MAPS_API_KEY`) |
| `GOOGLE_PLACES_TIMEOUT` / `GOOGLE_PLACES_MAX_ATTEMPTS` / `GOOGLE_PLACES_RETRY_DELAY` | `PT10S` / `3` / `PT1S` | same | geocoding timeout, attempts and the first back-off |
| `DERECHI_API_URI` | `http://localhost:8080` | `http://caddy:8080` | the API entry (Caddy); notices go to its `/graphql`. On a machine without it, its public address |
| `DERECHI_API_TIMEOUT` / `DERECHI_API_MAX_ATTEMPTS` / `DERECHI_API_RETRY_DELAY` | `PT15S` / `3` / `PT1S` | same | API timeout, attempts and the first back-off; creating a notice is retried only when the request certainly did not arrive |
| `DERECHI_API_REFERENCE_TTL` | `1h` | same | how long categories and countries from the API are cached; Caffeine spec format (`30m`, `1h`, `1d`) |
| `MINIO_ENDPOINT`, `MINIO_REGION`, `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | as in Admin-API | as in Admin-API | S3 client for the photos (the application's account) |
| `MINIO_BUCKET` | `derechi-files` | same | bucket the photos go to |
| `MINIO_MAX_UPLOAD` | `5MB` | same | largest photo uploaded |
| `TESSDATA_PREFIX` | empty, OCR off (development only) | set by the image: `/usr/share/tesseract-ocr/5/tessdata` | Tesseract's `*.traineddata` directory; not in compose |
| `REDACTION_OCR_LANGUAGES` | `ukr+eng` | same | Tesseract languages; each needs its `tesseract-ocr-<lang>` package in the `ingest` stage of the `Dockerfile`, or the start fails. Polish (`pol`) is not installed yet |
| `REDACTION_DOCUMENT_KEYWORDS` | `паспорт,passport,...` (comma-separated, see `application.yaml`) | same list | words that mark a document, in the post text or on the photo, case-insensitive |
| `REDACTION_FACE_MODEL` | `classpath:models/face_detection_yunet_2023mar.onnx` | same | YuNet ONNX model, a Spring resource location |
| `REDACTION_FACE_SCORE` | `0.6` | same | minimum face score |
| `REDACTION_DOCUMENT_MODE` | `PARTIAL` | same | `PARTIAL`: blur a document except its faces (no face found: blur it entirely); `FULL`: blur every document photo entirely |

The first start needs the Telegram session: stop the service and run
`docker compose -f docker-compose.yml -f docker-compose.services.yml run --rm -it ingest --login`
(add `--env-file`), which asks for the code Telegram sends to `TELEGRAM_PHONE`.

## Infrastructure containers

| Container | Reads |
|---|---|
| postgres | `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`; `initdb/01-keycloak.sh` creates the `keycloak` database owned by `POSTGRES_USER` |
| rabbitmq | `RABBITMQ_USER`, `RABBITMQ_PASSWORD` as `RABBITMQ_DEFAULT_USER` / `_PASS` |
| rabbitmq-init | `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`; imports `docker/rabbitmq/definitions.json` |
| keycloak | `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_JAVA_OPTS`, and `POSTGRES_HOST` / `_PORT` / `_USER` / `_PASSWORD` for its `keycloak` database; `DERECHI_SITE_URL` (`http://localhost:3000`), `DERECHI_ADMIN_URL` (`http://localhost:8083`) and `KEYCLOAK_SERVICES_SECRET` (`dev-secret-change-me`) fill the placeholders of the realm file on import; full mode adds `KEYCLOAK_PUBLIC_URI` as `KC_HOSTNAME` (in both modes) |
| minio | `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD` (`derechi` / `derechi123`) as the root user, also the console login |
| minio-init | `MINIO_ENDPOINT`, `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`; creates the bucket and the application account `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` with `docker/minio/derechi-app-policy.json` |
| alertmanager | `ALERT_EMAIL_TO`, `ALERT_SMTP_HOST` (`mailpit:1025`), `ALERT_SMTP_FROM`, `ALERT_SMTP_USERNAME`, `ALERT_SMTP_PASSWORD`, `ALERT_SMTP_REQUIRE_TLS` (`false`; set `true` in `env.prod`); rendered into `docker/alertmanager/alertmanager.yml.tmpl` at start, see [monitoring](monitoring.md#alerts) |
| grafana | `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` |
| pgadmin | `PGADMIN_DEFAULT_EMAIL`, `PGADMIN_DEFAULT_PASSWORD`; started only with the `dev` profile |
| mailpit | nothing environment-specific; started only with the `dev` profile |
| caddy | `MINIO_URI`, `MINIO_BUCKET`; `CLIENT_API_URI` only with `docker-compose.services.yml`, see [Caddy](#caddy-api-entry) |

Every long-running container has a memory limit, overridable per environment:
`POSTGRES_MEMORY` (1g), `PGADMIN_MEMORY` (512m), `RABBITMQ_MEMORY` (512m), `KEYCLOAK_MEMORY`
(768m), `PROMETHEUS_MEMORY` (512m), `ALERTMANAGER_MEMORY` (128m), `GRAFANA_MEMORY` (256m),
`MINIO_MEMORY` (512m), `MAILPIT_MEMORY` (128m), `CADDY_MEMORY` (128m), and `SERVICE_MEMORY` (512m) for each Spring
service except Ingest, which has `INGEST_MEMORY` (1g). See [docker-compose](docker-compose.md#healthchecks-restarts-and-memory).
