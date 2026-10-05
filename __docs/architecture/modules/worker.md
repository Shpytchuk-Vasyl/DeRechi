# Worker

A background worker. Every time a notice is created it looks for notices of the opposite
kind that could be the same thing and records the best candidates in `similar_item`. It
has no HTTP API; the only endpoints are actuator's.

| | |
|---|---|
| Port | 8085 (actuator only) |
| Consumes | queue `worker.items`, bound to `derechi.items` with `item.*.created`; queue `worker.claims`, bound with `item.*.claimed` and `item.*.returned`; queue `worker.archive`, bound with `item.*.archive` |
| Produces | `NotificationRequestedEvent` on `derechi.notifications` (`notification.claim.*`) |
| Needs | PostgreSQL, RabbitMQ |
| Stack | Spring AMQP, Spring Data JPA, Hibernate Spatial + JTS, optimaize `language-detector`, libphonenumber, Lombok |

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

## Claims

The second queue and the scheduled `ClaimFollowUpJob` implement the response flow described in
[../../features/claims.md](../../features/claims.md): notify the author in the language of
their phone number, remind both sides, archive the notice on confirmation or a week after the
last response. `ItemArchiver` is the only code that archives a notice: the admin "Archive"
button arrives here as `ARCHIVE_REQUESTED` on the third queue (`ArchiveListener`), and the
confirmed return and the quiet-notice rule call it directly. Archiving re-points the notice's
claims at the history copy instead of deleting them, which is why this module writes to the
item, history and claim tables. Texts live in this module's own five `messages*.properties`.

## Entities

The `entity` package is a copy of the shared schema, laid out like `DB-Postgres`
(`thing`, `lost`, `found`, `detail`, `matching`), with one addition: `Thing` has a
`@Transient Double orderMatch` that carries the rank from the query to the insert.

Packages by role: `listener` takes messages off the queues and dispatches by routing key,
`handler` holds one handler per routing key (`ItemKind` + verb), `cron` the scheduled
follow-up, `service` the work itself (`ClaimNotifier`, `ItemArchiver`, `ItemService`).

## Config

```yaml
derechi:
  items:
    queue: worker.items
  claims:
    queue: worker.claims
    exchange: derechi.notifications
    site-url: ${DERECHI_SITE_URL:http://localhost:3000}
    check-every: PT10M
    author-reminder-after: P1D
    claimant-reminder-after: P1D
    archive-after: P7D
    retention: P365D
  archive:
    queue: worker.archive
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 3
```

Datasource and RabbitMQ as in every service, from `POSTGRES_*` and `RABBITMQ_*`.
The `RabbitConfig` and `event` package are the same copy-per-module pattern described in
[../messaging.md](../messaging.md).

## Tests

Database-backed, on the Testcontainers harness copied from `Client-API`
(`support/AbstractPostgresTests`, `repository/AbstractRepositoryTests`; places are inserted with
SQL because this module's `Place` maps only the id and the coordinate):

- `service/ItemServiceTests`: the candidate query and `rankAll` (same category, ±3 days, 20 km,
  most relevant first, the rank of each candidate equal to PostgreSQL's own `ts_rank`, five at
  most, a blank title ranks zero, a Ukrainian title goes through `ts_rank_ukrainian`);
- `handler/ItemCreatedHandlerTests`: both handlers end to end into `similar_item`, a redelivered
  event does not duplicate rows;
- `repository/ClaimRepositoryTests`: the derived queries `ClaimFollowUpJob` selects its work with;
- `cron/ClaimFollowUpsTests`: purge and the reminder stamp against the real foreign keys;
- `service/ItemArchiverTests`: history copy, claims re-pointed under the `CHECK`, matches dropped.

Unit tests: `LanguageResolverTest`, `PhoneLocalesTest`, `ClaimNotifierTest` (mocked
`RabbitTemplate`, real bundles), `ItemCreatedListenerTest`, `ClaimListenerTest` and
`ArchiveListenerTest` (dispatch), `ClaimHandlersTest` (what `claimed` and `returned` do, replays),
`ItemArchiverTest`, `ClaimFollowUpJobTest` (publish-then-stamp order, newest-claim rule,
retention), `RabbitConfigTest` (the type ids read from Client-API and Admin-API, the one written
for Notification) and `MessagesTest` (bundle parity). `WorkerApplicationTests` starts the context
on the test container with `ddl-auto=validate`, so the scheduled job never touches the developer
database. `insert into similar_item` by hand (see the root `README.md`) is still how the match
page is exercised locally without going through the queue.

```bash
./mvnw -pl Worker test                     # needs Docker for the container-backed tests
./mvnw -pl Worker spring-boot:run
```
