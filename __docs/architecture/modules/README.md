# Modules

One page per deployable unit, plus the two things that are not services: the schema
library and the web client.

| Module | Port | Role | Stack | Talks to |
|---|---|---|---|---|
| [DB-Postgres](db-postgres.md) | none | owns the schema and migrations; a one-shot container that migrates and exits | JPA, Liquibase | PostgreSQL |
| [Getaway](getaway.md) | 8080 | public entry point, routes to Client-API and MinIO; **not deployed for now, replaced by Caddy** | Spring Cloud Gateway (WebFlux) | Client-API, MinIO |
| [Client-API](client-api.md) | 8082 | public GraphQL API for the web client | Spring MVC, Spring for GraphQL, JPA | PostgreSQL, RabbitMQ (publishes) |
| [Admin-API](admin-api.md) | 8083 | administration UI under `/admin/**` | Spring MVC, Thymeleaf, htmx, Spring Security OAuth2 client | PostgreSQL, RabbitMQ (publishes), MinIO, Keycloak |
| [Worker](worker.md) | 8085 | background worker: finds candidate matches for every new notice | Spring AMQP, JPA, PostGIS, full-text search | PostgreSQL, RabbitMQ (consumes) |
| [Notification](notification.md) | 8084 | background worker: delivers notifications through NotifyHub | Spring AMQP, NotifyHub | RabbitMQ (consumes), SMTP (Mailpit) |
| [Ingest](ingest.md) | 8086 | imports notices from outside sources (Telegram today) and publishes them to the site, our channel and the review chat | TDLight, Tesseract, ONNX Runtime, OpenRouter, Spring for GraphQL client | Telegram, OpenRouter, Google Places, Caddy (API entry), MinIO, PostgreSQL |
| [Web-Client](web-client.md) | 3000 | public website, not a Maven module | Next.js, pnpm | Caddy (API entry: GraphQL, files) |

Ports are the `server.port` values in each `application.yaml` and are the same inside
Compose; `docker-compose.services.yml` maps them one to one onto the host. Actuator of
each service listens on a separate management port, the service port + 1000 (9080, 9082,
9083, 9084, 9085, 9086; `MANAGEMENT_PORT`), which Compose does not publish; see
[../observability.md](../observability.md).

For now port 8080 belongs to Caddy, a container from `docker-compose.yml`, not to
`Getaway`: see [Caddy (API entry)](../../deployment/infrastructure.md#caddy-api-entry).

## Start order

None among the services. Infrastructure (PostgreSQL, RabbitMQ, Keycloak, MinIO, Mailpit,
Caddy) comes before all of them; `docker compose up -d` handles that. Caddy forwards
`/graphql` to `CLIENT_API_URI` and answers with a 502 for those paths until `Client-API` is
up; nothing else depends on another service being started first.
`Admin-API` additionally needs Keycloak to answer its issuer URL at startup.

The one exception is `DB-Postgres`. Its `db-postgres` container runs after PostgreSQL is
healthy, applies pending migrations and exits. `Client-API`, `Admin-API` and `Worker` start
only after it exits with 0 (`condition: service_completed_successfully`); see
[db-postgres.md](db-postgres.md).

## Shape of a service module

Each service follows the same layout, so a reader who knows one can find their way in the
others:

```
<Module>/
├── pom.xml                                  # dependencies only; versions from the root
└── src/main/
    ├── java/org/shpytchuk/<module>/
    │   ├── <Module>Application.java
    │   ├── config/                          # @Configuration, @ConfigurationProperties
    │   ├── entity/                          # a copy of the JPA entities this service reads
    │   ├── repository/
    │   ├── service/
    │   ├── controller/  or  listener/       # HTTP in, or RabbitMQ in
    │   └── event/                           # @EventType, EventTypeScanner, event classes
    └── resources/application.yaml           # localhost defaults; env vars override in Docker
```

`Getaway` has only an application class, an access-log config and a YAML file.
