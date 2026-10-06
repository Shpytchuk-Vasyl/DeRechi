# Getting started

From a clean machine to a running stack with a matched pair of notices and a notification
in the mailbox. Budget an hour the first time, most of it waiting for Docker images.

## Prerequisites

| Tool | Notes |
|---|---|
| JDK 26 | The reactor compiles with `<release>26</release>`. Any vendor build works; the Docker image uses Eclipse Temurin. |
| Docker Desktop | Infrastructure runs in containers, and the Postgres-backed tests use Testcontainers. Allow it at least 4 GB of memory. |
| Git | On Windows, clone with `core.autocrlf=false` or let the repository's line endings be; the Maven wrapper script must stay LF. |
| IntelliJ IDEA | Recommended. The Community edition is enough; Ultimate adds Spring run configurations and the database tool. Enable annotation processing for Lombok. |
| pnpm | Only if you work on `Web-Client`. See [Web-Client/README.md](../../Web-Client/README.md). |

Maven itself is not needed: use the wrapper in the repository root (`./mvnw` on Git Bash
and macOS, `mvnw.cmd` on cmd and PowerShell). There is one wrapper for the whole reactor;
modules do not have their own.

If your machine's time zone is `Europe/Kiev`, set `MAVEN_OPTS="-Duser.timezone=Europe/Kyiv"`
and add the same `-D` flag to your IDEA run configurations. The database rejects the old
spelling and every connection fails otherwise.

## 1. Clone and start the infrastructure

```bash
git clone https://github.com/Shpytchuk-Vasyl/DeRechi.git
cd DeRechi
```

Before the first start, uncomment the dev users in `docker-compose.yml` (the `keycloak`
service, the volume line marked "only for development"). The committed file is
production-safe and starts Keycloak without them. Keep the change local; optionally also swap
the `start` command for the commented-out `start-dev` one. See
[deployment/keycloak.md](../deployment/keycloak.md#dev-users).

```bash
docker compose up -d
```

The first run builds our Postgres image (PostGIS plus a Ukrainian full-text dictionary) and
pulls RabbitMQ, Keycloak, MinIO, Mailpit, pgAdmin, Prometheus, Alertmanager and Grafana.
Wait until `docker compose ps` shows PostgreSQL, RabbitMQ, MinIO and Mailpit `healthy` and
the rest `running`, and `http://localhost:8180` answers (Keycloak is the slowest). Keycloak imports the `derechi`
realm (and the dev users, once uncommented) on this first start; MinIO's init container creates the
`derechi-files` bucket and the application account, then exits.

What is now listening, with credentials, is listed in
[deployment/infrastructure.md](../deployment/infrastructure.md).

## 2. Create the schema

Services never touch the schema; `DB-Postgres` does.

```bash
./mvnw -pl DB-Postgres liquibase:update
```

Then build everything once to make sure the toolchain is right:

```bash
./mvnw test-compile
```

## 3. Start the services

Order does not matter.

From IDEA, run the `*Application` classes:
`GetawayApplication`, `ClientApiApplication`, `AdminApiApplication`,
`WorkerApplication`, `NotificationApplication`. From a terminal, one module per
window:

```bash
./mvnw -pl Getaway spring-boot:run
./mvnw -pl Client-API spring-boot:run
./mvnw -pl Admin-API spring-boot:run
./mvnw -pl Worker spring-boot:run
./mvnw -pl Notification spring-boot:run
```

The `Launcher` module starts `Getaway`, `Client-API`, `Admin-API` and
`Worker` as separate contexts in one JVM, which is lighter on a laptop. It is
currently commented out in the root `pom.xml` `<modules>` list; uncomment it locally if you
want it, but do not commit that, and note it starts neither `Notification` nor anything in
Docker. See [architecture/modules/launcher.md](../architecture/modules/launcher.md).

Ports: Getaway 8080, Client-API 8082, Admin-API 8083, Notification 8084, Worker 8085.
Actuator is on a separate management port, the service port + 1000: each answers
`/actuator/health` on 9080, 9082, 9083, 9084 or 9085.

The alternative, everything in containers, is one command and is covered in
[deployment/local-development.md](../deployment/local-development.md):

```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

## 4. Open the admin panel

`http://localhost:8083/admin` redirects to Keycloak. Log in as `admin@derechi.local` /
`admin`. You should see the navigation with Matches, Lost, Found and the archives. The other
dev users are `moderator@derechi.local` / `moderator`, `viewer@derechi.local` / `viewer` and
`user@derechi.local` / `user`; the last one has no admin roles and gets a 403, which is
correct. They come from `docker/keycloak/dev/derechi-users-0.json`, which is imported only if you
uncommented its volume line in step 1; production has none of them. Forgot it? Uncomment the
line, then `docker compose down -v` (loses local data) and start again: the import only
runs for a realm that does not exist yet. Switch the language with `?lang=uk` (or `pl`, `de`, `fr`, `en`).

## 5. Create a lost and a found notice

GraphiQL is served by `Client-API` and routed through the gateway:
`http://localhost:8080/graphiql`. First get the category ids:

```graphql
query { categories { id key } countries { code currency } }
```

Then create a lost item. Matching looks at the same category, a date within three days and a
place within 20 km, so give the found item the same category, a nearby date and nearby
coordinates. The `place.id` is a Google Place id in production; any string works here.

```graphql
mutation {
  createLostItem(input: {
    title: "Black leather wallet"
    description: "Lost near the central railway station"
    date: "2026-09-28"
    categoryId: 2
    compensation: { amount: 500 }
    place: { id: "manual-1", name: "Kyiv-Pasazhyrskyi", lat: 50.4397, lon: 30.4894, countryCode: "UA" }
    contact: { phone: "+380671234567", email: "owner@example.com", socialMedias: [TELEGRAM] }
  }) { id title compensation { amount currency } }
}
```

```graphql
mutation {
  createFoundItem(input: {
    title: "Found a black wallet"
    description: "Leather wallet found on platform 3"
    date: "2026-09-29"
    categoryId: 2
    image: "items/2026/09/example.jpg"
    place: { id: "manual-2", name: "Kyiv-Pasazhyrskyi, platform 3", lat: 50.4401, lon: 30.4901, countryCode: "UA" }
    contact: { phone: "+380501112233", email: "finder@example.com" }
  }) { id title }
}
```

Each mutation publishes an `item.lost.created` or `item.found.created` event on the
`derechi.items` exchange. Watch the `Worker` log: it says it is searching for
candidates and how many it saved. The RabbitMQ UI at `http://localhost:15672`
(`derechi` / `derechi`) shows the message passing through `worker.items`.

## 6. See the match and notify the owner

Open **Matches** in the admin panel. The lost wallet lists the found wallet as a candidate
with its score. Click **Notify**: the admin publishes a `notification.match.found` event,
`Notification` picks it up from `notification.events` and sends an email through Mailpit.
Open `http://localhost:8025` and the message is there. The row now shows who notified and when.

If any step did not happen, [troubleshooting](troubleshooting.md) covers the usual reasons.

## 7. Next

- [Architecture](../architecture/README.md) for what you just ran.
- [Development workflow](development-workflow.md) for your first ticket.
- [Testing locally](testing-locally.md) for running the suites.
