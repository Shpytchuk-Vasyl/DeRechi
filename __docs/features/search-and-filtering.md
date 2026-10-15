# Search and filtering

Two independent implementations exist: the public one in `Client-API` (GraphQL) and the staff one in `Admin-API` (list pages). They share the idea of JPA Specifications over the `Thing` entities and the PostGIS distance predicate, but not the code.

## Public API (`Client-API`)

### Filter

`lostItems` and `foundItems` take an optional `ItemFilterInput`:

| Field | Behaviour |
|---|---|
| `search` | a case-insensitive substring over `title`, `description` and `place.name`, **or** all the words of the query in `title` and `description` after stemming in one of five languages (see below), up to 100 characters |
| `categoryId` | exact match on the category |
| `dateFrom`, `dateTo` | inclusive bounds on the notice date |
| `near` | `NearInput { lat, lon, radiusKm }`; only items whose place is within the radius; `radiusKm` defaults to 50 and is capped at 500 |

`ThingSpecifications.byFilter` composes these through `SpecificationBuilder`, which skips any null or blank field, so an empty filter is a plain `findAll`.

### Full-text search

`search` is two predicates joined with `OR`, so the result is a superset of the old substring search:

- `SpecificationBuilder.containsAny`: `lower(...) LIKE '%query%'` over `title`, `description` and `place.name`. It keeps the cases full-text search misses: part of a word ("навуш"), the place name, `%` and digits.
- `matchesWords` in `ThingSpecifications`: `to_tsvector(<config>, title || ' ' || coalesce(description, '')) @@ plainto_tsquery(<config>, :search)` for each of `ukrainian`, `polish`, `german`, `french` and `english` (`FullTextFunctionContributor.SEARCH_CONFIGS`), joined with `OR`. Every word of the query must be in the notice, in any form and order, by the rules of one of these languages: "гаманця" finds "Гаманець", "кота" finds "кіт", "чорний рюкзак" finds "Рюкзак чорного кольору", "portfela" finds "portfel", "wallets" finds "wallet", "clé" finds "Clés". Each language is an HQL function `matches_words_<config>` registered by `FullTextFunctionContributor` (next to `SpatialFunctionContributor` in `META-INF/services`). The search text goes as a bind parameter (`HibernateCriteriaBuilder.value`), not inlined into the SQL.

**Apostrophes.** Both sides go through `translate(..., '''’', 'ʼʼ')` first: `'` and `’` (U+2019) become `ʼ` (U+02BC). The parser splits a word at `'` and `’` but not at `ʼ`, so without it "м'яка" became `м` and `яка`, both Ukrainian stop words, and "м'яка іграшка" found every "іграшка"; "мʼяч" and "м'яч" never met. Now a word with an apostrophe is one token, matched whole but without stemming (the dictionary does not know the `ʼ` spelling).

**No language detection.** `Worker` detects the language of a notice title with `LanguageResolver`, but a search query is one or two words, and on those the detector mostly returns nothing ("гаманець", "сумка") or takes a Ukrainian word for Russian ("окуляри", "паспорт"). So every query is tried in all five languages. A configuration that does not know a word keeps it as is (`simple` behind the hunspell ones, Cyrillic untouched by the snowball ones), which at worst is an exact whole-word match.

**No index.** `to_tsvector` runs on every row that passes the other filters, twice per page (rows and count). PostgreSQL checks the cheap predicates first: the `LIKE` before the five matches, the category, dates and radius before the whole `OR`. Measured on `derechi/postgis:17-3.5-uk`: about 110 ms per 1000 live notices for `ukrainian`, 30 ms for the three snowball languages together, `polish` about as much as `ukrainian`; with a category filter a few milliseconds. Archived notices are in `*_item_history` and are not searched, so the live tables stay small. If they grow, the way back is a GIN index per language on the same expression plus a subquery `id IN (SELECT ...)`, since an index is not used under an `OR` with `LIKE`.

