# Modules

One page per deployable unit, plus the two things that are not services: the schema
library and the web client.

| Module | Port | Role | Stack | Talks to |
|---|---|---|---|---|
| [DB-Postgres](db-postgres.md) | none | owns the schema and migrations; a library, run by hand | JPA, Liquibase | PostgreSQL |
| [Getaway](getaway.md) | 8080 | single public entry point, routes to Client-API and MinIO | Spring Cloud Gateway (WebFlux) | Client-API, MinIO |
| [Client-API](client-api.md) | 8082 | public GraphQL API for the web client | Spring MVC, Spring for GraphQL, JPA | PostgreSQL, RabbitMQ (publishes) |
| [Admin-API](admin-api.md) | 8083 | administration UI under `/admin/**` | Spring MVC, Thymeleaf, htmx, Spring Security OAuth2 client | PostgreSQL, RabbitMQ (publishes), MinIO, Keycloak |
| [Worker](worker.md) | 8085 | background worker: finds candidate matches for every new notice | Spring AMQP, JPA, PostGIS, full-text search | PostgreSQL, RabbitMQ (consumes) |
| [Notification](notification.md) | 8084 | background worker: delivers notifications through NotifyHub | Spring AMQP, NotifyHub | RabbitMQ (consumes), SMTP (Mailpit) |
| [Launcher](launcher.md) | none | starts several services in one JVM for local development; currently disabled | | |
| [Web-Client](web-client.md) | 3000 | public website, not a Maven module | Next.js, pnpm | Getaway (GraphQL, files) |

Ports are the `server.port` values in each `application.yaml` and are the same inside
Compose; `docker-compose.services.yml` maps them one to one onto the host. Actuator of
each service listens on a separate management port, the service port + 1000 (9080, 9082,
9083, 9084, 9085; `MANAGEMENT_PORT`), which Compose does not publish; see
[../observability.md](../observability.md).

## Start order

None among the services. Infrastructure (PostgreSQL, RabbitMQ, Keycloak, MinIO, Mailpit)
comes before all of them; `docker compose up -d` handles that. The gateway forwards
`/graphql` to `CLIENT_API_URI` and answers with a 5xx for those paths until `Client-API` is
up; nothing else depends on another service being started first.
`Admin-API` additionally needs Keycloak to answer its issuer URL at startup.

`DB-Postgres` is not in the order because it is not a service: run it once to migrate, see
[../../processes/database-changes.md](../../processes/database-changes.md).

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

`Getaway` has only an application class and a YAML file.
