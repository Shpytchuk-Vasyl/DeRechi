# Architecture

DeRechi is a lost-and-found board: people post notices about things they lost or found,
the system looks for likely matches between the two kinds of notice, and an administrator
can tell the owner of a lost item that something similar was found.

Technically it is a small set of Spring Boot services behind a gateway, sharing one
PostgreSQL database and talking to each other through RabbitMQ. Everything that is not
business logic (identity, file storage, mail, metrics) is an off-the-shelf container.

## Components

```
                 ┌──────────────┐          ┌─────────────────────┐
  public user ──▶│  Web-Client  │──GraphQL▶│  Getaway (8080)     │
  (browser)      │  Next.js     │          │  Spring Cloud GW    │
                 └──────────────┘          └──────┬───────┬──────┘
                       │ presigned PUT            │       │ /realms/**
                       ▼                      lb://      ▼
                 ┌──────────────┐          ┌──────────┐ ┌──────────────┐
                 │  MinIO (S3)  │◀─upload──│Client-API│ │ Keycloak     │
                 │  derechi-    │          │  (8082)  │ │ (8180)       │
                 │  files       │          └────┬─────┘ └──────▲───────┘
                 └──────▲───────┘               │ item.*.created      │ OIDC login
                        │                       ▼                     │
                        │                ┌─────────────┐        ┌─────┴────────┐
  administrator ────────┼───────────────▶│  RabbitMQ   │        │  Admin-API   │◀── administrator
  (browser, 8083)       │                │ derechi.*   │◀───────│  (8083)      │    (browser)
                        │                └──┬──────┬───┘ notif. │ MVC+Thymeleaf│
                        │                   │      │            └──────┬───────┘
                        │     item.*.created│      │notification.#     │
                        │                   ▼      ▼                   │
                        │     ┌──────────────────┐ ┌──────────────┐    │
                        │     │ Worker │ │ Notification │    │
                        │     │ (8085)           │ │ (8084)       │    │
                        │     └────────┬─────────┘ └──────┬───────┘    │
                        │              │ similar_item     │ SMTP       │
                        │              ▼                  ▼            │
                        │     ┌──────────────────┐ ┌──────────────┐    │
                        └─────│ PostgreSQL 17    │ │ Mailpit      │    │
                              │ + PostGIS        │ │ (1025/8025)  │    │
                              │ derechi, keycloak│ └──────────────┘    │
                              └──────────────────┘                     │
                                       ▲                               │
                                       └───────────────────────────────┘

  Discovery (8761, Eureka) registers every service; Prometheus (9090) scrapes
  /actuator/prometheus on each of them; Grafana (3000) reads Prometheus.
```

Two things in the picture are deliberate and worth knowing before reading further:

- **One database.** All services read and write the same `derechi` database. The schema is
  owned by a single library module, `DB-Postgres`, and every service carries its own copy of
  the entity classes. See [database.md](database.md).
- **The admin panel is not behind the gateway.** `Admin-API` serves HTML on its own port and
  logs in against Keycloak directly. The gateway exists for the public GraphQL API and for
  file traffic only. See [modules/getaway.md](modules/getaway.md).

## The two main flows

### A notice is created

1. A user fills in the form in `Web-Client`. The photo is uploaded straight to MinIO through
   the gateway with a presigned URL; only the object key reaches the API.
2. `Web-Client` calls `createLostItem` or `createFoundItem` on `Client-API` through the
   gateway (`/graphql`). `Client-API` validates the input, resolves the place's country and
   the reward currency, and saves `place`, `contact_info` and the item.
3. An aspect around `ItemService.create` publishes an `ItemCreatedEvent` to the
   `derechi.items` exchange with the routing key `item.lost.created` or `item.found.created`.
4. `Worker` consumes it from `worker.items`, looks for notices of the
   opposite kind in the same category, within three days and twenty kilometres, ranks them by
   full-text similarity of the title, and stores the top five in `similar_item`.

### A match is notified

1. An administrator opens `/admin/matches` in `Admin-API`. Each lost item is shown with its
   candidate found items from `similar_item`, best match first.
2. The administrator presses "Notify" on a candidate and picks a channel (all, email, phone,
   or a messenger). `MatchNotificationService` builds a localized `NotificationRequestedEvent`
   with only the contacts that channel needs, publishes it to `derechi.notifications` with
   the key `notification.match.found`, and stamps `notified_at` / `notified_by` on the row.
3. `Notification` consumes it from `notification.events` and hands it to NotifyHub, which
   sends an email (Mailpit in development) and an SMS when a phone is present. Delivery
   failures are retried three times and then parked in `notification.events.dlq`.

## Pages

- [tech-stack.md](tech-stack.md): versions and the libraries we lean on, with the reasons.
- [maven-reactor.md](maven-reactor.md): how the multi-module build is wired and what a module may and may not declare.
- [database.md](database.md): the shared schema, who owns it, and the tables.
- [messaging.md](messaging.md): RabbitMQ exchanges, queues, routing keys and the event classes.
- [authentication.md](authentication.md): Keycloak, the realm, clients, roles and how they reach Spring Security.
- [file-storage.md](file-storage.md): MinIO, the two upload paths and the public read path.
- [observability.md](observability.md): actuator, Prometheus jobs and Grafana.
- [modules/](modules/README.md): one page per module, including the ones that are not Maven modules.

Related: [conventions](../conventions/README.md) for how we write things,
[features](../features/README.md) for what the system does from a user's point of view,
[deployment](../deployment/README.md) for how it runs.
