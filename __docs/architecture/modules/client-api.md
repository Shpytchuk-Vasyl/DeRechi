# Client-API

The public API: a GraphQL endpoint that the web client (and later the mobile client) uses to
browse and post notices. It has no authentication today; the gateway forwards `/graphql`
to it as is.

| | |
|---|---|
| Port | 8082; actuator on the management port 9082 |
| Endpoint | `POST /graphql`; GraphiQL at `/graphiql` (enabled in `application.yaml`); the schema is printed at `/graphql/schema` |
| Needs | PostgreSQL, RabbitMQ (to publish) |
| Stack | Spring MVC + Spring for GraphQL, Spring Data JPA, Hibernate Spatial, AspectJ, Spring AMQP, Lombok |

## Schema

`Client-API/src/main/resources/graphql/schema.graphqls` is the one schema file; Spring for
GraphQL adds the Relay connection types (`LostItemConnection`, `PlaceConnection`, ...) at
runtime. `Web-Client` runs code generation against this file, so a schema change is a
web-client change too, see [web-client.md](web-client.md).

| Operation | Controller | Notes |
|---|---|---|
| `lostItem(id)`, `foundItem(id)` | `LostItemController`, `FoundItemController` | `null` when missing |
| `lostItems(filter, sort, first, after)`, `foundItems(...)` | same | cursor pagination, see below |
| `createLostItem(input)`, `createFoundItem(input)` | same | `update*` and `delete*` exist in the code and schema as comments, not yet exposed |
| `claimLostItem(id, contact)`, `claimFoundItem(id, contact)`, `confirmReturn(token)` | `ClaimController` | responses to a notice and the "item is back" link, see [../../features/claims.md](../../features/claims.md) |
| `unlockLostItemClaim(itemId, id)`, `unlockFoundItemClaim(itemId, id)` | `ClaimController` | create the Fourthwall checkout for the author's phone number; `PAYMENT_UNAVAILABLE`, `UNLOCK_LIMIT` |
| `lostItemClaim(itemId, id)`, `foundItemClaim(itemId, id)` | `ClaimController` | one claim's state (was it paid), `null` when the notice has no such claim |
| `categories` | `ReferenceController` | all `thing_category` rows |
| `countries` | `ReferenceController` | supported countries with their currency, from `CountriesProperties` |
| `places(name, first, after)` | `ReferenceController` | case-insensitive substring on `name`, sorted by name |
| `stats` | `FoundItemStatisticsController` | home page figures: found notices archived over the last 7 days and found notices dated today, both in UTC, see [../../features/notices.md](../../features/notices.md#home-page-figures) |

The `Date` scalar comes from `graphql-java-extended-scalars`, registered in `GraphQlConfig`.

### Pagination

List queries take `first` and `after` and return a `Window`. `OffsetPagination` turns the
`ScrollSubrange` into a `PageRequest` (default page 20, maximum 100; `first` outside
`1..100` is a `BAD_REQUEST`) and wraps the `Page` back into a `Window` with
`OffsetScrollPosition` cursors. Cursors are therefore opaque offsets, not keyset cursors;
a list that changes between pages can skip or repeat an item. Sorting is `ItemSort`
(`DATE_DESC` default, `DATE_ASC`, `TITLE_*`) with `id desc` as a tiebreaker.

### Filtering

`ItemFilterInput` → `ThingSpecifications.byFilter` → `SpecificationBuilder`:
`search` is a case-insensitive `LIKE` over `title`, `description` and `place.name`;
`categoryId`, `dateFrom`, `dateTo` are plain predicates; `near` becomes a `dwithin` call
on `place.coordinate` with a radius in metres. `dwithin` is a custom HQL function registered
by `SpatialFunctionContributor` (`st_dwithin(?1, CAST(?2 AS geography), ?3)`), declared in
`META-INF/services/org.hibernate.boot.model.FunctionContributor`. Details in
[../../features/search-and-filtering.md](../../features/search-and-filtering.md).

### Validation and errors

Inputs are records with Bean Validation annotations (`@NotBlank`, `@Size`, E.164 phone
pattern, latitude and longitude ranges, `@WithinDays(30)` on the notice date, `radiusKm` up
to 500). Business rules sit in `ItemService`: the country must be supported, the currency
must be one of the supported countries' currencies (explicit currency wins, otherwise the
place's country decides), a found item must have an image.

`GraphQlExceptionResolver` maps `NotFoundException` to `NOT_FOUND`, and both
`ConstraintViolationException` and `IllegalArgumentException` to `BAD_REQUEST` with a
readable message, and the payment failures to `PAYMENT_UNAVAILABLE` and `UNLOCK_LIMIT`.
Anything else is an `INTERNAL_ERROR` with no detail.

### Contact masking

The API never returns a full phone number or email. `ItemMapper.toDto` runs both through
`ContactMasker` (`+38067*****67`, `f*****r@example.com`). Full contacts are only visible in
the admin panel and in the notification sent to the owner.

## Services

`ItemService<T extends Thing>` is abstract and parameterised by the entity; `LostItemService`
and `FoundItemService` differ only in the repository, the factory and whether an image is
required. `create` saves `ContactInfo`, `Place` (keyed by Google place id, so a place is
reused across notices) and the item in one transaction.

`ItemEventAspect` is an `@AfterReturning` advice on `ItemService+.create(..)` that publishes
an `ItemCreatedEvent` to `derechi.items` with `item.lost.created` or `item.found.created`.
See [../messaging.md](../messaging.md) for why it is an aspect.

`FoundItemStatisticsService` (`service/found/`) answers `stats` with two counts:
`FoundItemHistoryRepository.countByArchivedAtGreaterThanEqual` from midnight UTC six days ago
and `FoundItemRepository.countByDate` for today in UTC. `FoundItemHistory` is copied into
this module for that query only; nothing here writes history, archiving stays in `Worker`.

`ClaimService` (per kind) and `ReturnService` record responses to a notice; `ClaimEventAspect`
publishes a `ClaimEvent` with `item.<kind>.claimed` / `item.<kind>.returned` after `claim(..)` or
`confirm(..)` returns a non-repeated result, the same `@AfterReturning` + `@Order(0)` pattern as
`ItemEventAspect`. The exchange is `derechi.claims.exchange`. The item is read with a plain
`findById`, without a row lock, so two simultaneous claims with the same phone or email can
both be recorded. Planned: a `PESSIMISTIC_WRITE` lock on the item row.

## Config

`application.yaml`: datasource and RabbitMQ on `localhost`, `ddl-auto: none`,
`open-in-view: false`, virtual threads on, and

```yaml
derechi:
  countries:
    supported: [UA, PL, DE, FR]
    fallback: UA
  claims:
    exchange: derechi.items
    unlock-limit: 1
    unlock-window: P7D
  fourthwall:
    api-url: https://api.fourthwall.com/open-api/v1.0
    shop-url: ${FOURTHWALL_SHOP_URL:...}
    username: ${FOURTHWALL_API_USERNAME:...}
    password: ${FOURTHWALL_API_PASSWORD:...}
    webhook-secret: ${FOURTHWALL_WEBHOOK_SECRET:dev-secret}
    price: 1
    product-name: "Author's phone number (%s)"
    product-description: "..."
    connect-timeout: 3s
    read-timeout: 10s
```

`countries` is bound to `CountriesProperties`, which derives each country's currency from
the JDK and refuses to start on an unknown code. `fourthwall` is bound to the
`FourthwallProperties` record, which refuses to start on a missing value or a non-positive
price or timeout and also signs and checks the webhook HMAC. `FourthwallConfig` builds the
Fourthwall `RestClient` on a `JdkClientHttpRequestFactory` with those timeouts: 3 seconds to
connect, 10 seconds to read. In Compose the datasource and RabbitMQ are
set through `POSTGRES_*` and `RABBITMQ_*`; see
[environment variables](../../deployment/environment-variables.md).

## Tests

| Base class | Kind | What it gives |
|---|---|---|
| `support/AbstractPostgresTests` | Testcontainers `postgis/postgis:17-3.5`, started once per JVM, `@Testcontainers(disabledWithoutDocker = true)` | applies the `DB-Postgres` changelog from disk, stubs the `ukrainian` text search config as a copy of `simple`, truncates all tables before each test |
| `repository/AbstractRepositoryTests` | `@DataJpaTest` with `ddl-auto=validate` and the real database | repository and specification tests in `repository/thing` and `repository/detail` |
| `controller/AbstractGraphQlTests` | `@SpringBootTest` + `@AutoConfigureGraphQlTester`, `RabbitTemplate` mocked | end-to-end GraphQL tests with seeded categories: items, claims, `ReferenceControllerTests` for categories, countries and places, `FoundItemStatisticsControllerTests` for `stats` |
| `support/Fixtures` | static builders for `Place`, `ContactInfo`, items | shared test data |

Unit tests without a container cover the aspect (`ItemEventAspectTest`, Mockito), the
masker, `ItemSort`, `EventTypeScanner`, `CountriesProperties`, the `@WithinDays` validator,
the mapper and the statistics window (`FoundItemStatisticsServiceTest`). `ReferenceControllerSliceTests` is a `@GraphQlTest` slice with a mocked
repository.

```bash
./mvnw -pl Client-API test                 # needs Docker for the container-backed tests
./mvnw -pl Client-API spring-boot:run      # http://localhost:8082/graphiql
```
