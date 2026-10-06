# Database migrations

The schema of the shared `derechi` database is owned by one module, `DB-Postgres`. It holds the JPA entities that are the source of truth and the Liquibase changelog derived from them. No service runs Liquibase (`spring.liquibase.enabled` is off and `ddl-auto: none`), so a schema change is a deliberate, reviewed step. Once a changeset is merged, the one-shot `db-postgres` container applies it on the next `up --build` of the compose stack. The step-by-step procedure is in [database-changes](../processes/database-changes.md); the command reference lives in `DB-Postgres/README.md`. This page is the set of rules a changeset must satisfy.

## Layout

```
DB-Postgres/
  changelog/
    changelog-master.yaml           # includeAll of changes/, relativeToChangelogFile: true
    changes/
      001-extentions-and-configuration.sql
      002-init-schema.postgresql.sql
      003-add-base_categories.postgresql.sql
      004-similar_item.postgresql.sql
      005-country-and-currency.postgresql.sql
      006-item-claims.postgresql.sql
      007-claim-payment.postgresql.sql
      008-claim-unlock-limit.postgresql.sql
      009-shedlock.postgresql.sql
  liquibase.properties              # for the Maven plugin; not under src/, so it stays out of the jar
```

`changelog/` is a module-level folder, not `src/main/resources/`. The POM copies it onto the classpath as `changelog/` so Spring sees `classpath:changelog/changelog-master.yaml` and the Maven plugin sees `changelog/changelog-master.yaml` from the module root.

## Rules

**Changesets are formatted SQL.** The master changelog is YAML only because `includeAll` does not exist in the SQL format. A changeset file starts with the marker and names each changeset `author:NNN-description`:

```sql
--liquibase formatted sql

--changeset vasil:005-country-and-currency-1 splitStatements:false
ALTER TABLE place ADD COLUMN country_code VARCHAR(2);
```

Use the file number as the prefix of the changeset id so ids stay unique across files. Auto-generated ids like `1789398081101-1` (a timestamp) are accepted but rename them when you review the diff.

**The number is the execution order.** `includeAll` sorts by filename. Keep the prefix zero-padded and monotonic: `010-...`. Two files with the same number is a merge-conflict waiting to happen; if two branches both add `010`, the second one to merge renumbers.

**Keep the `.postgresql.sql` suffix** on generated and hand-written changesets alike. Liquibase refuses to serialize a diff into a file without the `*.databaseType.sql` shape, and keeping the convention for manual files means the folder reads uniformly.

**Never edit a changeset that has been applied anywhere** (your database, a colleague's, staging). Liquibase stores a checksum in `databasechangelog` and fails validation when it changes. Fix a mistake with a new changeset. Locally you can recreate the database if you must, but the file still goes in as a new one.

**The changelog path is the same string everywhere: `changelog/...`.** Liquibase identifies a changeset by `(id, author, filename)`. If the plugin resolves `changelog/changes/006-x.sql` and Spring resolves `DB-Postgres/changelog/changes/006-x.sql`, they are different changesets and the migration is applied twice. Do not add prefixes to `changeLogFile` in `liquibase.properties` or to `spring.liquibase.change-log`.

**Review every generated diff by hand.** `liquibase:diff` compares the entities with the live database through `liquibase-hibernate7` and gets these things wrong in this project:

| What | Generated | Correct |
|---|---|---|
| PostGIS column | `GEOGRAPHY point, 4326` or bare `GEOGRAPHY` | `geography(Point,4326)` |
| Enum array (`SocialMediaEnum[]`) | `SMALLINT` | `smallint[]` |
| Naming strategy missing | `ContactInfo`, `socialMedias` | `contact_info`, `social_medias` (already fixed by `hibernate.physical_naming_strategy` in `liquibase.properties`; check anyway) |
| PostGIS own tables | included unless excluded | excluded via `diffExcludeObjects` (`spatial_ref_sys`, `geometry_columns`, ...) |
| Indexes not declared in `@Table(indexes=...)` | not generated | write them by hand, e.g. the GiST index on `place.coordinate` |
| Foreign-key names | `FK18kanuyefq0emoqxhn2pocfc7` | acceptable, but a readable name is better when you touch the file anyway |

`ddl-auto: validate` in `DB-Postgres` and in the Testcontainers-backed tests catches most of these after the fact, so run `./mvnw -pl DB-Postgres spring-boot:run` after applying.

**Data migrations follow backfill, then constrain.** When a new column must be `NOT NULL`, add it nullable, update existing rows, then tighten. Migration `005` is the template:

```sql
ALTER TABLE place ADD COLUMN country_code VARCHAR(2);
UPDATE place SET country_code = 'UA' WHERE country_code IS NULL;
ALTER TABLE place ALTER COLUMN country_code SET NOT NULL;
```

Put the schema change and the backfill of one column into the same changeset so a partial failure does not leave the column half-constrained.

**Codes are `VARCHAR`, not `CHAR`.** `country_code VARCHAR(2)` and `currency VARCHAR(3)` on purpose: Hibernate schema validation does not accept PostgreSQL `bpchar` for a `char(n)` mapping.

**Repeatable objects use `runOnChange:true`.** Views, functions, text-search configurations and reference-data seeds that should be re-applied whenever their definition changes are declared as `--changeset vasil:name runOnChange:true`. `001` uses a precondition (`onFail:MARK_RAN`) for the Ukrainian text-search configuration instead, because `CREATE TEXT SEARCH CONFIGURATION` is not idempotent.

**Reference data is a migration.** Base categories are inserted by `003`; a new category is a new changeset, not a manual `INSERT`. See [add-a-category](../extending/add-a-category.md).

**Entities change in `DB-Postgres` first.** Then the same mapping is mirrored in every service that copies the entity. See the "Entities copied per module" section in [java-code-style](java-code-style.md).

## Tests run the real changelog

`Client-API`'s `AbstractPostgresTests` starts `postgis/postgis:17-3.5` in Testcontainers and applies `DB-Postgres/changelog/changelog-master.yaml` through the Liquibase API before the context starts. A migration that does not apply cleanly to an empty database fails `./mvnw -pl Client-API test`, which is the cheapest way to check a changeset before a review. The test harness skips the Ukrainian full-text configuration because the hunspell dictionary only exists in our custom Postgres image; see [testing](testing.md).

## Timezone

The PostgreSQL image we run rejects `Europe/Kiev` as a `TimeZone` value (the alias was dropped from its timezone data). If your JVM default is that zone, every connection (Liquibase included) fails with `invalid value for parameter "TimeZone"`. Run with `-Duser.timezone=Europe/Kyiv`, for example through `MAVEN_OPTS`.
