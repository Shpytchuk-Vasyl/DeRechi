# Configuration

Each module has exactly one `src/main/resources/application.yaml`. It describes the **developer's machine**: everything points at `localhost`, infrastructure runs in Docker, services run in IDEA. Containers get the same file and override the few values that differ through environment variables. The full list of those variables is in [environment-variables](../deployment/environment-variables.md).

## Rule 1: `application.yaml` is the dev profile

Keep `localhost:5432`, `localhost:5672`, `localhost:8761`, `localhost:8180` and `localhost:9000` in the YAML. Do not change them to container names to "make Docker work"; `docker-compose.services.yml` already overrides them:

```yaml
environment:
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/derechi
  SPRING_RABBITMQ_HOST: rabbitmq
  EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE: http://discovery:8761/eureka
```

Spring's relaxed binding maps `SPRING_DATASOURCE_URL` to `spring.datasource.url`, so any Spring property can be overridden this way without touching the YAML. Values that are not Spring properties (`KEYCLOAK_URI`, `MINIO_ENDPOINT`, `NOTIFY_EMAIL_HOST`) are referenced explicitly with a placeholder.

## Rule 2: placeholders carry the dev default

When a value must differ between dev and containers and is not a plain Spring property, write it as `${ENV_NAME:dev-default}`:

```yaml
derechi:
  storage:
    public-url: ${MINIO_PUBLIC_URL:http://localhost:9000/${derechi.storage.bucket}}
notify:
  channels:
    email:
      host: ${NOTIFY_EMAIL_HOST:localhost}
```

The default is what IDEA uses; compose sets the variable. Never leave a placeholder without a default unless the application genuinely cannot start without it, because every developer would then have to set it.

Existing placeholders: `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_ENDPOINT`, `MINIO_PUBLIC_URL`, `GOOGLE_MAPS_API_KEY`, `KEYCLOAK_URI`, `KEYCLOAK_PUBLIC_URI` (Admin-API); `KEYCLOAK_URI`, `MINIO_URI`, `MINIO_BUCKET`, `MINIO_MAX_UPLOAD`, `WEB_ORIGIN_PATTERNS` (Getaway); `NOTIFY_EMAIL_HOST`, `NOTIFY_EMAIL_PORT`, `NOTIFY_EMAIL_USERNAME`, `NOTIFY_EMAIL_PASSWORD` (Notification).

## Rule 3: profiles only when a placeholder is not enough

The only Spring profile in use is `docker`, activated for `Admin-API` by `SPRING_PROFILES_ACTIVE=docker` in compose. It exists because the OIDC login needs **two** Keycloak addresses in containers: the browser is redirected to `http://localhost:8180` (`authorization-uri`), while the token, JWKS and user-info calls go to `http://keycloak:8080` inside the Docker network. With a single `issuer-uri` Spring would derive all four from one host, so the `docker` profile spells them out and the `!docker` profile keeps the short `issuer-uri`.

Before adding another profile, check whether a placeholder with a default would do. Profiles multiply the combinations that have to be tested.

## Rule 4: typed properties under `derechi.*`

Application-specific settings live under the `derechi` prefix and are bound to a record (see [java-code-style](java-code-style.md#configuration-properties)):

| Prefix | Record | Module |
|---|---|---|
| `derechi.admin` | `AdminProperties` (`client-id`, `page-size`) | Admin-API |
| `derechi.countries` | `CountriesProperties` (`supported`, `fallback`) | Admin-API, Client-API |
| `derechi.storage` | `StorageProperties` (`bucket`, `public-url`, `max-size`) | Admin-API |
| `derechi.maps` | `MapsProperties` (`api-key`, `region`) | Admin-API |
| `derechi.notifications` | `NotificationProperties` (`exchange`, `routing-key`) | Admin-API |
| `derechi.items.queue` | read with `${...}` in `@RabbitListener` | Automatic-Search |
| `derechi.notification.queue` | read with `${...}` in `@RabbitListener` | Notification |

`derechi.countries` is declared in both `Admin-API` and `Client-API` and must list the same countries; see [add-a-country](../extending/add-a-country.md).

## Rule 5: the Gateway knows service names, not ports

Routes in `Getaway/src/main/resources/application.yaml` use `lb://CLIENT-API` and resolve the instance through Eureka. Never put `localhost:8082` into a route. The two exceptions are things that are not Eureka clients: Keycloak (`${KEYCLOAK_URI}`) and MinIO (`${MINIO_URI}`), which are addressed directly.

## Rule 6: every service is scraped

A new service exposes `/actuator/prometheus` (it gets actuator and the Prometheus registry from the root POM) and must be added to **both** `docker/prometheus/prometheus.yml` and `docker/prometheus/prometheus-full.yml` with an `application` label. See [monitoring](../deployment/monitoring.md).

## Rule 7: no secrets in the repository

Dev credentials for local Docker containers (`derechi`/`derechi`, `admin`/`admin`) are fine in YAML and compose; they never leave the developer's machine. Anything that is a real credential for an external service goes into an environment variable without a committed default.

Known issue: `Admin-API/src/main/resources/application.yaml` ships a Google Maps API key as the default of `GOOGLE_MAPS_API_KEY`. It should be rotated and the default removed so the key is supplied only through the environment. Until that is done, do not copy the pattern.

## Where things are set, by module

| Module | Port | Notable settings |
|---|---|---|
| Discovery | 8761 | `register-with-eureka: false`, `enable-self-preservation: false` (dev instances come and go) |
| Getaway | 8080 | routes, global CORS from `WEB_ORIGIN_PATTERNS`, `files` and `files-upload` routes to MinIO |
| Client-API | 8082 | GraphQL schema location, GraphiQL enabled, `derechi.countries` |
| Admin-API | 8083 | OIDC client `derechi-admin`, Caffeine cache `categories`, static resource content hashing, multipart 5 MB, S3 client for MinIO, `derechi.*` |
| Automatic-Search | 8085 | listener retry `max-attempts: 3`, `default-requeue-rejected: false`, queue name |
| Notification | 8084 | same listener retry, `notify.*` NotifyHub config, queue name |

All servlet modules set `spring.threads.virtual.enabled: true`, `spring.jpa.open-in-view: false` and `ddl-auto: none`. Copy those three lines into any new module.
