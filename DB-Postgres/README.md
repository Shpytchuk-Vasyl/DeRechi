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



docker exec derechi-postgres psql -U derechi -d derechi -c "
--liquibase formatted sql

--changeset vasil:001-enable-postgis
CREATE EXTENSION IF NOT EXISTS postgis;

--changeset vasil:001-ukrainian-fts
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM pg_ts_config WHERE cfgname = 'ukrainian'
-- Require dictionary uk_ua.dict/uk_ua.affix and ukrainian.stop у tsearch_data,
-- they come from docker/postgres/Dockerfile.
CREATE TEXT SEARCH DICTIONARY ukrainian_hunspell (
    TEMPLATE  = ispell,
    DictFile  = uk_ua,
    AffFile   = uk_ua,
    StopWords = ukrainian
);

CREATE TEXT SEARCH CONFIGURATION ukrainian (COPY = simple);

ALTER TEXT SEARCH CONFIGURATION ukrainian
    ALTER MAPPING FOR word, hword, hword_part
    WITH ukrainian_hunspell, simple;


--liquibase formatted sql

-- changeset vasil:1789241128433-1 splitStatements:false
CREATE TABLE contact_info
(
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    email         VARCHAR(50)                             NOT NULL,
    phone         VARCHAR(16)                             NOT NULL,
    social_medias INT2[],
    CONSTRAINT "contact_infoPK" PRIMARY KEY (id)
);

-- changeset vasil:1789241128433-2 splitStatements:false
CREATE TABLE found_item
(
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    compensation          INTEGER,
    date                  date                                    NOT NULL,
    description           VARCHAR(250),
    image                 VARCHAR(200),
    title                 VARCHAR(100)                            NOT NULL,
    category_id           BIGINT                                  NOT NULL,
    info_id               BIGINT                                  NOT NULL,
    place_google_place_id VARCHAR(255)                            NOT NULL,
    CONSTRAINT "found_itemPK" PRIMARY KEY (id)
);

-- changeset vasil:1789241128433-3 splitStatements:false
CREATE TABLE found_item_history
(
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    compensation          INTEGER,
    date                  date                                    NOT NULL,
    description           VARCHAR(250),
    image                 VARCHAR(200),
    title                 VARCHAR(100)                            NOT NULL,
    archived_at           TIMESTAMP(6) WITH TIME ZONE             NOT NULL,
    category_id           BIGINT                                  NOT NULL,
    info_id               BIGINT                                  NOT NULL,
    place_google_place_id VARCHAR(255)                            NOT NULL,
    CONSTRAINT "found_item_historyPK" PRIMARY KEY (id)
);

-- changeset vasil:1789241128433-4 splitStatements:false
CREATE TABLE lost_item
(
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    compensation          INTEGER,
    date                  date                                    NOT NULL,
    description           VARCHAR(250),
    image                 VARCHAR(200),
    title                 VARCHAR(100)                            NOT NULL,
    category_id           BIGINT                                  NOT NULL,
    info_id               BIGINT                                  NOT NULL,
    place_google_place_id VARCHAR(255)                            NOT NULL,
    CONSTRAINT "lost_itemPK" PRIMARY KEY (id)
);

-- changeset vasil:1789241128433-5 splitStatements:false
CREATE TABLE lost_item_history
(
    id                    BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    compensation          INTEGER,
    date                  date                                    NOT NULL,
    description           VARCHAR(250),
    image                 VARCHAR(200),
    title                 VARCHAR(100)                            NOT NULL,
    archived_at           TIMESTAMP(6) WITH TIME ZONE             NOT NULL,
    category_id           BIGINT                                  NOT NULL,
    info_id               BIGINT                                  NOT NULL,
    place_google_place_id VARCHAR(255)                            NOT NULL,
    CONSTRAINT "lost_item_historyPK" PRIMARY KEY (id)
);

-- changeset vasil:1789241128433-6 splitStatements:false
CREATE TABLE place
(
    google_place_id VARCHAR(255)           NOT NULL,
    coordinate      geography(Point,4326)  NOT NULL,
    name            VARCHAR(100)           NOT NULL,
    CONSTRAINT "placePK" PRIMARY KEY (google_place_id)
);

-- changeset vasil:1789241128433-7 splitStatements:false
CREATE TABLE thing_category
(
    id  BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    key VARCHAR(100)                            NOT NULL,
    CONSTRAINT "thing_categoryPK" PRIMARY KEY (id)
);