**Cost per connection.** PostgreSQL loads a hunspell dictionary into each backend on its first use and keeps it there: `ukrainian` about 65 MB and 0.9 s, `polish` about 31 MB and 0.6 s, the snowball ones next to nothing. So every pooled `Client-API` connection that has searched holds about 96 MB, and its first search waits about 1.5 s. Hikari keeps up to 10 connections and replaces each one every 30 minutes; `Worker` already loads the same dictionaries for matching. That is why `POSTGRES_MEMORY` is 2g: ten searching `Client-API` connections alone take about 1 GB. Keep the pools and `POSTGRES_MEMORY` in step.

Results keep the requested `ItemSort`: there is no relevance order.

The radius check is `JTSSpatialPredicates.distanceWithin` from hibernate-spatial on `place.coordinate`. Because the column is `geography(Point,4326)`, the distance is in metres on the sphere, not degrees. `SpatialFunctionContributor` additionally registers a `dwithin` HQL function (`st_dwithin(?1, CAST(?2 AS geography), ?3)`) for places where the predicate helper is not convenient; it is wired through `META-INF/services/org.hibernate.boot.model.FunctionContributor`.

### Sorting and paging

`ItemSort` is an enum: `DATE_DESC` (default), `DATE_ASC`, `TITLE_DESC`, `TITLE_ASC`. `ItemSort.toSort` always appends `id DESC` as a tiebreaker so pages are stable.

Paging is Relay-style (`first`, `after`, `edges`, `pageInfo`) on top of offset pagination: `OffsetPagination` turns the `ScrollSubrange` into a `PageRequest` and back into a `Window`. The connection types are generated by Spring for GraphQL at runtime, which is why the Web-Client keeps a separate `graphql/connections.graphqls` for codegen.

### Places

`places(name, first, after)` returns places sorted by name with a case-insensitive substring match (`ReferenceService.places`, `name` up to 100 characters). The Web-Client uses it for the "near" picker, see [Places](places.md).

## Admin panel (`Admin-API`)

List pages take an `ItemFilter` record bound from the query string: `q`, `categoryId`, `from`, `to`. If `from` is after `to` the record swaps them. `ThingSpecifications.matching` applies the same three-column `lower(...) like` search and the category and date bounds. `ItemFilter.queryString()` re-serialises the filter so htmx links (candidate loading on the Matches page) keep it.

There is no geo filter in the admin lists.

Sorting comes from Spring's `Pageable` resolver (`?sort=date,desc`), but `Pages.safe` reduces it to the whitelist `id`, `title`, `date` before it reaches `order by`. Without that, any entity path would be accepted. Page size is `derechi.admin.page-size` (20). `SortView` tells the templates which column is active so the header links can flip direction.

## Known gaps

- Public full-text search does not rank, and Russian is not among its languages ("кошелька" does not find "кошелек").
- Words with an apostrophe are matched whole, without stemming ("м'яку" does not find "м'яка"), and the `LIKE` half still compares apostrophes as typed.
- Tests stub `ukrainian` and `polish` as copies of `simple` (see [testing](../conventions/testing.md)), so `ThingRepositoryTests` checks word order, apostrophes and English stemming, but not Ukrainian or Polish stemming.
- The place name is matched only as a substring ("Оперного театру" does not find "Оперний театр"): `place` is another table.
- The admin search is still `LIKE` only.
- No search over compensation, contacts or social networks, by design.
- The admin filter has no "near" option.

## Where to look

- `Client-API/src/main/java/org/shpytchuk/clientapi/specification/SpecificationBuilder.java`, `ThingSpecifications.java`
- `Client-API/src/main/java/org/shpytchuk/clientapi/service/OffsetPagination.java`, `dto/ItemSort.java`
- `Client-API/src/main/java/org/shpytchuk/clientapi/config/SpatialFunctionContributor.java`, `FullTextFunctionContributor.java`
- `Admin-API/src/main/java/org/shpytchuk/adminapi/form/ItemFilter.java`, `repository/ThingSpecifications.java`, `service/Pages.java`
- Tests: `ThingRepositoryTests`, `PlaceRepositoryTests`, `ItemSortTest`, `ItemFilterTest`
