# Add a category

Categories (`thing_category`) are reference data: an id and an upper-case key such as `DOCUMENTS` or `KEYS`. They are seeded by migration, labelled through the message bundles, and used as the first filter of automatic matching. The key also travels far beyond the table: the admin and the site label it, the site draws an icon for it and words the claim card after it, and `Ingest` lets the model answer with it.

## Before you start

Read [Notices](../features/notices.md) and [Database migrations](../conventions/database-migrations.md). The rows come from `DB-Postgres/changelog/changes/003-add-base_categories.postgresql.sql`: `DOCUMENTS`, `WALLET`, `ELECTRONICS`, `JEWELRY`, `ANIMALS`, `KEYS`, `BAGS`, `OTHER`.

The example adds `CLOTHING`. It is half there already: labels in `Admin-API`, `Ingest` and `Web-Client`, an icon and claim wording on the site, but no row and no place in the `Ingest` schema, so nobody can pick it yet. The walkthrough makes it real; for a brand-new key do every step.

## Steps

1. **Migration.** Never edit `003`; applied changesets are checksummed. Add a new formatted-SQL file with the next number (`015` is the last one today):

   ```sql
   -- DB-Postgres/changelog/changes/016-add-clothing-category.postgresql.sql
   --liquibase formatted sql

   --changeset vasil:016-add-clothing-category
   INSERT INTO thing_category (key) VALUES ('CLOTHING');
   ```

   Keys are upper-case identifiers with no spaces; they appear verbatim in message keys, in ICU `select` branches and in the GraphQL `Category.key`. Apply it with `./mvnw -pl DB-Postgres liquibase:update`. `key` is `VARCHAR(100)` with a unique constraint (`UC_THING_CATEGORYKEY_COL`), so inserting an existing key fails the changeset rather than creating a duplicate.

2. **Admin labels.** Templates render categories as `#{category.__${category.key}__}` (filters, dialogs, form). Add `category.CLOTHING=...` to all five bundles in `Admin-API/src/main/resources/`; `MessagesTest` enforces parity.

3. **Admin cache.** `ThingCategoryRepository.findAll` is `@Cacheable("categories")` with a one-hour expiry. After applying the migration either wait or restart `Admin-API`; the running instance will not see the row before that.

4. **Client-API needs nothing.** `categories { id key }` reads the table on every call (no cache there) and returns the key; translation is the client's job.

5. **Worker needs nothing.** Matching only compares `category.id`; a new category simply forms its own pool.

6. **Ingest.** The model answers through a strict JSON schema, so it can only pick a key listed in `category.enum` of `Ingest/src/main/resources/prompts/extraction-schema.json`; a key missing there never comes out of an imported post. Add it before `OTHER`. The channel post ends with a category hashtag, `channel.hashtag.category.CLOTHING`, in `messages.properties` and `messages_uk.properties`.

7. **Web-Client label.** `category.CLOTHING` in all five `Web-Client/messages/*.json` (`src/i18n/messages.test.ts` checks the key sets). Without it the filters, the badge and the meta description show the raw key.

8. **Web-Client art.** An icon (from `lucide`) and a tone in `ART` of `Web-Client/src/components/items/category-art.tsx`. It stands in for a missing photo on the cards, on the notice page and in the share and OG cards. A key without an entry gets the box of `OTHER` (`Package`, tone `idle`).

