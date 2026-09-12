# DB-Postgres — database schema

This module owns the schema of the shared `derechi` database. It is a **library, not a
service**: it is never started in Docker and no other module depends on it. Entities live
here, migrations live here, and every other service runs with `ddl-auto: none` and no
migration tool at all.

Migrations are managed by **Liquibase**. Deltas are generated from the JPA entities, so the
entities are the source of truth and the changelog is derived from them — but the generated
output always needs a read-through before it is applied (see [Known quirks](#known-quirks)).

## Layout

```
DB-Postgres/
├── changelog/
│   ├── changelog-master.yaml            # includeAll of changes/, sorted by filename
│   └── changes/
│       ├── 001-enable-postgis.sql
│       └── 002-init-schema.postgresql.sql
├── liquibase.properties                 # config for liquibase-maven-plugin
└── src/main/resources/application.yaml  # config for the Spring runtime
```

`changelog/` sits at the module root but is copied onto the classpath by an explicit
`<resources>` block in `pom.xml`. That is not cosmetic: Liquibase identifies a changeset by
`(id, author, filename)`, so the Maven plugin and Spring must resolve changesets under the
**same** relative path. If they disagree, Liquibase considers applied migrations unapplied
and replays them.

Changesets are plain SQL. Only the master changelog is YAML, because `includeAll` does not
exist in the SQL format.

## Prerequisites

```bash
docker compose up -d postgres
```

The image is `postgis/postgis` — the stock `postgres` image has no PostGIS, and
`place.coordinate` is a `geography` column.

If the JVM default timezone is `Europe/Kiev`, every connection fails with
`invalid value for parameter "TimeZone"`: PostgreSQL 18 dropped that alias. Use `Europe/Kyiv`:

```bash
export MAVEN_OPTS="-Duser.timezone=Europe/Kyiv"
```

## The flow

### 1. Change the entities

Ordinary JPA work in `src/main/java`. Nothing Liquibase-specific.

### 2. Compile

```bash
./mvnw -pl DB-Postgres compile
```

Not optional. The diff reads the entities through `referenceUrl=hibernate:spring:`, which
builds a `SessionFactory` from the **compiled** classes. Skip this and you diff against the
previous state of your entities.

### 3. Generate the delta

```bash
./mvnw -pl DB-Postgres liquibase:diff
```

Compares the entities against the live database and writes the difference to
`changelog/changes/003-delta.postgresql.sql` (configured as `diffChangeLogFile`). No shadow
database is involved.

`changeSets count: 0` means the schema already matches the entities and nothing was written.

### 4. Review and rename

**Read the generated file before applying it.** Liquibase gets several things in this project
wrong — the list is below. Fix them by hand.

Then rename `003-delta.postgresql.sql` to something meaningful:

```
changelog/changes/003-add-thing-status.postgresql.sql
```

`003-delta` is just "the next free number". If you leave the name as is, the next diff appends
its changesets to the end of that same file and two unrelated releases end up sharing one
number.

The `.postgresql.sql` suffix must stay. Without it Liquibase refuses to serialize:
`Serializing changelog as sql requires a file name in the format *.databaseType.sql`.

### 5. Apply

```bash
./mvnw -pl DB-Postgres liquibase:update
```

### 6. Verify

```bash
./mvnw -pl DB-Postgres spring-boot:run
```

The module runs with `ddl-auto: validate`, so Hibernate compares the resulting schema against
the entities and refuses to start on any mismatch. This is the step that catches what the diff
got wrong — do not skip it.

A second `liquibase:diff` should then report `changeSets count: 0`.

## Known quirks

Things the generator gets wrong in this project, in order of how often they bite:

**PostGIS types lose their typmod.** `geography(Point,4326)` is emitted as bare `GEOGRAPHY`
(or, from the entity side, as the un-parseable `GEOGRAPHY point, 4326`). Fix by hand.

**Arrays become scalars.** `SocialMediaEnum[]` maps to `smallint[]`, but a diff taken from the
entities emits a scalar `SMALLINT`. Caught by `ddl-auto: validate`, not by the diff itself.

**Naming strategy is not inherited from Boot.** The extension builds a bare Hibernate
`SessionFactory`, so `liquibase.properties` sets `hibernate.physical_naming_strategy`
explicitly. Without it the diff produces `ContactInfo` and `socialMedias` instead of
`contact_info` and `social_medias`.

**`diffChangeLogFile` is resolved from the reactor root**, not from the module — hence the
`DB-Postgres/` prefix in `liquibase.properties`. Every other path there is module-relative.

**`liquibase:dropAll` does not work on this database.** It cannot drop `geometry_columns`,
which the PostGIS extension owns. To reset a dev database, recreate it:

```bash
docker exec derechi-postgres psql -U derechi -c "DROP DATABASE derechi;"
docker exec derechi-postgres psql -U derechi -c "CREATE DATABASE derechi OWNER derechi;"
```

## Rules

**Never edit a changeset that has been applied.** Liquibase stores a checksum in
`databasechangelog`; changing an applied file breaks the next run with a validation error.
Corrections always go into a new changeset.

**Numbering is the execution order.** `includeAll` sorts by filename, so the numeric prefix is
what orders the migrations. Keep it zero-padded and monotonic.

**Repeatable changesets** (`runOnChange:true`) are the right tool for views, functions and
reference-data seeds — anything that should re-run whenever its definition changes.

**Indexes are not generated** unless declared in `@Table(indexes = ...)`. The GiST index for
geo queries on `place.coordinate`, for instance, has to be written into a migration by hand.
