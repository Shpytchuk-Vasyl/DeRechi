# Add a category

Categories (`thing_category`) are reference data: an id and an upper-case key such as `DOCUMENTS` or `KEYS`. They are seeded by migration, labelled through the message bundles, and used as the first filter of automatic matching.

## Before you start

Read [Notices](../features/notices.md) and [Database migrations](../conventions/database-migrations.md). The current set comes from `DB-Postgres/changelog/changes/003-add-base_categories.postgresql.sql`: `DOCUMENTS`, `WALLET`, `ELECTRONICS`, `JEWELRY`, `ANIMALS`, `KEYS`, `BAGS`, `OTHER`.

The example adds `CLOTHING`. (The admin bundle already has a `category.CLOTHING` label without a matching row; this walkthrough would make it real.)

## Steps

1. **Migration.** Never edit `003`; applied changesets are checksummed. Add a new formatted-SQL file with the next number:

   ```sql
   -- DB-Postgres/changelog/changes/006-add-clothing-category.postgresql.sql
   --liquibase formatted sql

   --changeset vasil:006-add-clothing-category
   INSERT INTO thing_category (key) VALUES ('CLOTHING');
   ```

   Keys are upper-case identifiers with no spaces; they appear verbatim in message keys and in the GraphQL `Category.key`. Apply it with `./mvnw -pl DB-Postgres liquibase:update`. `key` is `VARCHAR(100)` with a unique constraint (`UC_THING_CATEGORYKEY_COL`), so inserting an existing key fails the changeset rather than creating a duplicate.

2. **Admin labels.** Templates render categories as `#{category.__${category.key}__}` (filters, dialogs, form). Add `category.CLOTHING=...` to all five bundles in `Admin-API/src/main/resources/`; `MessagesTest` enforces parity.

3. **Admin cache.** `ThingCategoryRepository.findAll` is `@Cacheable("categories")` with a one-hour expiry. After applying the migration either wait or restart `Admin-API`; the running instance will not see the row before that.

4. **Client-API needs nothing.** `categories { id key }` reads the table on every call (no cache there) and returns the key; translation is the client's job.

5. **Automatic-Search needs nothing.** Matching only compares `category.id`; a new category simply forms its own pool.

6. **Removing or renaming** a category is a different job: `thing.category_id` is `NOT NULL` with a foreign key, so rows must be re-pointed first, and renaming a key breaks the message lookup and every client that stored the old key.

## Tests to add or update

- `ReferenceControllerTests.returnsTheCategoriesSeededByTheMigrations` in `Client-API` runs the real changelog in Testcontainers and asserts the seeded keys; add the new one.
- `AbstractGraphQlTests` looks up `DOCUMENTS` and `WALLET` by key for fixtures; nothing to change unless you removed one.
- `MessagesTest` for the label.

## Migration needed?

Yes, the insert above. No schema change.

## Web-Client impact

The web client gets the key from `categories` and needs a label for it in `Web-Client/messages/*.json` (and an icon if the design uses one). Until then the key shows raw.

## Checklist

- [ ] New numbered changeset with the insert, `003` untouched
- [ ] `liquibase:update` applied locally, `spring-boot:run` of `DB-Postgres` still validates
- [ ] `category.<KEY>` in five bundles, `MessagesTest` green
- [ ] `ReferenceControllerTests` expectations updated
- [ ] Admin-API restarted or cache expired before checking the UI
- [ ] Web-Client team informed of the new key