-- changeset vasil:1789241128433-8 splitStatements:false
ALTER TABLE thing_category
    ADD CONSTRAINT UC_THING_CATEGORYKEY_COL UNIQUE (key);

-- changeset vasil:1789241128433-9 splitStatements:false
ALTER TABLE lost_item
    ADD CONSTRAINT "FK6yioqpc7m1i0yg1mbdwbxun6e" FOREIGN KEY (info_id) REFERENCES contact_info (id);

-- changeset vasil:1789241128433-10 splitStatements:false
ALTER TABLE lost_item_history
    ADD CONSTRAINT "FK70u4k5cj68cb23u7fvdh7nlq5" FOREIGN KEY (info_id) REFERENCES contact_info (id);

-- changeset vasil:1789241128433-11 splitStatements:false
ALTER TABLE found_item_history
    ADD CONSTRAINT "FKapejpe6140stocrjwcq9wb3b" FOREIGN KEY (category_id) REFERENCES thing_category (id);

-- changeset vasil:1789241128433-12 splitStatements:false
ALTER TABLE found_item_history
    ADD CONSTRAINT "FKcs76sy8jr8ypp33h4w402msdk" FOREIGN KEY (place_google_place_id) REFERENCES place (google_place_id);

-- changeset vasil:1789241128433-13 splitStatements:false
ALTER TABLE found_item_history
    ADD CONSTRAINT "FKgle1qi8qlcbop56biww8mcug9" FOREIGN KEY (info_id) REFERENCES contact_info (id);

-- changeset vasil:1789241128433-14 splitStatements:false
ALTER TABLE lost_item_history
    ADD CONSTRAINT "FKhclnxrno7ev3ycck61p9vscxs" FOREIGN KEY (place_google_place_id) REFERENCES place (google_place_id);

-- changeset vasil:1789241128433-15 splitStatements:false
ALTER TABLE found_item
    ADD CONSTRAINT "FKmex47eta3w3naaedje44jd8k6" FOREIGN KEY (category_id) REFERENCES thing_category (id);

-- changeset vasil:1789241128433-16 splitStatements:false
ALTER TABLE lost_item
    ADD CONSTRAINT "FKmh6ft2w76omp60na9ffqtkrmm" FOREIGN KEY (place_google_place_id) REFERENCES place (google_place_id);

-- changeset vasil:1789241128433-17 splitStatements:false
ALTER TABLE found_item
    ADD CONSTRAINT "FKnuj06duc2lxbi2nog3cr8hg1s" FOREIGN KEY (place_google_place_id) REFERENCES place (google_place_id);

-- changeset vasil:1789241128433-18 splitStatements:false
ALTER TABLE lost_item
    ADD CONSTRAINT "FKp24qbicp8qxo1fdvpxiucybt8" FOREIGN KEY (category_id) REFERENCES thing_category (id);

-- changeset vasil:1789241128433-19 splitStatements:false
ALTER TABLE lost_item_history
    ADD CONSTRAINT "FKrto5ucuhr3o58cq45ng73vmkt" FOREIGN KEY (category_id) REFERENCES thing_category (id);

-- changeset vasil:1789241128433-20 splitStatements:false
ALTER TABLE found_item
    ADD CONSTRAINT "FKtd85og5xhg33q75jpviquunmf" FOREIGN KEY (info_id) REFERENCES contact_info (id);



--liquibase formatted sql

--changeset vasil:003-add-base-categories
INSERT INTO  thing_category(key)
values ('DOCUMENTS'), ('WALLET'), ('ELECTRONICS'), ('JEWELRY'), ('ANIMALS'), ('KEYS'), ('BAGS'), ('OTHER');


-- liquibase formatted sql

-- changeset vasil:1789398081101-1 splitStatements:false
CREATE TABLE similar_item
(
    found_item_id BIGINT           NOT NULL,
    lost_item_id  BIGINT           NOT NULL,
    match_order   DOUBLE PRECISION NOT NULL,
    notified_at   TIMESTAMP(6) WITH TIME ZONE,
    notified_by   VARCHAR(100),

    CONSTRAINT "similar_itemPK" PRIMARY KEY (found_item_id, lost_item_id)
);

-- changeset vasil:1789398081101-2 splitStatements:false
ALTER TABLE similar_item
    ADD CONSTRAINT "FK18kanuyefq0emoqxhn2pocfc7" FOREIGN KEY (lost_item_id) REFERENCES lost_item (id);

