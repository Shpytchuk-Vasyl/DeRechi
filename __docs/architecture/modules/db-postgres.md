# DB-Postgres

The schema library. It holds the JPA entities that define the `derechi` database and the
Liquibase changelog that creates it. It is **not a service**: it is never started in
Docker, no other module depends on it, and its jar is only ever built as a side effect of
building the reactor.

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
│       └── 006-item-claims.postgresql.sql
├── liquibase.properties                # for the Maven plugin; not under src/ on purpose
├── pom.xml
└── src/main/
    ├── java/org/shpytchuk/dbpostgres/
    │   ├── DbPostgresApplication.java
    │   ├── thing/       Thing, ThingCategory
    │   ├── lost/        LostItem, LostItemHistory, LostItemClaim
    │   ├── found/       FoundItem, FoundItemHistory, FoundItemClaim
    │   ├── detail/      ContactInfo, Place
    │   └── matching/    SimilarItem, Claim
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

## Why there is no fat jar

`spring-boot-maven-plugin` is not bound to `package` here. Nobody consumes the jar, and
`repackage` would hide the classes under `BOOT-INF/classes/`, which a dependent module
could not read. `./mvnw -pl DB-Postgres spring-boot:run` still works because the goal is
invoked directly and configured from the root `pluginManagement`.

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
| `liquibase.properties` | the same JDBC URL, `changeLogFile=changelog/changelog-master.yaml`, `diffChangeLogFile=DB-Postgres/changelog/changes/000-delta.postgresql.sql`, `referenceUrl` with the explicit `CamelCaseToUnderscoresNamingStrategy`, `diffExcludeObjects` for the PostGIS tables |

## Tests

`DbPostgresApplicationTests` is a context-load test. The real verification is
`spring-boot:run` against a migrated database, which fails on any mismatch between entities
and schema.
