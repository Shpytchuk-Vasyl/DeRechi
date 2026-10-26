# Add a search language

A new language a notice can be written in, as far as full-text search is concerned. It decides what language a notice's text is detected as when it is written ([Notices](../features/notices.md#language)), how its words are stemmed in the public search in `Client-API` ([Search and filtering](../features/search-and-filtering.md#full-text-search)) and in matching in `Worker` ([Automatic matching](../features/automatic-matching.md#language)). It has nothing to do with the UI languages (see [Add a language](add-a-language.md)); a search language that is also a site locale only changes which locale page of the notice is indexed, through `itemLocale` in the Web-Client.

## Before you start

Two things have to exist for a language: a PostgreSQL text search configuration (`regconfig`) and a detection profile in the optimaize `language-detector` library.

Check what PostgreSQL offers out of the box:

```sql
SELECT cfgname FROM pg_ts_config ORDER BY 1;
```

The stock image ships snowball configs for most Western European languages plus `russian`; `ukrainian` and `polish` are ours (hunspell, built in `docker/postgres/Dockerfile` and migrations `001` and `010`).

Two cases follow.

## Case 1: PostgreSQL already has the config (example: Portuguese)

1. **Enum, in three modules.** `SearchLanguage` is copied, like the entities; keep the copies in step:

   | Module | Constant | What it drives |
   |---|---|---|
   | `Client-API/src/main/java/org/shpytchuk/clientapi/language/SearchLanguage.java` | `PORTUGUESE("pt", "portuguese")` | detection on `create`, the search configuration of a `pt` row |
   | `Admin-API/src/main/java/org/shpytchuk/adminapi/language/SearchLanguage.java` | `PORTUGUESE("pt")` | detection on every create and edit in the admin panel |
   | `Worker/src/main/java/org/shpytchuk/worker/language/SearchLanguage.java` | `PORTUGUESE("pt", "portuguese")` | the configuration matching parses a `pt` notice with |

   Keep `SIMPLE` last. A language detected but missing in `Worker` is matched as `simple`; one only `Worker` knows never arrives.

2. **SQL functions.** Nothing to do: `Client-API`'s `FullTextFunctionContributor` builds the `CASE language WHEN 'pt' THEN 'portuguese' ...` of `matches_words` from `SearchLanguage.detectable()`, and `Worker`'s loops over `SearchLanguage.values()` and registers `ts_rank_portuguese`, `match_rank_portuguese` and `shares_words_portuguese`.

3. **Detection.** Nothing to do either: `LanguageResolver` in `Client-API` and `Admin-API` reads a built-in optimaize profile for every `SearchLanguage.detectable()` entry. If the library has no profile for the code, `readBuiltIn` throws at startup; the bundled profiles cover roughly 70 languages, so this is rare. There is no detect-only list: a language the detector knows is one search knows.

4. **Existing notices** keep the language they were stored with. A Portuguese notice written before the change is `null` or a neighbour's code until it is edited in the admin panel; live notices leave for the archive within 30 days, so no backfill is needed.

5. **Verify** with a notice in that language: `lostItem(id) { language }` returns `pt`, a search by another form of a word finds it, and the `match_order` values in `similar_item` show non-zero ranks for titles that share stems.

## Case 2: PostgreSQL has no config (example: Polish)

Polish went this way already, so its files are the reference: the `DICT_PL_COMMIT` block in `docker/postgres/Dockerfile`, `docker/postgres/polish.stop` and `010-polish-fts.postgresql.sql`.

1. **Dictionary.** Extend `docker/postgres/Dockerfile`: download a hunspell dictionary for the language in the `dict` stage, rename to `pl_pl.dict` and `pl_pl.affix` (PostgreSQL insists on those extensions, lower case), and `COPY` them into `tsearch_data` next to the Ukrainian files. PostgreSQL reads them only in UTF-8: check the `SET` line of the `.aff` file, and if it names another charset (`pl_PL` is `ISO8859-2`), convert both files with `iconv` and rewrite that line to `SET UTF-8`. Pin the download (a release version or a commit). Add a stop-word file (`polish.stop`) alongside `ukrainian.stop`.

2. **Migration.** New changeset in `DB-Postgres/changelog/changes/`, modelled on `001-ukrainian-fts`:

   ```sql
   --changeset vasil:010-polish-fts
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

4. **Enum.** Add `POLISH` to `SearchLanguage` in the three modules, as in case 1.

5. **Tests.** The `AbstractPostgresTests` of `Client-API`, `Admin-API` and `Worker` run the real changelog against a stock `postgis/postgis` container, which has no hunspell files. They pre-create `ukrainian` and `polish` as `COPY = simple` in `skipHunspellFullTextSearch()` so the preconditions of `001` and `010` mark those changesets as run. Add the new config to that method in all three, or the migration fails in CI.

## Tests to add or update

- `LanguageResolverTest` in `Client-API` and `Admin-API`: a row with a sentence in the new language.
- `ThingRepositoryTests` (`Client-API`): a row stored with the new code is found by another form of a word.
- `ItemServiceTests` (`Worker`): an event with the new code ranks a candidate with the new configuration, like `ranksAPolishItemWithThePolishConfiguration`.

For case 2 the test containers stub the configuration as a copy of `simple`, so these tests check detection and wiring, not stemming.

## Migration needed?

Case 1: no. Case 2: yes, the text search configuration, and a rebuilt PostgreSQL image.

## Web-Client impact

None. `itemLocale` indexes a notice in its language when that is a site locale and in `uk` otherwise, so a search language that is not a site locale needs nothing on the web side.

## Checklist

- [ ] `pg_ts_config` has the regconfig (or the dictionary, migration and image rebuild are done)
- [ ] `SearchLanguage` constant added in `Client-API`, `Admin-API` and `Worker`
- [ ] `Client-API` and `Admin-API` start (profile found), `Worker` starts (functions registered)
- [ ] Test bases that run the changelog stub the new config if it needs files
- [ ] Detection, search and ranking verified with a notice in the language