-- changeset vasil:1789398081101-3 splitStatements:false
ALTER TABLE similar_item
    ADD CONSTRAINT "FKi7nq8pfbxq1vy93mfrcu14v95" FOREIGN KEY (found_item_id) REFERENCES found_item (id);


-- liquibase formatted sql

-- country (ISO 3166-1 alpha-2), currency (ISO 4217).

-- changeset vasil:005-country-and-currency-1 splitStatements:false
ALTER TABLE place
    ADD COLUMN country_code VARCHAR(2);

UPDATE place
SET country_code = 'UA'
WHERE country_code IS NULL;

ALTER TABLE place
    ALTER COLUMN country_code SET NOT NULL;

-- changeset vasil:005-country-and-currency-2 splitStatements:false
ALTER TABLE lost_item
    ADD COLUMN currency VARCHAR(3);
ALTER TABLE found_item
    ADD COLUMN currency VARCHAR(3);
ALTER TABLE lost_item_history
    ADD COLUMN currency VARCHAR(3);
ALTER TABLE found_item_history
    ADD COLUMN currency VARCHAR(3);

UPDATE lost_item
SET currency = 'UAH'
WHERE currency IS NULL;
UPDATE found_item
SET currency = 'UAH'
WHERE currency IS NULL;
UPDATE lost_item_history
SET currency = 'UAH'
WHERE currency IS NULL;
UPDATE found_item_history
SET currency = 'UAH'
WHERE currency IS NULL;

ALTER TABLE lost_item
    ALTER COLUMN currency SET NOT NULL;
ALTER TABLE found_item
    ALTER COLUMN currency SET NOT NULL;
ALTER TABLE lost_item_history
    ALTER COLUMN currency SET NOT NULL;
ALTER TABLE found_item_history
    ALTER COLUMN currency SET NOT NULL;


-- liquibase formatted sql

-- changeset vasil:006-item-claims-1 splitStatements:false
CREATE TABLE lost_item_claim
(
    id                   BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    item_id              BIGINT,
    archived_item_id     BIGINT,
    contact_info_id      BIGINT                                  NOT NULL,
    token                VARCHAR(36)                             NOT NULL,
    created_at           TIMESTAMP(6) WITH TIME ZONE             NOT NULL,
    author_reminded_at   TIMESTAMP(6) WITH TIME ZONE,
    claimant_reminded_at TIMESTAMP(6) WITH TIME ZONE,
    confirmed_at         TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT "lost_item_claimPK" PRIMARY KEY (id),
    CONSTRAINT lost_item_claim_token_uq UNIQUE (token),
    CONSTRAINT lost_item_claim_one_item_ck CHECK (num_nonnulls(item_id, archived_item_id) = 1)
);

-- changeset vasil:006-item-claims-2 splitStatements:false
CREATE TABLE found_item_claim
(
    id                   BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    item_id              BIGINT,
    archived_item_id     BIGINT,
    contact_info_id      BIGINT                                  NOT NULL,
    token                VARCHAR(36)                             NOT NULL,
    created_at           TIMESTAMP(6) WITH TIME ZONE             NOT NULL,
    author_reminded_at   TIMESTAMP(6) WITH TIME ZONE,
    claimant_reminded_at TIMESTAMP(6) WITH TIME ZONE,
    confirmed_at         TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT "found_item_claimPK" PRIMARY KEY (id),
    CONSTRAINT found_item_claim_token_uq UNIQUE (token),
    CONSTRAINT found_item_claim_one_item_ck CHECK (num_nonnulls(item_id, archived_item_id) = 1)
);

-- changeset vasil:006-item-claims-3 splitStatements:false
ALTER TABLE lost_item_claim
    ADD CONSTRAINT lost_item_claim_item_fk FOREIGN KEY (item_id) REFERENCES lost_item (id);
ALTER TABLE lost_item_claim
    ADD CONSTRAINT lost_item_claim_archived_item_fk FOREIGN KEY (archived_item_id) REFERENCES lost_item_history (id);
ALTER TABLE lost_item_claim
    ADD CONSTRAINT lost_item_claim_contact_info_fk FOREIGN KEY (contact_info_id) REFERENCES contact_info (id);
CREATE INDEX lost_item_claim_item_idx ON lost_item_claim (item_id);
CREATE INDEX lost_item_claim_archived_item_idx ON lost_item_claim (archived_item_id);