9. **Web-Client claim wording.** The claim card names the thing instead of saying "річ": "Це ваші ключі?" with "Це моє, надіслати мої контакти" on a found notice, "Ви знайшли цей гаманець?" with "Гаманець у мене, надіслати мої контакти" on a lost one. Four messages choose the wording with an ICU `select` on the category key:

   | Message | Shown on | Generic `other` (uk) |
   |---|---|---|
   | `item.isItYours` | found notice, the question | Це ваша річ? |
   | `claim.sendMine` | found notice, the button | Це моє, надіслати мої контакти |
   | `item.didYouFind` | lost notice, the question | Ви знайшли цю річ? |
   | `claim.sendFound` | lost notice, the button | Річ у мене, надіслати мої контакти |

   Add a `CLOTHING {...}` branch, in each of the five bundles, wherever that language's `other` text reads wrong for the new thing:

   ```json
   "isItYours": "{category, select, DOCUMENTS {Це ваші документи?} ... CLOTHING {Це ваш одяг?} BAGS {Це ваша сумка?} other {Це ваша річ?}}"
   ```

   A key without a branch falls back to `other`, so skipping this step never breaks the card: it only reads generic. Not every message needs a branch in every language: `claim.sendMine` ("Це моє") fits any object in Ukrainian and Polish, so there only `ANIMALS` has one. What does need care is agreement inside the branch, because the text around it is shared:
   - Ukrainian "у/в" after the noun: "Сумка в мене", "Гаманець у мене";
   - plurals: "Sind das deine Schlüssel?", "They're mine", "Gehören mir";
   - the French participle agrees with the noun: "Je les ai trouvées" (clés), "Je les ai trouvés" (documents);
   - animals are not "it": "Це мій улюбленець", "Das Tier ist bei mir".

   The other claim texts (the explanation under the question, the message after sending, the hint after publishing, the confirmation page `/claims/<token>`) are written without naming the thing ("тому, хто знайшов", "власнику", "Усе повернулося до власника?") and take no `{category}`. Keep new claim texts that way rather than adding a `select` to them: the confirmation page does not even know the category.

10. **Guide (optional).** A "what to do if you lost X" page lives in `Web-Client/src/content/guides` (`GUIDE_SLUGS`, `GUIDE_META.<slug>.category`). A category without a guide simply has no guide link on its notices.

11. **Removing or renaming** a category is a different job: `thing.category_id` is `NOT NULL` with a foreign key, so rows must be re-pointed first, and renaming a key breaks the labels, the `select` branches, the `Ingest` enum and every client that stored the old key.

## Tests to add or update

- `ReferenceControllerTests.returnsTheCategoriesSeededByTheMigrations` in `Client-API` runs the real changelog in Testcontainers and asserts the seeded keys; add the new one.
- `AbstractGraphQlTests` looks up `DOCUMENTS` and `WALLET` by key for fixtures; nothing to change unless you removed one.
- `MessagesTest` in `Admin-API` and `src/i18n/messages.test.ts` in `Web-Client` for the labels. The Web-Client one also compares the arguments of every message across bundles, so a `select` must be in all five or in none.
- `ExtractionSchemaTest` in `Ingest` lists the schema's category enum exactly.
- E2E: `CategoryKey` and `ITEM_NAMES` in `Web-Client/e2e/support/data.ts` (a notice name per language; `tsc` asks for all five). The "claim wording" test in `e2e/specs/claim.spec.ts` opens shared notices of several categories and expects each one's question and button. To cover the new category, add a `view` notice with it in `e2e/support/shared.ts` (a new definition is simply created on the next run) and put it in that test's list.
- Load tests seed titles per category (`tests/load/lib/data.js`, `tests/load/seed/seed.sql`); add the key if the load data should include it.

## Migration needed?

Yes, the insert above. No schema change.

## Checklist

- [ ] New numbered changeset with the insert, `003` untouched
- [ ] `liquibase:update` applied locally, `spring-boot:run` of `DB-Postgres` still validates
- [ ] `category.<KEY>` in the five `Admin-API` bundles, `MessagesTest` green
- [ ] `ReferenceControllerTests` expectations updated
- [ ] Admin-API restarted or cache expired before checking the UI
- [ ] `Ingest`: key in the schema's `category.enum` and `ExtractionSchemaTest`, `channel.hashtag.category.<KEY>` in both bundles
- [ ] `Web-Client`: `category.<KEY>` in the five bundles, an entry in `category-art.tsx`
- [ ] `Web-Client`: a `<KEY>` branch in `item.isItYours`, `item.didYouFind`, `claim.sendMine`, `claim.sendFound` wherever `other` reads wrong, in every language
- [ ] E2E: `CategoryKey` and `ITEM_NAMES`, and a shared notice in the "claim wording" test if the wording has branches
