# Troubleshooting

The failures everyone hits once. Each entry: the symptom as you will see it, the cause, the
fix. If you hit something that belongs here, add it in the same PR that fixed it.

## Build and tooling

### `invalid value for parameter "TimeZone": "Europe/Kiev"`

Every database connection fails, from Maven, from IDEA, from tests. The database does not
accept the `Europe/Kiev` alias and your JVM reports it as the default time zone. Set
`MAVEN_OPTS="-Duser.timezone=Europe/Kyiv"` and add `-Duser.timezone=Europe/Kyiv` to the VM
options of your run configurations, or change the OS time zone name.

### Records, `@Argument` or `@ConfigurationProperties` bind to nulls

A record DTO deserializes with every field `null`, a GraphQL `@Argument` is missing, or a
`@ConfigurationProperties` record fails with "no parameter names". The compiler ran without
`-parameters`. The root `pom.xml` sets `<parameters>true</parameters>` in
`maven-compiler-plugin`; a module that overrides the compiler plugin must keep it. In IDEA,
make sure the project delegates builds to Maven or that `-parameters` is in the javac
options.

### Docker build fails with "Non-resolvable" or "Child module ... does not exist"

A module is listed in the root `<modules>` but not copied into the build stage. Every module
needs its own `COPY <Module> <Module>` line in the root `Dockerfile`. `Launcher` is the
standing example: it is commented out in `<modules>` for exactly this reason.

### Module builds on Windows, fails on Linux or in Docker

The folder name, the `artifactId` and the `<module>` entry differ in letter case
(`Client-Api` vs `Client-API`). Git on Windows ignores case; Linux does not. Rename with
`git mv` so Git records the change.

### Testcontainers tests "pass" instantly

They were skipped. The Postgres-backed suites are `disabledWithoutDocker = true`; with Docker
Desktop stopped, surefire reports them as skipped and the build is green. Start Docker and
rerun. Check the `Skipped:` count in the summary.

## Database and migrations

### `Validation Failed: ... checksum changed` on `liquibase:update`

Somebody edited a changeset that had already been applied. Revert the edit and put the
correction into a new changeset. If the file is really meant to be as it is now (for example
a comment fix), `./mvnw -pl DB-Postgres liquibase:clearCheckSums` recomputes the checksums on
the next run; use it knowingly.

### Migrations run again on a database that already has them

Liquibase identifies a changeset by `(id, author, filename)`. If the Maven plugin and Spring
see different paths (`changelog/changes/...` vs `DB-Postgres/changelog/changes/...` or
`src/main/resources/...`), everything looks unapplied. The path must be `changelog/...` on
both sides; see [conventions/database-migrations.md](../conventions/database-migrations.md).
`liquibase:unexpectedChangeSets` shows rows in `databasechangelog` with no matching file.

### `liquibase:dropAll` fails on `geometry_columns`

PostGIS owns that table and Liquibase cannot drop it. Recreate the database instead:

```bash
docker exec derechi-postgres psql -U derechi -d postgres -c "DROP DATABASE derechi;"
docker exec derechi-postgres psql -U derechi -d postgres -c "CREATE DATABASE derechi OWNER derechi;"
./mvnw -pl DB-Postgres liquibase:update
```

### `Schema-validation: wrong column type encountered` mentioning `bpchar`

A migration declared a column as `CHAR(n)`. Hibernate's validator does not accept `bpchar`
for a `String` field even with `length`. Use `VARCHAR(n)`, as `country_code` and `currency`
do.

### `liquibase:diff` produces `GEOGRAPHY point, 4326` or a scalar `SMALLINT`

Known generator limits: PostGIS typmods and array columns are not rendered correctly. Fix the
generated SQL by hand before applying; the checklist is in
[processes/database-changes.md](../processes/database-changes.md).

### Tests fail with `ddl-auto: validate` but the migration applied fine

A service's copy of the entity is out of step with `DB-Postgres`. Compare the entity in
`org.shpytchuk.<module>.entity` with `org.shpytchuk.dbpostgres` and bring it in line.

## Admin panel and Keycloak

### A role I added to the realm JSON does not show up

The realm is imported only on the first start against an empty `keycloak` database. Either
add the role in the console as well, or reset Keycloak so the import runs again. See
[processes/keycloak-realm-changes.md](../processes/keycloak-realm-changes.md).

### 403 on an admin page for a user who should see it

The user's token does not carry the `SCOPE:ACTION` client role on `derechi-admin` that the
controller requires. Check which composite role the user has and what that composite contains
in the realm JSON. Set `logging.level.org.shpytchuk.adminapi.security=DEBUG` and
`KeycloakAuthoritiesMapper` logs the resolved authorities on login. Remember the token is
cached for the session: sign out and in again after changing roles.

### Login loops or `Invalid redirect_uri`

The admin's redirect URI (`http://localhost:8083/login/oauth2/code/keycloak`) must match a
pattern on the `derechi-admin` client (`${DERECHI_ADMIN_URL}/*`, `http://localhost:8083/*`
locally). The placeholder is filled only on the first realm import, so running the admin on
another port or host means changing the redirect URI in the console as well as
`DERECHI_ADMIN_URL`. In the compose stack the browser talks to
Keycloak on `KEYCLOAK_PUBLIC_URI` and the service on `KEYCLOAK_URI`; if the public one is
wrong the browser cannot reach the login page.

