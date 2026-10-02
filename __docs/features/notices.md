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

The Web-Client consumes exactly this API; it has no private endpoints of its own.

## The input

`ItemInput` is validated with Bean Validation before the service sees it (`Client-API/src/main/java/org/shpytchuk/clientapi/input/`):

| Field | Rules |
|---|---|
| `title` | required, up to 100 characters |
| `description` | optional, up to 250 characters |
| `date` | required, not in the future, within the last 30 days (`@WithinDays(30)`, custom constraint in `anotation/WithinDays.java`) |
| `compensation` | optional `MoneyInput { amount, currency }`, amount is a whole number `>= 0`, currency is optional and defaults to the country's currency, see [Countries and currencies](countries-and-currencies.md) |
| `image` | storage key, up to 200 characters, required for found items (checked in `ItemService.apply`) |
| `categoryId` | required, must exist (`NOT_FOUND` otherwise) |
| `place` | `PlaceInput`: Google place id, name, lat/lon, `countryCode` (two letters, must be supported) |
| `contact` | `ContactInfoInput`: phone in E.164 (`^\+[1-9]\d{7,14}$`), email up to 50 characters, optional list of `SocialMedia` |

What the client gets back is `ItemDto` built by `ItemMapper`. Contact details are masked by `ContactMasker` so that scraping the API does not yield usable contacts:

```
+380671234567     -> +38067*****67
finder@example.com -> f*****r@example.com
```

Full contacts are only visible in the admin panel and in the notifications sent to the author.
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

## What happens after a notice is created

`ItemEventAspect` wraps every `ItemService.create(..)` with `@AfterReturning` and publishes an `ItemCreatedEvent` (`id`, `date`, `category`, `lat`, `lon`, `title`) to the `derechi.items` exchange. The routing key is `item.lost.created` or `item.found.created` depending on which service ran. `Worker` consumes it, see [Automatic matching](automatic-matching.md).

The aspect has `@Order(0)`, so it sits outside the transaction interceptor and fires only after the insert has committed; a failing `create` publishes nothing (`ItemEventAspectTest.doesNotPublishWhenCreateThrows`). The flip side: if RabbitMQ is unreachable at that moment the row is already committed but the publish throws and the mutation returns an error. There is no outbox, so such a notice simply never gets matched.

## Storage

One row in `lost_item` or `found_item`, one `contact_info` row per notice, one `place` row keyed by the Google place id. The schema is owned by `DB-Postgres`, see [Database](../architecture/database.md). Entities are copied into each service; keep the copies identical when you touch them (see [Add a GraphQL field](../extending/add-a-graphql-field.md)).

## Known gaps

- No update or delete for the public. The code is there in `ItemService` and the schema, commented out, waiting for an authentication story on `Client-API`.
- No ownership: a notice is not tied to a Keycloak user, so "my notices" cannot be built yet.
- Phone masking assumes a five-digit visible prefix, which fits Ukrainian numbers best.

## Where to look

- `Client-API/src/main/resources/graphql/schema.graphqls`
- `Client-API/src/main/java/org/shpytchuk/clientapi/service/ItemService.java`, `LostItemService.java`, `FoundItemService.java`
- `Client-API/src/main/java/org/shpytchuk/clientapi/controller/LostItemController.java`, `FoundItemController.java`
- `Client-API/src/main/java/org/shpytchuk/clientapi/util/ContactMasker.java`
- Tests: `LostItemControllerTests`, `FoundItemControllerTests`, `ItemServiceTests`, `ContactMaskerTest`, `WithinDaysLocalDateValidatorTest`

Extending: [Add a GraphQL field](../extending/add-a-graphql-field.md), [Add a category](../extending/add-a-category.md), [Add a social network](../extending/add-a-social-network.md).
