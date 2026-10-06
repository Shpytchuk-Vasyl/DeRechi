# Local development

Infrastructure in Docker, services on the host. This is the setup everyone uses day to day; the onboarding walkthrough with more context is in [getting-started](../developer-guide/getting-started.md).

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 26 | the reactor compiles with `--release 26` |
| Docker with Compose v2 | recent | `docker compose`, not `docker-compose` |
| pnpm | 9+ | only for `Web-Client` |
| IntelliJ IDEA | any recent | optional, Maven works from the shell |

Maven itself is not required; use the wrapper in the repository root (`./mvnw` or `mvnw.cmd`).

## 1. Start the infrastructure

```bash
docker compose --profile dev up -d
```

`--profile dev` adds Mailpit and pgAdmin, which production does not run; `--env-file env.local` does the same through `COMPOSE_PROFILES=dev`.

This builds the custom PostGIS image on first run (it downloads the Ukrainian hunspell dictionary), then starts PostgreSQL, RabbitMQ, Keycloak, MinIO (plus the `minio-init` job that creates the `derechi-files` bucket and the `rabbitmq-init` job that imports the queues), Mailpit, pgAdmin, Prometheus, Alertmanager and Grafana. Wait until `docker compose ps` shows `healthy` for PostgreSQL, RabbitMQ, MinIO and Mailpit and `running` for the rest. Keycloak takes the longest; it is ready when `http://localhost:8180` answers.

## 2. Create the schema

The services never run migrations. Apply the changelog once, and again whenever a new changeset lands:

```bash
./mvnw -pl DB-Postgres liquibase:update
```

If your JVM's default timezone is `Europe/Kiev`, this fails with `invalid value for parameter "TimeZone"`. Set `MAVEN_OPTS=-Duser.timezone=Europe/Kyiv` (and the same VM option in IDEA run configurations).

## 3. Start the services

In any order; the Gateway answers `/graphql` once `Client-API` is up.

```bash
./mvnw -pl Getaway spring-boot:run
./mvnw -pl Client-API spring-boot:run
./mvnw -pl Admin-API spring-boot:run
./mvnw -pl Worker spring-boot:run
./mvnw -pl Notification spring-boot:run
```

Start only what you need: `Admin-API` alone is enough for admin UI work (it needs PostgreSQL, Keycloak, MinIO and RabbitMQ from compose). `Worker` and `Notification` are only needed when you exercise the event flows.

Actuator is on a separate management port, the service port + 1000: `http://localhost:9080/actuator/health` for the Gateway, `9082`, `9083`, `9084`, `9085` for the others. The service ports themselves do not serve `/actuator`.

In IDEA, create a Spring Boot run configuration per `*Application` class, or a compound configuration that starts them together. The `Launcher` module exists to start several contexts in one JVM for manual testing but is currently commented out of the reactor; see [launcher](../architecture/modules/launcher.md).

## 4. Open things

| What | URL | Login |
|---|---|---|
| Admin panel | `http://localhost:8083/admin` | Keycloak user, see below |
| GraphiQL through the gateway | `http://localhost:8080/graphiql` | none |
| GraphQL endpoint | `http://localhost:8080/graphql` | none |
| Keycloak console | `http://localhost:8180` | `admin` / `admin` |
| RabbitMQ management | `http://localhost:15672` | `derechi` / `derechi` |
| Mailpit (outgoing mail) | `http://localhost:8025` | none |
| pgAdmin | `http://localhost:5050` | `admin@derechi.local` / `admin` |
| MinIO console | `http://localhost:9001` | `derechi` / `derechi123` (the root account) |
| Prometheus | `http://localhost:9090` | none |
| Alertmanager | `http://localhost:9093` | none |
| Grafana | `http://localhost:3000` | `admin` / `admin` |

Dev users in the `derechi` realm, imported from `docker/keycloak/dev/derechi-users-0.json`. Its volume line is commented out in `docker-compose.yml`; uncomment it locally before the first start and do not commit it (see [keycloak](keycloak.md#dev-users)):

| User | Password | Realm roles |
|---|---|---|
| `admin@derechi.local` | `admin` | `ADMIN_SUPER`, `USER` |
| `moderator@derechi.local` | `moderator` | `ADMIN_EDITOR`, `USER` |
| `viewer@derechi.local` | `viewer` | `ADMIN_VIEWER`, `USER` |
| `user@derechi.local` | `user` | `USER` (no admin access) |

## Web client

```bash
cd Web-Client
pnpm install
pnpm dev        # http://localhost:3000
```

It calls `Client-API` through the Gateway (`GRAPHQL_URL`) and loads images from the Gateway's `/files/**` route; both default to `localhost:8080`. See `Web-Client/README.md`.

## Everything in Docker instead

```bash
docker compose --profile dev -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

With personal keys (the Fourthwall API user, a Google Maps key) in `env.local`, pass it to compose:

```bash
docker compose --env-file env.local -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

Builds all service images and runs them in the same project. Use it to verify a change in a production-like topology (container names, no `localhost`); for daily work it is slower than running from IDEA. Details in [docker-compose](docker-compose.md).

## Stopping and resetting

```bash
docker compose stop          # keep data
docker compose down          # remove containers, keep volumes
docker compose down -v       # remove volumes too: database, Keycloak realm state, MinIO objects, queues
```

After `down -v` the realm is re-imported from `docker/keycloak/realms/derechi-realm.json` (and the dev users from `docker/keycloak/dev`, if their volume line is uncommented) on the next start and the schema must be re-created with `liquibase:update`.
