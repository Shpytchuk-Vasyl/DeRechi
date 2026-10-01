# Database

## One database, on purpose

All services share a single PostgreSQL database called `derechi`. We decided against a
database per service early: the domain is small, every service needs the same handful of
tables, and the cost of keeping copies in sync across services would have been paid for
nothing. If that ever changes, this page is where to start the discussion.

Keycloak is the exception. It manages its own schema with its own migrations, so it lives in
a separate database, `keycloak`, inside the same PostgreSQL container. The database is
created by `docker/postgres/initdb/01-keycloak.sql` on the container's first start. The rule
"the schema is owned by DB-Postgres" does not apply to it.

## Who owns the schema

Only the `DB-Postgres` module:

- the JPA entities that describe the schema live in `DB-Postgres/src/main/java`;
- migrations live in `DB-Postgres/changelog/changes/` as Liquibase formatted SQL;
- the module is run by hand to apply migrations. It is a library, not a service, and is
  never started in Docker.

Every other service runs with `spring.jpa.hibernate.ddl-auto: none` and does not include
Liquibase at all: the Boot starter is not on their classpath, so there is nothing to disable.
`Client-API` and `Admin-API` have `liquibase-core` in test scope only, so their
Testcontainers tests can apply the `DB-Postgres` changelog to a fresh container.

Nobody depends on `DB-Postgres`. Each service has its own copy of the entity classes in its
own `entity` package (`org.shpytchuk.clientapi.entity`, `org.shpytchuk.adminapi.entity`,
`org.shpytchuk.automaticsearch.entity`). The copies can differ: `Automatic-Search` has a
transient `orderMatch` field on `Thing` for ranking, `Client-API` has no history entities.
What they share is the table layout, not the Java types. When a column is added, every copy
that reads it gets updated; a copy that does not care can ignore the column.

The procedure for changing the schema is in
[../processes/database-changes.md](../processes/database-changes.md), and the rules for
writing a changeset are in
[../conventions/database-migrations.md](../conventions/database-migrations.md).

## Tables

Column types below are what the migrations create. Hibernate validates against them in
`DB-Postgres` (`ddl-auto: validate`) and in the integration tests.

| Table | What it holds | Notable columns |
|---|---|---|
| `thing_category` | reference list of categories | `key VARCHAR(100) UNIQUE`; seeded by migration 003 with `DOCUMENTS`, `WALLET`, `ELECTRONICS`, `JEWELRY`, `ANIMALS`, `KEYS`, `BAGS`, `OTHER` |
| `place` | a Google Places location | PK `google_place_id`, `name`, `coordinate geography(Point,4326) NOT NULL`, `country_code VARCHAR(2) NOT NULL` (ISO 3166-1 alpha-2, added in 005) |
| `contact_info` | how to reach the person who posted | `phone VARCHAR(16)`, `email VARCHAR(50)`, `social_medias INT2[]` (ordinals of `SocialMediaEnum`, nullable) |
| `lost_item` | a lost notice | `title VARCHAR(100)`, `description VARCHAR(250)`, `image VARCHAR(200)` (object key in MinIO), `date`, `compensation INTEGER` (whole units, nullable), `currency VARCHAR(3) NOT NULL` (ISO 4217), FKs to category, contact info and place |
| `found_item` | a found notice | same shape as `lost_item`; the application requires `image` for found items, the schema does not |
| `lost_item_history`, `found_item_history` | archived notices | same columns plus `archived_at TIMESTAMPTZ NOT NULL`; rows are copied here by `Automatic-Search` (`ItemArchiver`) when an administrator asks for the archive, a return is confirmed or a notice with claims goes quiet, and the original row is deleted |
| `lost_item_claim`, `found_item_claim` | responses to a notice ("it's mine" / "I found it") | `item_id` (the live notice) or `archived_item_id` (its history copy), exactly one by `CHECK`, `contact_info_id` (the claimant's contacts), `token VARCHAR(36)` unique (reminder links), `created_at`, `author_reminded_at`, `claimant_reminded_at`, `confirmed_at`. Archiving a notice re-points its claims at the history row; they are deleted with their contact infos a year after creation or when the notice is deleted outright; see [../features/claims.md](../features/claims.md) |
| `similar_item` | candidate matches produced by `Automatic-Search` | PK `(found_item_id, lost_item_id)`, `match_order DOUBLE PRECISION` (full-text rank, higher is better), `notified_at TIMESTAMPTZ`, `notified_by VARCHAR(100)` (admin username) |

Two type choices are deliberate:

- `country_code` and `currency` are `VARCHAR`, not `CHAR`. Hibernate's schema validation
  does not accept PostgreSQL's `bpchar` as `char(n)`, and `ddl-auto: validate` would refuse
  to start.
- `social_medias` is a real array column (`smallint[]`), mapped with
  `@JdbcTypeCode(SqlTypes.ARRAY)` and `@Enumerated(EnumType.ORDINAL)`. Liquibase's diff
  emits a scalar `SMALLINT` for it, so that part of a generated changeset is always fixed by
  hand.

## Extensions and text search

Migration 001 enables PostGIS and creates a `ukrainian` text search configuration from the
hunspell dictionary that `docker/postgres/Dockerfile` bakes into the image. The
configuration is guarded by a precondition so the changeset is marked as run on a database
where it already exists.

`Automatic-Search` ranks candidates with `ts_rank(to_tsvector('<config>', ...), ...)`, where
`<config>` is one of PostgreSQL's built-in configurations (`english`, `german`, `french`,
`spanish`, `italian`, `russian`), our `ukrainian`, or `simple` as the fallback. See
[modules/automatic-search.md](modules/automatic-search.md).

PostGIS owns a few catalog-like tables (`spatial_ref_sys`, `geometry_columns`, ...). They
are excluded from Liquibase diffs through `diffExcludeObjects` in `liquibase.properties`,
and `liquibase:dropAll` does not work on this database because it cannot drop them. To
reset a development database, drop and recreate it; the commands are in
`DB-Postgres/README.md`.

## Connections

| Who | How | Config |
|---|---|---|
| `Client-API`, `Admin-API`, `Automatic-Search` | Hikari pool, JPA | `spring.datasource.*`, overridden in containers by `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` |
| `DB-Postgres` | JPA with `ddl-auto: validate` + Liquibase at startup | `src/main/resources/application.yaml` for Spring, `liquibase.properties` for the Maven plugin |
| Keycloak | its own JDBC pool | `KC_DB_URL=jdbc:postgresql://postgres:5432/keycloak` |
| `Notification`, `Getaway`, `Discovery` | no database | |

Development credentials are `derechi` / `derechi` on `localhost:5432`.

If the JVM's default timezone is `Europe/Kiev`, connections fail with
`invalid value for parameter "TimeZone"`; use `Europe/Kyiv`. The fix for Maven runs is in
[../developer-guide/troubleshooting.md](../developer-guide/troubleshooting.md).
