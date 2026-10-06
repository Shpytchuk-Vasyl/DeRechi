# Configuration

Each module has exactly one `src/main/resources/application.yaml`. Its defaults describe the **developer's machine**: everything points at `localhost`, infrastructure runs in Docker, services run in IDEA. Every value that differs between environments is a placeholder, and containers set the variables. The full list of those variables is in [environment-variables](../deployment/environment-variables.md).

## Rule 1: `application.yaml` is the dev profile

Keep `localhost` as the default of every address in the YAML. Do not change the defaults to container names to "make Docker work"; `docker-compose.services.yml` sets the variables:

```yaml
# application.yaml, in every module that needs a database
spring:
  datasource:
    url: jdbc:postgresql://${POSTGRES_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB:derechi}
    username: ${POSTGRES_USER:derechi}
    password: ${POSTGRES_PASSWORD:derechi}

# docker-compose.services.yml
x-postgres: &postgres
  POSTGRES_HOST: ${POSTGRES_HOST:-postgres}
  POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-derechi}
```

PostgreSQL and RabbitMQ use the same variable names in every module (`POSTGRES_*`, `RABBITMQ_*`), and the PostgreSQL and RabbitMQ containers are started with the same ones, so a credential is set in one place for both sides. Relaxed binding still works on top (`SPRING_DATASOURCE_URL` beats the YAML), but compose does not use it: set the variable, not the property.

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

The two syntaxes differ by one character. Spring puts the default after a colon, `${NAME:default}`; compose after a colon and a dash, `${NAME:-default}`. Written the compose way in a YAML file, `${NAME:-x}` gives the default `-x`.

In compose, the default of a variable is the **container** view of the local stack (`postgres`, `rabbitmq`, `client-api`), and it must equal the YAML default wherever the two views agree. Never give compose an empty default for a value the YAML defaults to something else: an empty string is a value, and it replaces the YAML default. When a variable must reach the container only if it is set, list it without a value (`GOOGLE_MAPS_API_KEY:` in `admin-api`).

The placeholders of each module are listed in [environment-variables](../deployment/environment-variables.md).

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
| `derechi.claims` | `ClaimsProperties` (`exchange`, `unlock-limit`, `unlock-window`) | Client-API |
| `derechi.fourthwall` | `FourthwallProperties` (`api-url`, `shop-url`, `username`, `password`, `webhook-secret`, `price`, `product-name`, `product-description`, `connect-timeout` 3s, `read-timeout` 10s) | Client-API |
| `derechi.items.queue` | read with `${...}` in `@RabbitListener` | Worker |
| `derechi.notification.queue` | read with `${...}` in `@RabbitListener` | Notification |

`derechi.countries` is declared in both `Admin-API` and `Client-API` and must list the same countries; see [add-a-country](../extending/add-a-country.md).

## Rule 5: Gateway routes are variables

Every route in `Getaway/src/main/resources/application.yaml` targets a placeholder whose default is the local address: `${CLIENT_API_URI:http://localhost:8082}`, `${MINIO_URI:http://localhost:9000}`. There is no Keycloak route. Compose sets the container addresses. Never write a bare `localhost:<port>` into a route; a new route gets its own `*_URI` variable. There is no service registry (Eureka was removed): on one compose host Docker's DNS resolves the container name, and several instances of a service would sit behind one load-balanced address. Planned: bringing service discovery back.

## Rule 6: every service is scraped

A new service exposes `/actuator/prometheus` (it gets actuator and the Prometheus registry from the root POM) on a separate management port, `management.server.port: ${MANAGEMENT_PORT:<service port + 1000>}`, with `management.endpoint.health.show-details: never`. Compose does not publish that port. The service must be added to **both** `docker/prometheus/prometheus.yml` (`host.docker.internal:<management port>`) and `docker/prometheus/prometheus-full.yml` (`<service>:<management port>`) with an `application` label. See [monitoring](../deployment/monitoring.md).

## Rule 7: no secrets in the repository

Dev credentials for local Docker containers (`derechi`/`derechi`, `admin`/`admin`) are fine as defaults in YAML and compose; they never leave the developer's machine. Anything that is a real credential for an external service goes into an environment variable without a committed default.

The values of an environment live in one file at the repository root, read by compose with `--env-file`: `env.local`, `env.test`, `env.prod`. `env.local` is committed and holds dev values only; `env.test` and `env.prod` are git-ignored, because they hold that environment's credentials. The variable list they follow is in [environment-variables](../deployment/environment-variables.md).

Known issue: `Admin-API/src/main/resources/application.yaml` ships a Google Maps API key as the default of `GOOGLE_MAPS_API_KEY`. It should be rotated and the default removed so the key is supplied only through the environment. Until that is done, do not copy the pattern.

## Where things are set, by module

| Module | Port | Management port | Notable settings |
|---|---|---|---|
| Getaway | 8080 | 9080 | routes, global CORS from `WEB_ORIGIN_PATTERNS`, `files` and `files-upload` routes to MinIO |
| Client-API | 8082 | 9082 | GraphQL schema location, GraphiQL enabled, `derechi.countries`, `derechi.claims`, `derechi.fourthwall` |
| Admin-API | 8083 | 9083 | OIDC client `derechi-admin`, Caffeine cache `categories`, static resource content hashing, multipart 5 MB, S3 client for MinIO, `derechi.*` |
| Worker | 8085 | 9085 | listener retry `max-attempts: 3`, `default-requeue-rejected: false`, queue name, ShedLock on the claims job |
| Notification | 8084 | 9084 | same listener retry, `notify.*` NotifyHub config, queue name |

All servlet modules set `spring.threads.virtual.enabled: true`, `spring.jpa.open-in-view: false` and `ddl-auto: none`. Copy those three lines into any new module.
