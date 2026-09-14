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
│       ├── 001-extentions-and-configuration.sql
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
docker exec derechi-postgres psql -U derechi -d postgres -c "DROP DATABASE derechi;"
docker exec derechi-postgres psql -U derechi -d postgres -c "CREATE DATABASE derechi OWNER derechi;"
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
## Command reference

Every goal below is run from the **reactor root**, and every one of them reads
`liquibase.properties` through the `<propertyFile>` configured on the plugin, so the
connection details never go on the command line.

`process-resources` is what copies `changelog/` into `target/classes`. The plugin resolves
changesets from there, not from the source tree, so run it whenever a changeset file changed:

```bash
./mvnw -pl DB-Postgres process-resources
```

### Applying

| Command | What it does |
|---|---|
| `./mvnw -pl DB-Postgres liquibase:update` | Applies every pending changeset. |
| `./mvnw -pl DB-Postgres liquibase:updateSQL` | Dry run: prints the SQL instead of executing it. |
| `./mvnw -pl DB-Postgres liquibase:update -Dliquibase.changesToApply=1` | Applies only the next N changesets. |
| `./mvnw -pl DB-Postgres liquibase:update -Dliquibase.toTag=v1` | Applies everything up to a tag, then stops. |
| `./mvnw -pl DB-Postgres liquibase:updateTestingRollback` | Applies, rolls back, applies again — proves the rollback works. |

### Inspecting

| Command | What it does |
|---|---|
| `./mvnw -pl DB-Postgres liquibase:status -Dliquibase.verbose=true` | Lists changesets not yet applied. First thing to run when a migration "did nothing". |
| `./mvnw -pl DB-Postgres liquibase:history` | Lists what has been applied, in order. |
| `./mvnw -pl DB-Postgres liquibase:validate` | Parses the changelog and checks for duplicate ids and checksum conflicts, without touching the schema. |
| `./mvnw -pl DB-Postgres liquibase:unexpectedChangeSets` | Finds rows in `databasechangelog` with no matching file — typically a renamed or deleted changeset. |
| `./mvnw -pl DB-Postgres liquibase:listLocks` | Shows who holds the changelog lock. |
| `./mvnw -pl DB-Postgres liquibase:snapshot -Dliquibase.outputFile=snapshot.json` | Dumps the current schema as JSON. |
| `./mvnw -pl DB-Postgres liquibase:dbDoc -Dliquibase.outputDirectory=target/dbdoc` | Generates browsable HTML docs for the schema. |

### Generating

| Command | What it does |
|---|---|
| `./mvnw -pl DB-Postgres liquibase:diff` | Entities vs. live database; writes `diffChangeLogFile`. The main one. |
| `./mvnw -pl DB-Postgres liquibase:diff -Dliquibase.diffChangeLogFile=DB-Postgres/changelog/changes/004-something.postgresql.sql` | Same, but names the output file up front so it does not need renaming afterwards. |
| `./mvnw -pl DB-Postgres liquibase:diff -Dliquibase.outputFile=diff.txt` | Report only — prints the differences instead of writing a changeset. |
| `./mvnw -pl DB-Postgres liquibase:generateChangeLog` | Builds a changelog from the **existing database**, ignoring the entities. For adopting a schema that has no changelog yet. |

### Tags and rollback

| Command | What it does |
|---|---|
| `./mvnw -pl DB-Postgres liquibase:tag -Dliquibase.tag=v1` | Marks the current state so it can be rolled back to later. |
| `./mvnw -pl DB-Postgres liquibase:tagExists -Dliquibase.tag=v1` | Checks whether that tag is present. |
| `./mvnw -pl DB-Postgres liquibase:rollback -Dliquibase.rollbackCount=1` | Reverts the last N changesets. |
| `./mvnw -pl DB-Postgres liquibase:rollback -Dliquibase.rollbackTag=v1` | Reverts back to a tag. |
| `./mvnw -pl DB-Postgres liquibase:rollback -Dliquibase.rollbackDate=2026-09-12` | Reverts everything applied after a date. |
| `./mvnw -pl DB-Postgres liquibase:rollbackSQL -Dliquibase.rollbackCount=1` | Dry run of the above. |
| `./mvnw -pl DB-Postgres liquibase:futureRollbackSQL` | Prints the SQL that would undo the *pending* changesets, before applying them. |

Rollback only works for changesets Liquibase can reverse automatically or that carry an
explicit `--rollback` line. Raw SQL changesets without one cannot be rolled back.

### Recovery

These rewrite bookkeeping rather than schema. Reach for them only when the changelog and the
database have drifted apart.

| Command | What it does |
|---|---|
| `./mvnw -pl DB-Postgres liquibase:changelogSync` | Marks every pending changeset as applied **without running it**. For a schema that already exists. |
| `./mvnw -pl DB-Postgres liquibase:changelogSyncSQL` | Dry run of the above. |
| `./mvnw -pl DB-Postgres liquibase:changelogSyncToTag -Dliquibase.toTag=v1` | Same, but stops at a tag. |
| `./mvnw -pl DB-Postgres liquibase:clearCheckSums` | Clears stored checksums so they are recomputed on the next run. Fixes "checksum changed" after an applied file was edited. |
| `./mvnw -pl DB-Postgres liquibase:releaseLocks` | Releases a stale changelog lock left by a killed run. |
| `./mvnw -pl DB-Postgres liquibase:update -Dliquibase.dropFirst=true` | Drops everything, then applies. Does **not** work here — see `dropAll` under [Known quirks](#known-quirks). |

To reset a dev database, recreate it instead:

```bash
docker exec derechi-postgres psql -U derechi -d postgres -c "DROP DATABASE derechi;"
docker exec derechi-postgres psql -U derechi -d postgres -c "CREATE DATABASE derechi OWNER derechi;"
./mvnw -pl DB-Postgres spring-boot:run
```

### Useful flags

| Flag | Effect |
|---|---|
| `-Dliquibase.propertyFile=other.properties` | Uses a different properties file than the one in `pom.xml`. |
| `-Dliquibase.logLevel=debug` | Full Liquibase logging. |
| `-Dliquibase.skip=true` | No-op, for skipping the plugin in a larger build. |

Goals ignore properties that belong to another goal and say so:

```
'diffChangeLogFile' in properties file is not being used by this task.
java.lang.NoSuchFieldException: ...
```

That is informational — `liquibase.properties` is shared by every goal, and `update` has no
`diffChangeLogFile` field. The build still succeeds.