### `MessagesTest` fails after adding a key

You added the key to one bundle. Every key, with the same `{0}`-style arguments, must exist
in `messages.properties`, `messages_uk.properties`, `messages_pl.properties`,
`messages_de.properties` and `messages_fr.properties`. The test output names the key and the
bundle that misses it.

### Google place autocomplete suggests the wrong countries, or not all of ours

The widget restricts suggestions to `derechi.countries.supported`, and Google caps that
restriction at five countries. With more than five supported countries the restriction has
to be dropped or made dynamic; `data-region` only biases results and does not filter.

### Two `<dialog>` elements with the same id after "load more candidates"

Dialogs for found items must live **inside** the `fragments/candidates :: cell` fragment,
because htmx replaces the whole cell. A dialog placed at the bottom of `matches.html` is
duplicated the moment a cell is reloaded. See [conventions/admin-ui.md](../conventions/admin-ui.md).

### `Unsupported country: XX` or `Unsupported currency: XXX`

The country is not in `derechi.countries.supported` of the API you are calling, or the
currency is not one of the supported countries' currencies. Both APIs have their own copy of
that list; keep them identical. Adding a country is in
[extending/add-a-country.md](../extending/add-a-country.md).

### The service does not start: "Країна XX не має валюти" or an `IllegalArgumentException` from `CountriesProperties`

Deliberate fail-fast. A code in `derechi.countries.supported` is not a valid ISO 3166-1
alpha-2 code, has no currency in the JDK, or `fallback` is not in the list.

## Messaging and notifications

### A message ended up in `worker.items.dlq`, `worker.claims.dlq`, `worker.archive.dlq` or `notification.events.dlq`

After three failed attempts the listener rejects the message and the broker dead-letters it.
A minute later the `DeadLetters` alert fires and Alertmanager mails it (into Mailpit locally);
how to replay or drop the messages is in
[deployment/monitoring.md](../deployment/monitoring.md#dead-letter-queues). Open the queue in the management UI and "Get messages": the `x-death` header carries the
reason. The usual causes:

- the routing key has no handler (`No handler for the key ...` in `Worker`);
- the payload's `__TypeId__` does not match any `@EventType` in the consumer, typically after
  renaming an event in one module but not the other;
- a real exception in the handler (database down, entity missing); fix the cause, then re-create the
  notice (or re-click Notify) so a fresh event is published; the dead-lettered copy can be
  purged.

### `Notification` fails at start-up with a NotifyHub channel error

A channel is enabled by the **presence** of its key under `notify.channels.*` (`email.host`,
`telegram.bot-token`), and an empty value counts as present. An unused channel must be
commented out, not set to an empty string, and `NOTIFY_EMAIL_HOST` must point at a reachable
SMTP host (Mailpit locally).

### Notify clicked, nothing in Mailpit

In order: is `Notification` running (`http://localhost:9084/actuator/health`, the management
port); is the message on `notification.events` or in its DLQ; does the lost item's contact
have an email (the `EMAIL` and `ALL` channels need it, `PHONE` and the messengers do not); was
the same match notified within the last hour (NotifyHub deduplicates by key with a one-hour
TTL, in memory: per instance, and forgotten on restart). The `Notification` log
prints the subject and channel at `DEBUG`.

### `@RabbitListener` fails: queue does not exist

The queues are not declared by the services; they come from `docker/rabbitmq/definitions.json`,
which `rabbitmq-init` imports on every `docker compose up`. Run `docker compose up -d` (or
`docker compose run --rm rabbitmq-init`). If the import itself fails,
`docker compose logs rabbitmq-init` names the queue whose arguments no longer match.

## Gateway and monitoring

### Gateway returns 5xx for `/graphql`

The gateway forwards to `CLIENT_API_URI` as is. Either `Client-API` is not up yet (check
`http://localhost:9082/actuator/health`, the management port), or the gateway got the wrong
address. The gateway does not expose `/actuator/gateway/routes`; check the `CLIENT_API_URI` it
was started with. From IDEA the default is `http://localhost:8082`; in compose it is
`http://client-api:8082`.

### Prometheus shows a service target down although it is running

In the dev config targets are `host.docker.internal:<management port>` (9080, 9082 to 9085),
not the service port. On Linux that name needs the `extra_hosts` entry that
`docker-compose.yml` already sets; on Windows and macOS Docker Desktop provides it. A service
run from IDEA with a different `MANAGEMENT_PORT` than the config expects also shows as down.
In the full compose stack the config switches to `prometheus-full.yml` with container names
(`<service>:908x`).

### Keycloak metrics missing

Keycloak exposes `/metrics` on its management port 9000, not `/actuator/prometheus`, and
only with `KC_METRICS_ENABLED=true`. It is a separate Prometheus job, not part of
`derechi-services`.

### CORS errors from the web client

The gateway's global CORS allows `localhost`, `127.0.0.1`, `192.168.*` and `10.*` origins on
any port. Another origin has to be added through `WEB_ORIGIN_PATTERNS`. Also check that the
browser hits the gateway (`8080`), not `Client-API` directly.
