# Automatic matching

Every new notice is compared in the background against notices of the opposite kind. The result is a list of candidate pairs with a relevance score, which the admin panel shows on the Matches page (see [Match notifications](match-notifications.md)). The work is done by the `Worker` module, a RabbitMQ consumer with no HTTP API beyond the actuator.

## Flow

1. `Client-API` publishes `ItemCreatedEvent` (`id`, `date`, `category`, `lat`, `lon`, `title`, `description`, `language`, plus the fields `Ingest` needs for a channel post, which `Worker` ignores) to the `notice.status.exchange` exchange with key `notice.lost.created` or `notice.found.created`. `Admin-API` publishes the same event after the commit when a notice is created in the admin panel, so those notices are matched too.
2. Worker's one queue, `worker.queue`, is bound with `notice.*.created` among others. `WorkerQueueListener.onItemCreated` dispatches by the routing key of the message (`RoutingKeyDispatcher`, used by every handler method of the listener) to the `ItemCreatedHandler` registered for it. An unknown key is rejected without requeue.
3. `ItemMatchHandler<T>` is one class with two beans wired in `ItemSearchConfig`: `lostItemCreatedHandler` searches `found_item`, `foundItemCreatedHandler` searches `lost_item`. Each delegates to the `ItemService<T>` bean of the opposite kind and holds the `KindOps` of the kind that arrived.
4. `ItemService.findMostSuitable` runs one query that returns the ids and ranks of the five best candidates (`MatchCandidate`).
5. The handler writes the pairs with `KindOps.insertMatches`, which calls `SimilarItemRepository.insertForLost` or `insertForFound` so the new notice lands in its own column; both wrap `insertAll` (one statement with arrays). The pair `(found_item_id, lost_item_id)` is the primary key.
6. An edit in the admin panel publishes `ItemUpdatedEvent` (`notice.<kind>.updated`, the same shape) after the commit. `WorkerQueueListener.onItemUpdated` hands it to the same `ItemMatchHandler` bean (`ItemUpdatedHandler`), which first deletes the notice's candidates nobody was notified about (`similar_item.notified_at IS NULL`, `KindOps.deleteUnnotifiedMatches`) and then searches again; a notified pair stays and only gets its rank refreshed.

## Candidate rules

All of them live in `ThingSpecifications.byFilter`:

| Rule | Value | Where |
|---|---|---|
| Same category | `category.id = event.category` | `categoryIs` |
| Date window | `event.date ± 3 days` | `PERIOD = Period.ofDays(3)` |
| Distance | within 20 000 m of the event's coordinate | `RADIUS_METERS`, `JTSSpatialPredicates.distanceWithin` |
| Shared words | at least 2 words of the event's title and description in the candidate's title or description, 1 for a one-word text | `relevantTo` |
| Ordering | `ts_rank_cd` of `title || ' ' || coalesce(description, '')` against the event's title and description, descending, then `id` | `matchRank` |
| Candidates | first 5 | `ItemService.MAX_CANDIDATES` |

The rank that goes into `similar_item.match_order` is the one the query sorts by (`ThingSpecifications.matchRank`), read in the same statement. The search text is the event's title and description together; when both are blank nothing matches. A description makes more pairs reachable (a found "Purse" with "black leather" in its description meets a lost "Black leather wallet"), and the date, distance and category rules keep the extra words from pulling in unrelated notices.

## Language

PostgreSQL full-text search needs a configuration (`regconfig`) to stem words. `Worker` does not detect a language any more: the event carries the `language` the notice was stored with (detected from its text when it was written, see [Notices](notices.md#language)), and `SearchLanguage.fromIsoCode` turns it into a configuration:

| `SearchLanguage` | ISO | regconfig |
|---|---|---|
| `ENGLISH` | en | `english` |
| `GERMAN` | de | `german` |
| `FRENCH` | fr | `french` |
| `SPANISH` | es | `spanish` |
| `ITALIAN` | it | `italian` |
| `RUSSIAN` | ru | `russian` |
| `UKRAINIAN` | uk | `ukrainian` |
| `POLISH` | pl | `polish` |
| `SIMPLE` | `null` or anything else | `simple` |

Both the new notice's text and the candidates' are parsed in the new notice's language, so their words can be compared. `SIMPLE` (no stemming, exact tokens) covers a notice whose language was not detected and an event published before the field existed.

`FullTextFunctionContributor` registers an HQL function `ts_rank_<regconfig>` for every enum value plus a generic `ts_rank_cfg` and the `dwithin` predicate. That is why adding a language is an enum change rather than SQL (see [Add a search language](../extending/add-a-search-language.md)).

The `ukrainian` and `polish` configurations are not built into PostgreSQL. `docker/postgres/Dockerfile` downloads the `dict_uk` and `pl_PL` hunspell dictionaries into `tsearch_data`, and migrations `001-extentions-and-configuration.sql` and `010-polish-fts.postgresql.sql` create the dictionaries and configurations. Tests on a stock PostGIS image stub both with `COPY = simple` (`AbstractPostgresTests.skipHunspellFullTextSearch`).

## Failure handling

`spring.rabbitmq.listener.simple.retry` retries a failing message 3 times in-process; `default-requeue-rejected: false` then dead-letters it through `dead-letter.exchange` to `worker.dlq`. Nothing reads the DLQ today; it is there for inspection in the RabbitMQ UI. See [Messaging](../architecture/messaging.md).

## Known gaps and ideas

- The radius, the date window and the page size are constants, not configuration.
- A candidate is parsed in the new notice's language, not its own: a Ukrainian lost notice and a Russian found one share only the words spelled the same in both, and stemming helps neither.
- A notice created while `Worker` is down is matched when the service comes back (the queue is durable), but a notice whose publish failed in `Client-API` is never matched.
- Matching runs at creation and after an edit in the admin panel. A lost notice posted before the matching found notice is still found, because the found notice triggers its own search in the other direction. An edit redoes only the edited notice's own candidate list: pairs where it is someone else's candidate are not recalculated.
- The project README floats an idea of score thresholds (around 0.8 to notify an admin, above 0.9 to confirm with an external model before notifying the owner). Nothing of that is implemented.

## Where to look

- `Worker/src/main/java/org/shpytchuk/worker/listener/WorkerQueueListener.java`, `listener/RoutingKeyDispatcher.java`
- `Worker/src/main/java/org/shpytchuk/worker/service/ItemService.java`, `handler/ItemMatchHandler.java`, `config/ItemSearchConfig.java`, `service/KindOps.java`, `repository/SimilarItemRepository.java`
- `Worker/src/main/java/org/shpytchuk/worker/specification/ThingSpecifications.java`
- `Worker/src/main/java/org/shpytchuk/worker/language/`
- `Worker/src/main/java/org/shpytchuk/worker/config/FullTextFunctionContributor.java`
- `DB-Postgres/changelog/changes/001-extentions-and-configuration.sql`, `004-similar_item.postgresql.sql`
- Tests: `ItemServiceTests` and `ItemMatchHandlerTests` (Worker, on the Testcontainers database), `ItemMatchHandlerTest`, `KindOpsTest`, `WorkerQueueListenerTest`, `RoutingKeyDispatcherTest`, `RabbitConfigTest`; `MatchServiceTests` in Admin-API for the page that shows the result
