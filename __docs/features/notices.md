# Notices

A notice is a public post about a thing somebody lost or found. There are two kinds, `LostItem` and `FoundItem`, which share almost everything through the `Thing` mapped superclass. Notices are created by the public through `Client-API` (GraphQL) and managed by staff through `Admin-API` (see [Admin panel](admin-panel.md)).

## What a user can do

| Action | Where | Notes |
|---|---|---|
| Post a lost notice | `createLostItem(input: ItemInput!)` | photo optional |
| Post a found notice | `createFoundItem(input: ItemInput!)` | photo required |
| Read one notice | `lostItem(id)`, `foundItem(id)` | `null` when missing |
| Browse notices | `lostItems(filter, sort, first, after)`, `foundItems(...)` | Relay-style connection, see [Search and filtering](search-and-filtering.md) |
| Edit or delete | not available | the mutations exist in the schema file but are commented out |
| See how busy the site is | `stats { returnedThisWeek foundToday }` | two figures on the home page, see below |

The Web-Client consumes exactly this API; it has no private endpoints of its own.

## The input

`ItemInput` is validated with Bean Validation before the service sees it (`Client-API/src/main/java/org/shpytchuk/clientapi/input/`):

| Field | Rules |
|---|---|
| `title` | required, up to 100 characters |
| `description` | optional, up to 250 characters |
| `date` | required, not in the future, within the last 30 days (`@WithinDays(30)`, custom constraint in `anotation/WithinDays.java`) |
| `compensation` | optional `MoneyInput { amount, currency }`, amount is a whole number `>= 0` (`0` is a reward by agreement, without a sum; no `compensation` at all is no reward), currency is optional and defaults to the country's currency, see [Countries and currencies](countries-and-currencies.md) |
| `image` | storage key, up to 200 characters, required for found items (checked in `ItemService.apply`) |
| `categoryId` | required, must exist (`NOT_FOUND` otherwise) |
| `place` | `PlaceInput`: Google place id, name, lat/lon, `countryCode` (two letters, must be supported) |
| `contact` | `ItemContactInfoInput`: phone in E.164 (`^\+[1-9]\d{7,14}$`), optional email up to 50 characters (blank is stored as `null` by `ContactInfoMapper`, which builds the `contact_info` row for notices and claims alike), optional list of `SocialMedia`. An author without an email gets responses and match notifications by SMS only. The phone may be left out only with a `nickName` (up to 50) and exactly one `socialMedias` entry (`@PhoneOrNickName`), plus an optional `profileUrl` (`https://`, up to 255): the site form always sends a phone, only `Ingest` uses this for an author it knows by a Telegram `@username` or a Facebook profile name, see [Claims](claims.md#an-author-with-only-a-nickname) |

What the client gets back is `ItemDto` built by `ItemMapper`. Contact details are masked by `ContactMasker` so that scraping the API does not yield usable contacts:

```
+380671234567     -> +38067*****67
finder@example.com -> f*****r@example.com
```

A notice without a phone returns `phone: null`; the nickname is not part of `ContactInfo`. Full contacts are only visible in the admin panel and in the notifications sent to the author.
A viewer who recognises the item does not get them either: they send their own contacts through
`claimLostItem` / `claimFoundItem`, see [Claims](claims.md).

## Errors

`GraphQlExceptionResolver` maps exceptions to GraphQL error types:

| Exception | GraphQL `errorType` |
|---|---|
| `NotFoundException` | `NOT_FOUND` |
| `ConstraintViolationException` | `BAD_REQUEST`, message lists `field: reason` pairs |
| `IllegalArgumentException` (unsupported country or currency, missing image) | `BAD_REQUEST` |

Anything else falls through to the default `INTERNAL_ERROR`.

## Language

Every notice remembers the language its text is written in: `language VARCHAR(2)`, an ISO 639-1 code, on `lost_item`, `found_item` and both `*_history` tables (migration 016). It is detected from the text when the notice is written, never taken from the author's UI language: the site's default locale is `en`, a Ukrainian in Poland browses `/pl/`, the admin's UI is not the author's, and `Ingest` has no UI at all.

- `Client-API`: `ItemService.create` runs `LanguageResolver` (the optimaize `language-detector`) over the title and the description together. `Ingest` posts through `createLostItem`/`createFoundItem`, so its notices get it the same way.
- `Admin-API`: `ItemFormApplier` does the same on every create and every edit, of live and archived notices alike, so an edited text gets its language again.
- The detector knows only the `SearchLanguage` languages: `en`, `de`, `fr`, `es`, `it`, `ru`, `uk`, `pl`. Three of them (`es`, `it`, `ru`) are not site locales and are stored as they are. Its confidence threshold is high, so a short title without a description ("Гаманець") often stays `null`.
- Rows written before migration 016 got `uk`, what the site assumed for every notice until then; they leave for the archive within 30 days of their `date`.

What reads it:

| Where | What for |
|---|---|
| `Client-API` search | the notice's words and the query are stemmed in this language, `simple` when `null`, see [Search and filtering](search-and-filtering.md#full-text-search) |
| `Worker` matching | `ItemCreatedEvent`/`ItemUpdatedEvent` carry it, and candidates are compared in the new notice's language, see [Automatic matching](automatic-matching.md#language) |
| Web-Client SEO | the notice page is indexed in this language only (`uk` when it is `null` or not a site locale), and the sitemap lists it there, see [web-client.md](../architecture/modules/web-client.md) |

GraphQL returns it as `language` on `LostItem` and `FoundItem`.

## What happens after a notice is created

`ItemEventAspect` wraps every `ItemService.create(..)` with `@AfterReturning` and publishes an `ItemCreatedEvent` (`id`, `date`, `category`, `lat`, `lon`, `title`, `language`, and for the Telegram channel `categoryKey`, `placeName`, `description`, `compensation`, `currency`, `image`) to the `notice.status.exchange` exchange. The routing key is `notice.lost.created` or `notice.found.created`: the aspect binds the service that ran (`target(service)`) and asks its `kind()`, an `ItemKind`, for `noticeKey("created")`. `Worker` consumes it, see [Automatic matching](automatic-matching.md), and so does `Ingest`, which posts the notice to our Telegram channel, see [Telegram import](telegram-ingest.md#notices-from-the-site).

The aspect has `@Order(0)`, so it sits outside the transaction interceptor and fires only after the insert has committed; a failing `create` publishes nothing (`ItemEventAspectTest.doesNotPublishWhenCreateThrows`). If RabbitMQ is unreachable at that moment, `EventPublisher` puts the event into its in-memory buffer and the mutation still succeeds; the event goes out once the connection is back. A restart of the instance loses the buffer, and then such a notice is never matched: there is no outbox (see [Messaging](../architecture/messaging.md)).

A notice lives 30 days from its `date`, the day the thing was lost or found, not the day it was posted. After that `Worker` archives it whether it has claims or not (`ClaimFollowUpJob`, step "archive of stale item", `derechi.archive.max-age: P30D`, days in UTC, `date` strictly before today minus 30 days): a notice dated 1 September is still listed on 1 October and goes on the first run of 2 October. Since `Client-API` accepts a `date` up to 30 days back, a notice posted with an old date is listed for only the days it has left. Claims archive it earlier, see [Claims](claims.md).

## Home page figures

`stats` is answered by `FoundItemStatisticsController` → `FoundItemStatisticsService` with two counts over found notices only:

| Field | What is counted | Window |
|---|---|---|
| `returnedThisWeek` | rows in `found_item_history` by `archived_at` | the last 7 days, today included, from midnight UTC; a rolling window, so the figure does not drop to zero every Monday |
| `foundToday` | rows in `found_item` by `date` | today in UTC |

The figures are approximate. History also receives notices archived automatically without a confirmed return (a week after a claim, 30 days after `date`), and `date` is the day the item was found, not the time it was posted (there is no `created_at`).

The Web-Client caches the answer for an hour under the `stats` tag and drops the tag when a found notice is posted; it hides a figure that is zero.

## Storage

One row in `lost_item` or `found_item`, one `contact_info` row per notice, one `place` row keyed by the Google place id. The schema is owned by `DB-Postgres`, see [Database](../architecture/database.md). Entities are copied into each service; keep the copies identical when you touch them (see [Add a GraphQL field](../extending/add-a-graphql-field.md)).

## Known gaps

- No update or delete for the public. The code is there in `ItemService` and the schema, commented out, waiting for an authentication story on `Client-API`.
- No ownership: a notice is not tied to a Keycloak user, so "my notices" cannot be built yet.
- Phone masking assumes a five-digit visible prefix, which fits Ukrainian numbers best.
- The home page figures are approximate. "Returned" counts every archived found notice, including those archived a week after the last claim without a confirmation, archived by staff or archived 30 days after their `date`; counting claims with `confirmed_at` would be exact. "Found today" reads the date the finder entered, not when the notice was posted: there is no `created_at` column.
- Days in `stats` start at midnight UTC, which is 02:00 or 03:00 in Kyiv.

## Where to look

- `Client-API/src/main/resources/graphql/schema.graphqls`
- `Client-API/src/main/java/org/shpytchuk/clientapi/service/ItemService.java`, `lost/LostItemService.java`, `found/FoundItemService.java`, `entity/ItemKind.java`, `mapper/ContactInfoMapper.java`
- `Client-API/src/main/java/org/shpytchuk/clientapi/controller/LostItemController.java`, `FoundItemController.java`
- `Client-API/src/main/java/org/shpytchuk/clientapi/util/ContactMasker.java`
- `Client-API/src/main/java/org/shpytchuk/clientapi/language/`, `Admin-API/src/main/java/org/shpytchuk/adminapi/language/`, `Admin-API/src/main/java/org/shpytchuk/adminapi/mapper/ItemFormApplier.java`
- `DB-Postgres/changelog/changes/016-thing-language.postgresql.sql`
- `Client-API/src/main/java/org/shpytchuk/clientapi/controller/FoundItemStatisticsController.java`, `service/found/FoundItemStatisticsService.java`
- Tests: `LostItemControllerTests`, `FoundItemControllerTests`, `ItemServiceTests`, `LanguageResolverTest` (in `Client-API` and `Admin-API`), `AdminItemServiceTests`, `ItemEventAspectTest`, `ItemKindTest`, `ContactInfoMapperTest`, `ContactMaskerTest`, `WithinDaysLocalDateValidatorTest`, `FoundItemStatisticsControllerTests`, `FoundItemStatisticsServiceTest`

Extending: [Add a GraphQL field](../extending/add-a-graphql-field.md), [Add a category](../extending/add-a-category.md), [Add a social network](../extending/add-a-social-network.md).
