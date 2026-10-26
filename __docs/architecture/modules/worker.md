# Worker

A background worker. Every time a notice is created it looks for notices of the opposite
kind that could be the same thing and records the best candidates in `similar_item`. It
has no HTTP API; the only endpoints are actuator's.

| | |
|---|---|
| Port | 8085 (serves nothing public); actuator on the management port 9085 |
| Consumes | one queue, `worker.queue`, bound to `notice.status.exchange` with `notice.*.created` and `notice.*.returned`, to `claims.exchange` with `claim.*.claimed` and `claim.*.paid`, and to `notice.action.exchange` with `notice.*.archive` |
| Produces | `NotificationRequestedEvent` on `notification.exchange` (`notification.claim.*`); `ItemArchivedEvent` on `notice.status.exchange` (`notice.<kind>.archived`, after the commit of every archiving) |
| Needs | PostgreSQL, RabbitMQ |
| Stack | Spring AMQP, Spring Data JPA, Hibernate Spatial + JTS, libphonenumber, ShedLock, Lombok |

## Flow

1. `WorkerQueueListener` reads everything from `worker.queue`: `@RabbitListener` on the class
   and a `@RabbitHandler` method per event class, picked by the `__TypeId__` header. Its
   `onItemCreated` receives an `ItemCreatedEvent` (`id`, `date`, `category`, `lat`,
   `lon`, `title`; the fields only `Ingest` needs are ignored) from `Client-API` or from the
   admin panel and hands the message to a `RoutingKeyDispatcher<ItemCreatedHandler>`,
   a map `routing key → handler` built from all handler beans. Every handler method dispatches
   through that class; a key nobody handles throws `AmqpRejectAndDontRequeueException` (straight
   to the DLQ, no retry), and two handlers for one key fail startup. An event class without a
   method lands in the `@RabbitHandler(isDefault = true)` method, which rejects it the same way.
2. `ItemMatchHandler<T>` is one class and two `@Transactional` beans from `ItemSearchConfig`:
   `lostItemCreatedHandler` (`notice.lost.created`) searches **found** items,
   `foundItemCreatedHandler` (`notice.found.created`) searches **lost** items. Each bean holds
   the `KindOps` of the kind that arrived and the `ItemService` of the opposite kind.
3. `ItemService<T>.findMostSuitable(event)` (two beans from `ItemSearchConfig`, one per
   entity type) runs one query that returns the ids and ranks of the candidates
   (`MatchCandidate`), not the entities.
4. The handler calls `KindOps.insertMatches(itemId, candidateIds, matchOrders)`, which puts
   the new notice in its own column of `(found_item_id, lost_item_id, match_order)` through
   `SimilarItemRepository.insertForLost` or `insertForFound`, both wrappers around `insertAll`,
   a single `INSERT ... SELECT FROM unnest(...)`.

## The query

The search text is the new notice's title and description joined by a space (`ItemService.searchText`);
the language is detected on it, and it is what the candidates must share words with and are ranked
against. `ThingSpecifications.byFilter(event, text, language)` combines four predicates; the order
comes from `ThingSpecifications.matchRank`:

| Predicate | Rule | Source |
|---|---|---|
| category | same `category.id` | `event.category` |
| date | within ±3 days of the event's date | `PERIOD = Period.ofDays(3)` |
| distance | `place.coordinate` within 20 000 m of the event's point | `RADIUS_METERS`, `JTSSpatialPredicates.distanceWithin` |
| shared words | `shares_words_<config>(title || ' ' || coalesce(description, ''), :text)`: at least 2 words of the new title and description are in the candidate's title or description, or 1 when the text has a single word | `MIN_SHARED_WORDS` in `FullTextFunctionContributor`; a blank text matches nothing |
| order | `match_rank_<config>(...)` descending, then `id` | see below |

