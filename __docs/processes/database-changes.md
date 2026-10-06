# Database changes

All services share the `derechi` database, and only `DB-Postgres` is allowed to change its
schema. Entities in `DB-Postgres` are the source of truth; Liquibase migrations are generated
from them by diffing against a live database, then fixed by hand and committed. Services run
with `ddl-auto: none` and `spring.liquibase.enabled: false`, so no service migrates on
start-up. `DB-Postgres` applies migrations itself. Locally that is the Maven plugin. In
Docker it is the one-shot `db-postgres` container, which runs before the services that use
the database.

The rules behind this page are in [database migration conventions](../conventions/database-migrations.md).
The full Liquibase command reference, including rollback and recovery goals, is in
[DB-Postgres/README.md](../../DB-Postgres/README.md).

## Prerequisites

- Postgres running: `docker compose up -d postgres`. The image is our own PostGIS build with a
  Ukrainian full-text dictionary, so the stock `postgres` image will not do.
- The JVM time zone must not be `Europe/Kiev`; PostgreSQL rejects that alias. Set
  `MAVEN_OPTS="-Duser.timezone=Europe/Kyiv"` if your machine uses the old name.

## Step by step

1. **Change the entity in `DB-Postgres`** under `src/main/java/org/shpytchuk/dbpostgres`
   (`thing/`, `lost/`, `found/`, `detail/`, `matching/`). Ordinary JPA work.

2. **Change the same entity in every service that has a copy.** Entities are deliberately
   duplicated per service (`org.shpytchuk.adminapi.entity`, `org.shpytchuk.clientapi.entity`,
   `org.shpytchuk.worker.entity`, each split into the same `thing/`, `lost/`, `found/`,
   `detail/`, `matching/` sub-packages). Services that do not use the field can skip it only
   if their copy of the entity does not map that table at all; otherwise `ddl-auto: validate`
   in the tests will complain.

3. **Compile.** The diff reads the compiled classes, not the sources.

   ```bash
   ./mvnw -pl DB-Postgres compile
   ```

4. **Generate the delta.**

   ```bash
   ./mvnw -pl DB-Postgres liquibase:diff
   ```

   This writes `DB-Postgres/changelog/changes/000-delta.postgresql.sql`. A result of
   `changeSets count: 0` means the database already matches the entities.

5. **Review the generated file by hand.** Liquibase gets several things wrong here, every time:
   - `geography(Point,4326)` comes out as bare `GEOGRAPHY` or as unparseable
     `GEOGRAPHY point, 4326`. Write the real type.
   - Array columns (`SocialMediaEnum[]` is `smallint[]`) come out as scalar `SMALLINT`.
   - Indexes are only generated when declared in `@Table(indexes = ...)`; GiST indexes for geo
     queries are written by hand.
   - Generated constraint names such as `FK18kanuyefq0emoqxhn2pocfc7` are ugly but harmless;
     rename them if you are touching the statement anyway.
   - Changeset ids: the generator uses timestamps (`1789398081101-1`). Prefer readable ids that
     match the file (`005-country-and-currency-1`), as the later migrations do.

6. **Rename the file** to the next free number and a meaningful name, keeping the
   `.postgresql.sql` suffix, which Liquibase requires for SQL changelogs:

   ```
   DB-Postgres/changelog/changes/006-add-thing-status.postgresql.sql
   ```

7. **Add a backfill when introducing a `NOT NULL` column** on a table that has data. The
   pattern we use (see `005-country-and-currency.postgresql.sql`): add the column nullable,
   `UPDATE ... WHERE col IS NULL` with a sensible default, then `ALTER COLUMN ... SET NOT NULL`.

8. **Apply it.**

   ```bash
   ./mvnw -pl DB-Postgres liquibase:update
   ```

9. **Validate the schema against the entities.** `DB-Postgres` starts with
   `ddl-auto: validate` and refuses to boot on any mismatch. This is the step that catches
   what the diff got wrong.

   ```bash
   ./mvnw -pl DB-Postgres spring-boot:run
   ```

   A second `liquibase:diff` should now report zero changesets.

10. **Run the tests of every affected service.** The Postgres-backed tests start a PostGIS
    Testcontainer and apply the changelog from `DB-Postgres/changelog`, so they prove that the
    migration runs on an empty database and that the service's copy of the entity matches it.

    ```bash
    ./mvnw -pl Client-API,Admin-API test
    ```

11. **Commit the migration together with the entity changes** and go through the normal
    [pull request](../developer-guide/pull-requests.md) flow. Say in the PR that it contains a
    migration.

## Rules

- **Never edit a changeset that has been applied anywhere**, including a teammate's machine.
  Liquibase stores a checksum and the next `update` fails with a validation error. Fix
  mistakes with a new changeset. `liquibase:clearCheckSums` exists for emergencies, not for
  routine.
- **Numbering is execution order.** `includeAll` sorts by filename. Keep the prefix zero-padded
  and monotonic; if two branches take the same number, the second one to merge renumbers.
- **The changelog path is always `changelog/...`** with no module or `src/main/resources`
  prefix. Liquibase identifies a changeset by `(id, author, filename)`; a different path means
  "not applied" and the migration runs again.
- Use `runOnChange:true` for views, functions and reference-data seeds that should re-run when
  their definition changes.
- Resetting a dev database means recreating it. `liquibase:dropAll` cannot drop the PostGIS
  system tables:

  ```bash
  docker exec derechi-postgres psql -U derechi -d postgres -c "DROP DATABASE derechi;"
  docker exec derechi-postgres psql -U derechi -d postgres -c "CREATE DATABASE derechi OWNER derechi;"
  ./mvnw -pl DB-Postgres liquibase:update
  ```

## Rollout to shared environments

On an environment run with `docker-compose.services.yml`, Compose enforces the order:

1. `up -d --build` rebuilds the `db-postgres` image with the new changelog and runs it. It
   applies the pending changesets, validates the schema against the entities and exits.
2. `client-api`, `admin-api` and `worker` start only after it exits with 0. If it fails,
   Compose reports `dependency failed to start` and does not start them; read
   `docker logs derechi-db-postgres`.

The first `up` against a database whose schema was applied by hand (no `databasechangelog`
table) needs a one-time `liquibase:changelogSync`; see
[DB-Postgres/README.md](../../DB-Postgres/README.md#a-database-whose-schema-was-applied-by-hand).

For a database outside that compose stack, apply the migration by hand before deploying:
`./mvnw -pl DB-Postgres liquibase:update` pointed at that environment. Override the
connection with `-Dliquibase.url=... -Dliquibase.username=... -Dliquibase.password=...`, or
pass a separate properties file with `-Dliquibase.propertyFile`.

Write migrations so that the **previous** version of the services still works against the
new schema where you can: add columns nullable or with defaults, drop columns in a later
release once nothing reads them. That keeps a service rollback possible without a database
rollback. Rolling a migration back is only possible when the changeset carries an explicit
`--rollback` line; plain SQL changesets without one cannot be reverted by Liquibase.

Today there is no staging environment. On any environment that runs the compose stack,
migrations are applied automatically on deploy by `db-postgres`. See [release](release.md).
