# Modules

One page per deployable unit, plus the two things that are not services: the schema
library and the web client.

| Module | Port | Role | Stack | Talks to |
|---|---|---|---|---|
| [DB-Postgres](db-postgres.md) | none | owns the schema and migrations; a library, run by hand | JPA, Liquibase | PostgreSQL |
| [Discovery](discovery.md) | 8761 | Eureka server | Spring Cloud Netflix | nothing |
| [Getaway](getaway.md) | 8080 | single public entry point, routes to Client-API, Keycloak and MinIO | Spring Cloud Gateway (WebFlux) | Eureka, Keycloak, MinIO |
| [Client-API](client-api.md) | 8082 | public GraphQL API for the web client | Spring MVC, Spring for GraphQL, JPA | PostgreSQL, RabbitMQ (publishes), Eureka |
| [Admin-API](admin-api.md) | 8083 | administration UI under `/admin/**` | Spring MVC, Thymeleaf, htmx, Spring Security OAuth2 client | PostgreSQL, RabbitMQ (publishes), MinIO, Keycloak, Eureka |
| [Worker](worker.md) | 8085 | background worker: finds candidate matches for every new notice | Spring AMQP, JPA, PostGIS, full-text search | PostgreSQL, RabbitMQ (consumes), Eureka |
| [Notification](notification.md) | 8084 | background worker: delivers notifications through NotifyHub | Spring AMQP, NotifyHub | RabbitMQ (consumes), SMTP (Mailpit), Eureka |
| [Launcher](launcher.md) | none | starts several services in one JVM for local development; currently disabled | | |
| [Web-Client](web-client.md) | 3000 | public website, not a Maven module | Next.js, pnpm | Getaway (GraphQL, files) |

Ports are the `server.port` values in each `application.yaml` and are the same inside
Compose; `docker-compose.services.yml` maps them one to one onto the host.

## Start order

`Discovery` first. Every other service is a Eureka client and registers on startup; they
retry if the registry is not there yet, but the gateway cannot route `lb://CLIENT-API`
until both the registry and `Client-API` are up. Infrastructure (PostgreSQL, RabbitMQ,
Keycloak, MinIO, Mailpit) comes before all of them; `docker compose up -d` handles that.
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

`Discovery` and `Getaway` have only an application class and a YAML file.
