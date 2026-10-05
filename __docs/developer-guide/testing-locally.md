# Testing locally

Two halves: the automated suites, which need Docker for anything that touches the database,
and the manual checks, which need the running stack. Both before a PR.

## Automated tests

```bash
./mvnw test-compile                                        # compiles main and test sources of every module
./mvnw test                                                # everything
./mvnw -pl Admin-API test                                  # one module
./mvnw -pl Client-API -Dtest=LostItemControllerTests test  # one class
./mvnw -pl Client-API -Dtest='LostItemControllerTests#createsLostItem' test   # one method
```

**Docker must be running.** The Postgres-backed tests start a `postgis/postgis:17-3.5`
Testcontainer and apply the real changelog from `DB-Postgres/changelog`. They are annotated
`@Testcontainers(disabledWithoutDocker = true)`, which means that without Docker they are
**skipped, not failed**, and `BUILD SUCCESS` tells you nothing. Check the surefire summary
for `Skipped:` before trusting a green run.

`ImageStorageIT` in `Admin-API` is different: it talks to the MinIO from `docker compose`
and is enabled only when that MinIO answers (`@EnabledIf("minioIsUp")`).

### What each family covers

| Tests | Style | What they prove |
|---|---|---|
| `*Test` in `view/`, `form/`, `util/`, `dto/`, `event/` | Plain JUnit, no Spring | Formatting, masking, enum logic, event id scanning |
| `MessagesTest` (`Admin-API`) | Plain JUnit | All five bundles have the same keys and the same message arguments |
| `PermissionsTest` (`Admin-API`) | Plain JUnit | The `Scope` × `Action` matrix and the `SCOPE:ACTION` authority format |
| `CountriesPropertiesTest` (both APIs) | Plain JUnit | Config validation: unknown country or missing fallback fails fast |
| `*ControllerTests` (`Admin-API`) | `@WebMvcTest` with `SecurityConfig` imported, services mocked | Routes, permissions, form binding, rendered HTML |
| `*ControllerTests` (`Client-API`) | `@SpringBootTest` + `@AutoConfigureGraphQlTester` on a Testcontainer | The GraphQL schema end to end against a real schema |
| `*RepositoryTests` (`Client-API`) | `@DataJpaTest` with `ddl-auto=validate` on a Testcontainer | Specifications, spatial and full-text queries, entity-to-schema match |
| `MatchNotificationServiceTest`, `NotificationSenderTest`, `ItemEventAspectTest` | Mockito | Event contents and routing without a broker |
| `*ApplicationTests` | `@SpringBootTest` context load | The module still starts |

Conventions for writing new ones are in [conventions/testing.md](../conventions/testing.md).

## Manual checks

Start the stack as in [getting started](getting-started.md).

### GraphQL through the gateway

`http://localhost:8080/graphiql`. Going through the gateway rather than `:8082` directly
also proves the `client-api` route and CORS. Useful documents:

```graphql
query Reference { categories { id key } countries { code currency } }
```

```graphql
mutation CreateLost {
  createLostItem(input: {
    title: "Black leather wallet"
    description: "Lost near the central railway station"
    date: "2026-09-28"
    categoryId: 2
    compensation: { amount: 500 }
    place: { id: "manual-1", name: "Kyiv-Pasazhyrskyi", lat: 50.4397, lon: 30.4894, countryCode: "UA" }
    contact: { phone: "+380671234567", email: "owner@example.com", socialMedias: [TELEGRAM] }
  }) {
    id title date
    compensation { amount currency }
    place { name countryCode }
    contact { phone email }
  }
}
```

```graphql
query SearchLost {
  lostItems(
    filter: { search: "wallet", categoryId: 2, near: { lat: 50.45, lon: 30.52, radiusKm: 20 } }
    sort: DATE_DESC
    first: 10
  ) {
    edges { cursor node { id title date place { name } contact { phone email } } }
    pageInfo { hasNextPage endCursor }
  }
}
```

Contacts in query results are masked (`+38067*****67`, `o*****r@example.com`); that is the
intended behaviour, not a bug. Errors come back as `BAD_REQUEST` with messages such as
`Unsupported country: XX` or `NOT_FOUND`.

### Admin panel and permissions

`http://localhost:8083/admin`. Log in with each dev user when you touch permissions:

| User | Realm role | Expect |
|---|---|---|
| `admin@derechi.local` / `admin` | `ADMIN_SUPER` | Everything: delete, archive, the archive pages, plus all of the below |
| `moderator@derechi.local` / `moderator` | `ADMIN_EDITOR` | View, create, edit, notify; no archive, no delete, no archive pages |
| `viewer@derechi.local` / `viewer` | `ADMIN_VIEWER` | Matches, lost and found lists only; action buttons hidden, a direct POST gets 403 |
| `user@derechi.local` / `user` | `USER` | 403 on `/admin` |

Which `SCOPE:ACTION` roles each composite holds is in `docker/keycloak/realms/derechi-realm.json`.
To switch users, sign out from the admin (it logs you out of Keycloak too) or use a private
window. Check at least one non-English locale with `?lang=uk`.

### RabbitMQ

`http://localhost:15672`, `derechi` / `derechi`. Queues to watch:

| Queue | Consumer | Fed by |
|---|---|---|
| `worker.items` | `Worker` | `derechi.items` with `item.*.created` |
| `notification.events` | `Notification` | `derechi.notifications` with `notification.#` |
| `worker.items.dlq`, `notification.events.dlq` | nobody | Dead letters after 3 failed attempts |

A healthy run leaves the DLQs at zero. A message there can be opened in the UI ("Get
messages") to see the payload and the `x-death` header with the reason.

### Notifications

Mailpit at `http://localhost:8025` receives every email `Notification` sends. SMS and
messengers have no local sink; the `Notification` log shows what it tried. Notifying the same
match twice within an hour is deduplicated by NotifyHub on purpose.

### Images

Upload from the admin item form (jpeg, png, webp, gif, up to 5 MB). The key appears in the
MinIO console at `http://localhost:9001` (`derechi` / `derechi123`) under `derechi-files`,
and the image is served at `http://localhost:8080/files/<key>` through the gateway.

### Metrics

`http://localhost:9090/targets`. Services run from IDEA are scraped as
`host.docker.internal:<port>`; a target shows as down until the service is up. Grafana at
`http://localhost:3000` (`admin` / `admin`) has the Prometheus datasource provisioned; the
Spring Boot dashboard id 4701 works with the `application` label the scrape config sets.

## Resetting state

Application database, when migrations or test data went sideways (`liquibase:dropAll` cannot
drop the PostGIS tables, so recreate):

```bash
docker exec derechi-postgres psql -U derechi -d postgres -c "DROP DATABASE derechi;"
docker exec derechi-postgres psql -U derechi -d postgres -c "CREATE DATABASE derechi OWNER derechi;"
./mvnw -pl DB-Postgres liquibase:update
```

Everything, including RabbitMQ definitions, the Keycloak realm, MinIO objects and Mailpit:

```bash
docker compose down -v
docker compose up -d
./mvnw -pl DB-Postgres liquibase:update
```

`down -v` is the only way to get the realm JSON re-imported, since it loads only into an
empty volume. `definitions.json` is imported again on every `up` by `rabbitmq-init`.