-- changeset vasil:006-item-claims-4 splitStatements:false
ALTER TABLE found_item_claim
    ADD CONSTRAINT found_item_claim_item_fk FOREIGN KEY (item_id) REFERENCES found_item (id);
ALTER TABLE found_item_claim
    ADD CONSTRAINT found_item_claim_archived_item_fk FOREIGN KEY (archived_item_id) REFERENCES found_item_history (id);
ALTER TABLE found_item_claim
    ADD CONSTRAINT found_item_claim_contact_info_fk FOREIGN KEY (contact_info_id) REFERENCES contact_info (id);
CREATE INDEX found_item_claim_item_idx ON found_item_claim (item_id);
CREATE INDEX found_item_claim_archived_item_idx ON found_item_claim (archived_item_id);


"




docker exec derechi-postgres psql -U derechi -d derechi -c "

-- liquibase formatted sql

-- changeset vasil:007-claim-payment-1 splitStatements:false
ALTER TABLE lost_item_claim
    ADD COLUMN payment_product_id VARCHAR(64),
    ADD COLUMN payment_variant_id VARCHAR(64),
    ADD COLUMN paid_at            TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN contacts_sent_at   TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE found_item_claim
    ADD COLUMN payment_product_id VARCHAR(64),
    ADD COLUMN payment_variant_id VARCHAR(64),
    ADD COLUMN paid_at            TIMESTAMP(6) WITH TIME ZONE,
    ADD COLUMN contacts_sent_at   TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE lost_item_claim
    ADD CONSTRAINT lost_item_claim_payment_variant_id_uq UNIQUE (payment_variant_id);
ALTER TABLE found_item_claim
    ADD CONSTRAINT found_item_claim_payment_variant_id_uq UNIQUE (payment_variant_id);

-- changeset vasil:007-claim-payment-2 splitStatements:false
CREATE TABLE fourthwall_order
(
    id                  BIGINT GENERATED BY DEFAULT AS IDENTITY NOT NULL,
    order_id            VARCHAR(64)                             NOT NULL,
    friendly_id         VARCHAR(64),
    status              VARCHAR(40)                             NOT NULL,
    email               VARCHAR(100),
    username            VARCHAR(100),
    amount              NUMERIC(10, 2)                          NOT NULL,
    currency            VARCHAR(3)                              NOT NULL,
    test_mode           BOOLEAN                                 NOT NULL,
    placed_at           TIMESTAMP(6) WITH TIME ZONE             NOT NULL,
    received_at         TIMESTAMP(6) WITH TIME ZONE             NOT NULL,
    lost_item_claim_id  BIGINT,
    found_item_claim_id BIGINT,
    CONSTRAINT "fourthwall_orderPK" PRIMARY KEY (id),
    CONSTRAINT fourthwall_order_order_id_uq UNIQUE (order_id)
);

-- changeset vasil:007-claim-payment-3 splitStatements:false
ALTER TABLE fourthwall_order
    ADD CONSTRAINT fourthwall_order_lost_item_claim_fk FOREIGN KEY (lost_item_claim_id) REFERENCES lost_item_claim (id) ON DELETE SET NULL;
ALTER TABLE fourthwall_order
    ADD CONSTRAINT fourthwall_order_found_item_claim_fk FOREIGN KEY (found_item_claim_id) REFERENCES found_item_claim (id) ON DELETE SET NULL;
CREATE INDEX fourthwall_order_lost_item_claim_idx ON fourthwall_order (lost_item_claim_id);
CREATE INDEX fourthwall_order_found_item_claim_idx ON fourthwall_order (found_item_claim_id);

"




docker exec derechi-postgres psql -U derechi -d derechi -c "
-- liquibase formatted sql

-- changeset vasil:008-claim-unlock-limit-1 splitStatements:false
ALTER TABLE lost_item_claim
    ADD COLUMN payment_requested_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE found_item_claim
    ADD COLUMN payment_requested_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE lost_item_claim SET payment_requested_at = created_at WHERE payment_product_id IS NOT NULL;
UPDATE found_item_claim SET payment_requested_at = created_at WHERE payment_product_id IS NOT NULL;

-- changeset vasil:008-claim-unlock-limit-2 splitStatements:false
CREATE INDEX lost_item_claim_payment_requested_idx ON lost_item_claim (payment_requested_at)
    WHERE payment_requested_at IS NOT NULL;
CREATE INDEX found_item_claim_payment_requested_idx ON found_item_claim (payment_requested_at)
    WHERE payment_requested_at IS NOT NULL;
"