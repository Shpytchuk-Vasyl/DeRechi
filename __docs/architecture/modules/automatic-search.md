# Automatic-Search

A background worker. Every time a notice is created it looks for notices of the opposite
kind that could be the same thing and records the best candidates in `similar_item`. It
has no HTTP API; the only endpoints are actuator's.

| | |
|---|---|
| Port | 8085 (actuator only) |
| Consumes | queue `automatic-search.items`, bound to `derechi.items` with `item.*.created` |
| Needs | PostgreSQL, RabbitMQ, Discovery |
| Stack | Spring AMQP, Spring Data JPA, Hibernate Spatial + JTS, optimaize `language-detector`, Lombok |

## Flow

1. `ItemCreatedListener` receives an `ItemCreatedEvent` (`id`, `date`, `category`, `lat`,
   `lon`, `title`) and reads the routing key from the message. It keeps a map
   `routing key → ItemCreatedHandler`, built from all handler beans, and throws
   `AmqpRejectAndDontRequeueException` for a key nobody handles (straight to the DLQ, no
   retry).
2. `LostItemCreatedHandler` (`item.lost.created`) searches **found** items;
   `FoundItemCreatedHandler` (`item.found.created`) searches **lost** items. Both are
   `@Transactional` and otherwise symmetric.
3. `ItemService<T>.findAllMostSuitable(event)` (two beans from `ItemSearchConfig`, one per
   entity type) runs the query and ranks the result.
4. The handler inserts `(found_item_id, lost_item_id, match_order)` rows with
   `SimilarItemRepository.insertAll`, a single `INSERT ... SELECT FROM unnest(...)`.

## The query

`ThingSpecifications.byFilter(event, language)` combines four predicates:

| Predicate | Rule | Source |
|---|---|---|
| category | same `category.id` | `event.category` |
| date | within ±3 days of the event's date | `PERIOD = Period.ofDays(3)` |
| distance | `place.coordinate` within 20 000 m of the event's point | `RADIUS_METERS`, `JTSSpatialPredicates.distanceWithin` |
| order | `ts_rank_<config>(title || ' ' || coalesce(description, ''), :title)` descending, then `id` | only when the title is not blank |

The page size is 5 (`PAGE_SIZE`), so at most five candidates are stored per new notice.
After the page is loaded, `ThingRepository.rankAll` recomputes the rank for exactly those
ids with the same configuration and the value is written into the transient `orderMatch`
field, which becomes `match_order`. A blank title gives every candidate a rank of 0.

Candidates are found only for the notice that triggered the event. An older notice that
would match a new one is reached from the new one's side, so the pairing is symmetric in
practice, but nothing re-scans history.

## Language detection

PostgreSQL full-text search needs a configuration (`english`, `ukrainian`, ...) to stem
words, and the title can be in any of the languages the site supports.
`LanguageResolver` runs the optimaize detector over the title and maps the ISO code to a
`SearchLanguage`:

| `SearchLanguage` | regconfig |
|---|---|
| `ENGLISH`, `GERMAN`, `FRENCH`, `SPANISH`, `ITALIAN`, `RUSSIAN` | the PostgreSQL built-ins |
| `UKRAINIAN` | `ukrainian`, created by migration 001 from the hunspell dictionary baked into our PostgreSQL image |
| `SIMPLE` | `simple`, the fallback for a blank title, an undetected language, or a detected language with no configuration |

The detector is loaded with profiles for every `SearchLanguage` plus `pl`
(`DETECT_ONLY_CODES`). Polish is in the detector so that Polish titles are not mistaken
for Ukrainian or Russian, but there is no `polish` configuration in PostgreSQL, so it
falls through to `simple`. Adding a language is described in
[../../extending/add-a-search-language.md](../../extending/add-a-search-language.md).

`FullTextFunctionContributor` registers one HQL function per `SearchLanguage`
(`ts_rank_english`, `ts_rank_ukrainian`, ...), a generic `ts_rank_cfg(regconfig, doc,
query)` used by `rankAll`, and the same `dwithin` that `Client-API` has. It is wired through
`META-INF/services/org.hibernate.boot.model.FunctionContributor`.

## Entities

The `entity` package is a copy of the shared schema with one addition: `Thing` has a
`@Transient Double orderMatch` that carries the rank from the query to the insert. Nothing
here writes to the item tables; `similar_item` is the only table this service modifies.

## Config

```yaml
derechi:
  items:
    queue: automatic-search.items
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3
```

Datasource, RabbitMQ and Eureka as in every service, overridden by environment in Compose.
The `RabbitConfig` and `event` package are the same copy-per-module pattern described in
[../messaging.md](../messaging.md).

## Tests

Only `AutomaticSearchApplicationTests` (context load). The ranking query and the language
resolver have no automated tests yet; `insert into similar_item` by hand (see the root
`README.md`) is how the match page is exercised locally without going through the queue.

```bash
./mvnw -pl Automatic-Search spring-boot:run
```
