# DB-Postgres

The schema owner. It holds the JPA entities that define the `derechi` database and the
Liquibase changelog that creates it. No other module depends on it. In Docker it is a
**one-shot job**, not a long-running service: the `db-postgres` container applies pending
changesets, validates the schema against the entities (`ddl-auto: validate`) and exits.
`client-api`, `admin-api` and `worker` wait for it to exit with 0
(`condition: service_completed_successfully`).

`DB-Postgres/README.md` is the full reference for the module, including every Liquibase
goal we use. This page is the short version and the reasoning.

## Layout

```
DB-Postgres/
├── changelog/
│   ├── changelog-master.yaml           # includeAll of changes/, relativeToChangelogFile
│   └── changes/
│       ├── 001-extentions-and-configuration.sql
│       ├── 002-init-schema.postgresql.sql
│       ├── 003-add-base_categories.postgresql.sql
│       ├── 004-similar_item.postgresql.sql
│       ├── 005-country-and-currency.postgresql.sql
│       ├── 006-item-claims.postgresql.sql
│       ├── 007-claim-payment.postgresql.sql
│       ├── 008-claim-unlock-limit.postgresql.sql
│       └── 009-shedlock.postgresql.sql     # Worker's ShedLock table, no entity
├── liquibase.properties                # for the Maven plugin; not under src/ on purpose
├── pom.xml
└── src/main/
    ├── java/org/shpytchuk/dbpostgres/
    │   ├── DbPostgresApplication.java
    │   ├── thing/       Thing, ThingCategory
    │   ├── lost/        LostItem, LostItemHistory, LostItemClaim
    │   ├── found/       FoundItem, FoundItemHistory, FoundItemClaim
    │   ├── detail/      ContactInfo, Place
    │   ├── matching/    SimilarItem, Claim
    │   └── payment/     FourthwallOrder
    └── resources/application.yaml      # ddl-auto: validate, liquibase enabled
```

`changelog/` is a directory at the module root, not under `src/main/resources`.
`liquibase.properties` is kept out of `src/` so it does not end up in the jar.

## Why the changelog path must be identical everywhere

Liquibase identifies a changeset by the triple `(id, author, filename)`, and `filename` is
the path as the tool saw it. Two tools read this changelog:

- the Maven plugin, with `<searchPath>${project.basedir}</searchPath>`, sees
  `changelog/changelog-master.yaml` relative to the module directory;
- Spring, when the module is run, sees `classpath:changelog/changelog-master.yaml`, which
  works because `pom.xml` has a second `<resource>` copying `changelog/` to
  `target/classes/changelog`.

Both resolve to `changelog/...`, so a migration applied by one is recognised as applied by
the other. If either side ever saw `DB-Postgres/changelog/...` or
`src/main/resources/changelog/...` instead, every changeset would look new and be applied a
second time. The integration tests in `Client-API` and `Admin-API` point Liquibase at the
same directory on disk for the same reason.

## Why the jar is executable

`spring-boot-maven-plugin` is bound to `package`, as in the services. The root `Dockerfile`
copies `${MODULE}/target/*.jar` and runs it with `java -jar`, so the `db-postgres` image
needs a repackaged jar. `repackage` moves the classes under `BOOT-INF/classes/`, which a
dependent module could not read. That is acceptable only because nobody depends on
`DB-Postgres`. The integration tests in `Client-API` and `Admin-API` read `changelog/` from
disk, not from the jar. If a module ever needs the entities as a dependency, give the
executable jar a classifier and change the `COPY` in the `Dockerfile` to match.

## Generating a migration

The diff is computed between the **compiled entities** and the **live database**. The
`liquibase-hibernate7` extension builds a Hibernate `SessionFactory` from the package named
in `referenceUrl=hibernate:spring:org.shpytchuk.dbpostgres?...`, so there is no second
database to keep around.

```bash
./mvnw -pl DB-Postgres compile          # the diff reads target/classes, not sources
./mvnw -pl DB-Postgres liquibase:diff   # -> changelog/changes/000-delta.postgresql.sql
# read it, fix it, rename it
./mvnw -pl DB-Postgres liquibase:update
./mvnw -pl DB-Postgres spring-boot:run  # ddl-auto: validate confirms entities == schema
```

The generated SQL is always reviewed by hand. The things the generator gets wrong in this
project are listed in `DB-Postgres/README.md` under "Known quirks" and summarised in
[../../conventions/database-migrations.md](../../conventions/database-migrations.md):
PostGIS typmods, array columns, naming strategy, the `DB-Postgres/` prefix on
`diffChangeLogFile`, and the fact that `dropAll` cannot run on a PostGIS database.

## Config

| File | Purpose |
|---|---|
| `src/main/resources/application.yaml` | `localhost:5432/derechi`, `ddl-auto: validate`, `spring.liquibase.change-log: classpath:changelog/changelog-master.yaml` |
| `liquibase.properties` | the same JDBC URL, `changeLogFile=changelog/changelog-master.yaml`, `diffChangeLogFile=DB-Postgres/changelog/changes/000-delta.postgresql.sql`, `referenceUrl` with the explicit `CamelCaseToUnderscoresNamingStrategy`, `diffExcludeObjects` for the PostGIS tables and `shedlock` (a table without an entity, which the diff would otherwise drop) |

## Tests

`DbPostgresApplicationTests` is a context-load test. The real verification is
`spring-boot:run` against a migrated database, which fails on any mismatch between entities
and schema.