Words are counted after the configuration has stemmed them and dropped stop words (`на`, `біля`,
`десь`, ...), as distinct lexemes: a word repeated in the title counts once, while a word the
hunspell dictionary gives several normal forms for may count as several. A typo is a different word:
"паспотр Аля Марія" shares only "Марія" with "Знайдено паспорт на ім'я Марія" and is not a match.
Lost and found titles often differ in their verb ("Загублено телефон" and "Знайдено телефон
Samsung" share one word of two), so such pairs are not matched either; that is the price of
the two-word rule.

The rank is `ts_rank_cd` (cover density) of the search text's words joined by `|`: about 0.1 for every
occurrence of a shared word, so a candidate with the full name ranks above one with the first name
alone ("Загублено Леокарту Столяр Максим": "…на ім'я Столяр Максим" 0.3, "…на ім'я Максим Петренко"
0.2). Where the words stand does not matter. The `&` query is not used because it gives 0 as soon as
one title word is missing, which "Загублено" always is in a found notice.

At most five candidates are stored per new notice (`MAX_CANDIDATES`). The query is a criteria
`SELECT new MatchCandidate(id, rank) ... ORDER BY rank DESC, id LIMIT 5` built from
`ThingSpecifications.byFilter` and `ThingSpecifications.matchRank`: the rank it sorts by is the
value that becomes `match_order`, so there is no page count, no second ranking query and no
loading of the candidates' place and category. `ItemServiceTests` pins it to one statement.

Candidates are found only for the notice that triggered the event. An older notice that
would match a new one is reached from the new one's side, so the pairing is symmetric in
practice, but nothing re-scans history.

## Language

PostgreSQL full-text search needs a configuration (`english`, `ukrainian`, ...) to stem
words. `Worker` does not detect it: `Client-API` and `Admin-API` store the language of a notice's
text when it is written, and `ItemCreatedEvent`/`ItemUpdatedEvent` carry it as an ISO code
(see [../../features/notices.md](../../features/notices.md#language)). `ItemService` maps it with
`SearchLanguage.fromIsoCode`:

| `SearchLanguage` | regconfig |
|---|---|
| `ENGLISH`, `GERMAN`, `FRENCH`, `SPANISH`, `ITALIAN`, `RUSSIAN` | the PostgreSQL built-ins |
| `UKRAINIAN` | `ukrainian`, created by migration 001 from the hunspell dictionary baked into our PostgreSQL image |
| `POLISH` | `polish`, created by migration 010 the same way, from the `pl_PL` hunspell dictionary (sjp.pl) |
| `SIMPLE` | `simple`, for a notice whose language was not detected, a code with no configuration, or an event from before the field existed |

`ItemMapper.copy` carries `language` into the history row when `ItemArchiver` archives a notice. Adding a language is described in
[../../extending/add-a-search-language.md](../../extending/add-a-search-language.md).

`FullTextFunctionContributor` registers three HQL functions per `SearchLanguage`: `ts_rank_<config>`
(plain `ts_rank` of the `&` query), `match_rank_<config>` (the matching order above) and
`shares_words_<config>` (the shared-words filter); then the generic `ts_rank_cfg(regconfig, doc, query)`
and `match_rank_cfg(regconfig, doc, title)`, and the same `dwithin` that
`Client-API` has. It is wired through
`META-INF/services/org.hibernate.boot.model.FunctionContributor`.

## Claims

The claim events on the same queue and the scheduled `ClaimFollowUpJob` implement the response flow described in
[../../features/claims.md](../../features/claims.md): notify the author in the language of
their phone number, remind both sides, archive the notice on confirmation or a week after the
last response. The same job also archives every notice whose `date` is more than
`derechi.archive.max-age` (30 days) ago, claims or not; Web-Client stops indexing a notice at the
same age (`STALE_AFTER_DAYS` in `src/lib/seo.ts`), so the two change together. `ItemArchiver` is the only code that
archives a notice: the admin "Archive" button arrives here as `ARCHIVE_REQUESTED` with
`notice.<kind>.archive` (`WorkerQueueListener.onArchiveRequested`), and the confirmed return, the quiet-notice and the stale-notice rules
call it directly. Archiving re-points the notice's
claims at the history copy instead of deleting them, which is why this module writes to the
item, history and claim tables. Every archiving ends with `notice.<kind>.archived` (`ItemArchivedEvent`,
`ITEM_ARCHIVED`, with the live notice's id) on `notice.status.exchange` (`derechi.archive.exchange`), sent by
`AfterCommitPublisher` after the commit; `Ingest` deletes the notice's channel post on it. A
broker failure at that moment is a `WARN`, and the event is lost. Texts live in this module's own five `messages*.properties`.

What tells a lost notice from a found one lives in `service/KindOps<T, H, C>`: the item,
history and claim repositories of the kind, how to build its stamped history copy, and which
column of `similar_item` is its own (`insertMatches`, `deleteMatches`). `ItemKinds` is the
`@Component` that holds the two instances (`of(kind)`, `claims(kind)`), so `ItemArchiver`, the
claim handlers and the job take an `ItemKind` and never switch on it.

The claim handlers (`ClaimedHandler`, `ReturnedHandler`, `PaidHandler`, one instance per kind,
each answering its own `routingKey()`: `claim.<kind>.claimed`, `notice.<kind>.returned`,
`claim.<kind>.paid`, built by `ItemKind.claimKey` / `noticeKey`) are registered by
`ClaimsConfig.ClaimHandlers`, a Spring `BeanRegistrar` that loops over `ItemKind.values()`. They
are registered one by one on purpose: a list returned from a single `@Bean` would neither get
the `@Transactional` proxy (`PaidHandler.handle`) nor be collected into the `List<ClaimHandler>`
of `WorkerQueueListener`.
`ClaimsConfigTests` pins both.

`ClaimNotifier` has one render-and-publish path; the four messages it sends are the enum
`ClaimMessage` (routing key, bundle key, recipient, dedup key). The job runs its five steps
from a list of `Step(name, summary, find, act)` in order, each finder running after the
previous step finished, and the summary line is built from the steps.

`ClaimFollowUpJob.run` is `@Scheduled` and also `@SchedulerLock(name = "claim-follow-up")`
(ShedLock): two instances running it at once would send every reminder twice.
`SchedulerLockConfig` enables the lock with `lockAtMostFor` PT30M (frees the lock of an
instance that died mid-run) and `lockAtLeastFor` PT1M, and provides a
`JdbcTemplateLockProvider` with `usingDbTime()`, so the database clock decides and the
instances' clocks may drift. The lock row lives in the `shedlock` table (migration 009), which
is not an entity. As far as the scheduled job is concerned, several Worker instances may run
side by side.

## Entities

The `entity` package is a copy of the shared schema, laid out like `DB-Postgres`
(`thing`, `lost`, `found`, `detail`, `matching`).

Packages by role: `listener` takes messages off `worker.queue` and dispatches by routing key
(`RoutingKeyDispatcher`), `handler` holds one handler bean per routing key (`ItemKind` + verb;
the classes are parameterized by kind), `cron` the scheduled follow-up, `service` the work
itself (`ClaimNotifier`, `ItemArchiver`, `ItemService`) and the per-kind `KindOps` behind
`ItemKinds`.

## Config

```yaml
derechi:
  worker:
    queue: worker.queue
  claims:
    exchange: notification.exchange
    site-url: ${DERECHI_SITE_URL:http://localhost:3000}
    check-every: PT10M
    author-reminder-after: P1D
    claimant-reminder-after: P1D
    archive-after: P7D
    retention: P365D
  archive:
    max-age: P30D
    exchange: notice.status.exchange
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false
        retry:
          enabled: true
          max-retries: 2
```

Datasource and RabbitMQ as in every service, from `POSTGRES_*` and `RABBITMQ_*`.
The `RabbitConfig` and `event` package are the same copy-per-module pattern described in
[../messaging.md](../messaging.md).

## Tests

Database-backed, on the Testcontainers harness copied from `Client-API`
(`support/AbstractPostgresTests`, `repository/AbstractRepositoryTests`; places are inserted with
SQL because this module's `Place` maps only the id and the coordinate):

- `service/ItemServiceTests`: the candidate query in one statement (same category, ±3 days, 20 km,
  at least two shared words or one for a one-word title, a typo or a repeated word not counted
  twice, most relevant first, the full name above the first name alone, the rank of each candidate
  equal to PostgreSQL's own `ts_rank_cd` of the `|` query, five at most, the description's words found and ranked with the title's, blank title and description match nothing, a Ukrainian title goes through
  `ts_rank_ukrainian`);
- `handler/ItemMatchHandlerTests`: both handler beans end to end into `similar_item`, a
  redelivered event does not duplicate rows;
- `repository/ClaimRepositoryTests`: the derived queries `ClaimFollowUpJob` selects its work with;
- `cron/ClaimFollowUpsTests`: purge and the reminder stamp against the real foreign keys;
- `service/ItemArchiverTests`: history copy, claims re-pointed under the `CHECK`, matches dropped.

Unit tests: `PhoneLocalesTest`, `ClaimNotifierTest` (mocked
`RabbitTemplate`, real bundles), `ClaimMessageTest` (routing, bundle and dedup keys, recipients),
`RoutingKeyDispatcherTest` and `WorkerQueueListenerTest` (dispatch), `ItemMatchHandlerTest` (which column each kind takes, through
the repository's real default methods), `KindOpsTest` and `ItemKindsTest`, `ClaimHandlersTest`
(what `claimed` and `returned` do, replays), `PaidHandlerTest`, `ItemArchiverTest`,
`ClaimFollowUpJobTest` (publish-then-stamp order, newest-claim rule, retention, step order),
`RabbitConfigTest` (the type ids read from Client-API and Admin-API, the one written for
Notification) and `MessagesTest` (bundle parity). `ClaimsConfigTests` starts a small context with
`ApplicationContextRunner`: six handler beans, all of them reachable through `WorkerQueueListener`, the
`@Transactional` proxy on `PaidHandler`. `WorkerApplicationTests` starts the context
on the test container with `ddl-auto=validate`, so the scheduled job never touches the developer
database. `insert into similar_item` by hand (see the root `README.md`) is still how the match
page is exercised locally without going through the queue.

```bash
./mvnw -pl Worker test                     # needs Docker for the container-backed tests
./mvnw -pl Worker spring-boot:run
```
