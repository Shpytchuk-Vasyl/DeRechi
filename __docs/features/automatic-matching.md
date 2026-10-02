# Automatic matching

Every new notice is compared in the background against notices of the opposite kind. The result is a list of candidate pairs with a relevance score, which the admin panel shows on the Matches page (see [Match notifications](match-notifications.md)). The work is done by the `Worker` module, a RabbitMQ consumer with no HTTP API beyond the actuator.

## Flow

1. `Client-API` publishes `ItemCreatedEvent` (`id`, `date`, `category`, `lat`, `lon`, `title`) to the `derechi.items` exchange with key `item.lost.created` or `item.found.created`.
2. The queue `worker.items` is bound with `item.*.created`. `ItemCreatedListener` reads the routing key from the message and dispatches to the `ItemCreatedHandler` registered for it. An unknown key is rejected without requeue.
3. `LostItemCreatedHandler` searches `found_item`; `FoundItemCreatedHandler` searches `lost_item`. Both delegate to an `ItemService<T>` bean (two instances, wired in `ItemSearchConfig`).
4. `ItemService.findAllMostSuitable` builds the candidate query, takes the first page and asks the repository to rank the candidates.
5. The handler inserts the pairs into `similar_item` with `SimilarItemRepository.insertAll` (one statement with arrays). The pair `(found_item_id, lost_item_id)` is the primary key.

## Candidate rules

All of them live in `ThingSpecifications.byFilter`:

| Rule | Value | Where |
|---|---|---|
| Same category | `category.id = event.category` | `categoryIs` |
| Date window | `event.date ± 3 days` | `PERIOD = Period.ofDays(3)` |
| Distance | within 20 000 m of the event's coordinate | `RADIUS_METERS`, `JTSSpatialPredicates.distanceWithin` |
| Ordering | `ts_rank` of `title || ' ' || coalesce(description, '')` against the event title, descending, then `id` | `orderByRelevance` |
| Page | first 5 | `ItemService.PAGE_SIZE` |

The rank that goes into `similar_item.match_order` is recomputed for the chosen ids with `ThingRepository.rankAll` using the same text search configuration. An empty or blank title gives everybody a score of `0.0`.

## Language detection

PostgreSQL full-text search needs a configuration (`regconfig`) to stem words. `LanguageResolver` detects the language of the event title with the optimaize `language-detector` library and maps it to a `SearchLanguage`:

| `SearchLanguage` | ISO | regconfig |
|---|---|---|
| `ENGLISH` | en | `english` |
| `GERMAN` | de | `german` |
| `FRENCH` | fr | `french` |
| `SPANISH` | es | `spanish` |
| `ITALIAN` | it | `italian` |
| `UKRAINIAN` | uk | `ukrainian` |
| `SIMPLE` | none | `simple` |

Polish is in `DETECT_ONLY_CODES`: the detector knows it so that Polish text is not misread as Ukrainian, but there is no Polish regconfig in PostgreSQL, so it resolves to `SIMPLE` (no stemming, exact tokens).

`FullTextFunctionContributor` registers an HQL function `ts_rank_<regconfig>` for every enum value plus a generic `ts_rank_cfg` and the `dwithin` predicate. That is why adding a language is an enum change rather than SQL (see [Add a search language](../extending/add-a-search-language.md)).

The `ukrainian` configuration is not built into PostgreSQL. `docker/postgres/Dockerfile` downloads the `dict_uk` hunspell dictionary into `tsearch_data`, and migration `001-extentions-and-configuration.sql` creates the dictionary and configuration. Tests on a stock PostGIS image stub it with `COPY = simple` (`AbstractPostgresTests.skipUkrainianFullTextSearch`).

## Failure handling

`spring.rabbitmq.listener.simple.retry` retries a failing message 3 times in-process; `default-requeue-rejected: false` then dead-letters it to `derechi.items.dlx`, bound to `worker.items.dlq`. Nothing reads the DLQ today; it is there for inspection in the RabbitMQ UI. See [Messaging](../architecture/messaging.md).

## Known gaps and ideas

- The radius, the date window and the page size are constants, not configuration.
- Only the title is used for detection and ranking input; the candidate's description is part of the document but the event's description is not sent.
- A notice created while `Worker` is down is matched when the service comes back (the queue is durable), but a notice whose publish failed in `Client-API` is never matched.
- Matching is one-shot at creation time. A lost notice posted before the matching found notice is still found, because the found notice triggers its own search in the other direction, but edits in the admin panel do not re-run matching.
- The project README floats an idea of score thresholds (around 0.8 to notify an admin, above 0.9 to confirm with an external model before notifying the owner). Nothing of that is implemented.

## Where to look

- `Worker/src/main/java/org/shpytchuk/worker/listener/ItemCreatedListener.java`
- `Worker/src/main/java/org/shpytchuk/worker/service/ItemService.java`, `handler/LostItemCreatedHandler.java`, `handler/FoundItemCreatedHandler.java`
- `Worker/src/main/java/org/shpytchuk/worker/specification/ThingSpecifications.java`
- `Worker/src/main/java/org/shpytchuk/worker/language/`
- `Worker/src/main/java/org/shpytchuk/worker/config/FullTextFunctionContributor.java`
- `DB-Postgres/changelog/changes/001-extentions-and-configuration.sql`, `004-similar_item.postgresql.sql`
- Tests: `ItemServiceTests` and `ItemCreatedHandlerTests` (Worker, on the Testcontainers database), `LanguageResolverTest`, `ItemCreatedListenerTest`, `RabbitConfigTest`; `MatchServiceTests` in Admin-API for the page that shows the result
