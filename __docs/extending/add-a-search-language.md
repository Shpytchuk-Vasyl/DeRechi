# Add a search language

A new language for the full-text ranking in `Worker`. This decides how a notice title is stemmed and matched; it has nothing to do with the UI languages (see [Add a language](add-a-language.md)).

## Before you start

Read [Automatic matching](../features/automatic-matching.md). Two things have to exist for a language: a PostgreSQL text search configuration (`regconfig`) and a detection profile in the optimaize `language-detector` library.

Check what PostgreSQL offers out of the box:

```sql
SELECT cfgname FROM pg_ts_config ORDER BY 1;
```

The stock image ships snowball configs for most Western European languages plus `russian`; `ukrainian` is ours (hunspell, built in `docker/postgres/Dockerfile` and migration `001`). Polish is **not** in the list, which is why it is detect-only today.

Two cases follow.

## Case 1: PostgreSQL already has the config (example: Portuguese)

1. **Enum.** `Worker/src/main/java/org/shpytchuk/worker/language/SearchLanguage.java`:

   ```java
   PORTUGUESE("pt", "portuguese"),
   ```

   Keep `SIMPLE` last. `isoCodes()` and `fromIsoCode` are derived from the enum.

2. **Rank function.** Nothing to do: `FullTextFunctionContributor` loops over `SearchLanguage.values()` and registers `ts_rank_portuguese` as an HQL function at startup.

3. **Detection.** Nothing to do either: `LanguageResolver` reads a built-in optimaize profile for every `SearchLanguage.isoCodes()` entry plus `DETECT_ONLY_CODES`. If the library has no profile for the code, `readBuiltIn` throws at startup; the bundled profiles cover roughly 70 languages, so this is rare.

4. **Verify** with a notice in that language: the log line from `LostItemCreatedHandler` or `FoundItemCreatedHandler` and the `match_order` values in `similar_item` should show non-zero ranks for titles that share stems.

## Case 2: PostgreSQL has no config (example: Polish)

1. **Dictionary.** Extend `docker/postgres/Dockerfile`: download a hunspell dictionary for the language in the `dict` stage, rename to `pl_pl.dict` and `pl_pl.affix` (PostgreSQL insists on those extensions, lower case), and `COPY` them into `tsearch_data` next to the Ukrainian files. Add a stop-word file (`polish.stop`) alongside `ukrainian.stop`.

2. **Migration.** New changeset in `DB-Postgres/changelog/changes/`, modelled on `001-ukrainian-fts`:

   ```sql
   --changeset vasil:006-polish-fts
   --preconditions onFail:MARK_RAN onError:HALT
   --precondition-sql-check expectedResult:0 SELECT count(*) FROM pg_ts_config WHERE cfgname = 'polish'
   CREATE TEXT SEARCH DICTIONARY polish_hunspell (
       TEMPLATE = ispell, DictFile = pl_pl, AffFile = pl_pl, StopWords = polish);
   CREATE TEXT SEARCH CONFIGURATION polish (COPY = simple);
   ALTER TEXT SEARCH CONFIGURATION polish
       ALTER MAPPING FOR word, hword, hword_part WITH polish_hunspell, simple;
   ```

   The precondition lets the changeset mark itself as run on a database that already has the config (the tests create a stub, see below).

3. **Rebuild the image.** `docker compose build postgres && docker compose up -d postgres`; the dictionary files must be in the container before `liquibase:update` creates the dictionary, otherwise PostgreSQL reports a missing file.

4. **Enum.** Add `POLISH("pl", "polish")` to `SearchLanguage` and remove `"pl"` from `LanguageResolver.DETECT_ONLY_CODES`, otherwise the profile is loaded twice.

5. **Tests.** `Client-API`'s `AbstractPostgresTests` runs the real changelog against a stock `postgis/postgis` container, which has no hunspell files. It pre-creates `ukrainian` as `COPY = simple` in `skipUkrainianFullTextSearch()` so the `001` precondition marks the changeset as run. Do the same for the new config in that method (and in any other module's test base that migrates), or the migration fails in CI.

## Tests to add or update

`Worker` has only a context test today. A worthwhile addition for either case: a `@SpringBootTest` on a Testcontainers PostGIS database (copy the pattern from `Client-API`'s `AbstractPostgresTests`) that inserts two found items and one event and asserts the ranking order for a title in the new language. For case 2 the test container needs the dictionary too, so the test either builds from `docker/postgres` or stubs the config and only checks that detection resolves to the new enum constant.

## Migration needed?

Case 1: no. Case 2: yes, the text search configuration, and a rebuilt PostgreSQL image.

## Web-Client impact

None. Search language is internal to matching.

## Checklist

- [ ] `pg_ts_config` has the regconfig (or the dictionary, migration and image rebuild are done)
- [ ] `SearchLanguage` constant added, `DETECT_ONLY_CODES` cleaned up
- [ ] `Worker` starts (profile found, functions registered)
- [ ] Test bases that run the changelog stub the new config if it needs files
- [ ] Ranking verified with a notice in the language
