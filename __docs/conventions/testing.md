# Testing

Tests are JUnit 5 with AssertJ and Spring Boot's test starters. Database-backed tests use Testcontainers against the same PostGIS image and the same Liquibase changelog as production, so a passing suite means the schema, the entities and the queries agree. How to run them day to day is in [testing-locally](../developer-guide/testing-locally.md).

## Kinds of tests

**Unit tests** (`*Test`): no Spring context, plain constructors, fast. For anything that is pure logic.

- `Client-API`: `ContactMaskerTest`, `ItemSortTest`, `ItemMapperTest`, `ItemCreatedEventTest`, `EventTypeScannerTest`, `WithinDaysLocalDateValidatorTest`, `CountriesPropertiesTest`
- `Admin-API`: `FormatsTest`, `NotifyChannelTest`, `ItemFilterTest`, `PermissionsTest`, `MessagesTest`, `CountriesPropertiesTest`, `MatchNotificationServiceTest`, `LostItemAdminServiceTest`
- `Notification`: `NotificationSenderTest` (uses NotifyHub's `TestNotifyHub` to capture what would be sent)
- `Automatic-Search`: `PhoneLocalesTest`, `ClaimNotifierTest`, `ClaimFollowUpJobTest`, `ItemArchiverTest`, `ClaimListenerTest`, `ArchiveListenerTest`, `MessagesTest`

**Slice tests** (`*Tests`): one layer with a Spring context.

- `Admin-API` controllers: `@WebMvcTest` + `MockMvc`, with `oidcLogin().authorities(...)` from `security-oauth2-client-test` to act as an admin holding specific `SCOPE:ACTION` authorities. Services are `@MockitoBean`s. Examples: `LostItemControllerTests`, `LostItemHistoryControllerTests`, `MatchControllerTests`, `ItemFormPlaceSearchTests`.
- `Client-API` repositories: `AbstractRepositoryTests` on top of a real database (`ThingRepositoryTests`, `PlaceRepositoryTests`, `ContactInfoRepositoryTests`).
- `Client-API` GraphQL: `AbstractGraphQlTests` starts the full context with `@AutoConfigureGraphQlTester`, mocks `RabbitTemplate`, and seeds categories before each test (`LostItemControllerTests`, `FoundItemControllerTests`, `PlaceControllerTests`, `ReferenceControllerTests`; `ReferenceControllerSliceTests` runs the lighter GraphQL slice). `ItemEventAspectTest` and `ItemServiceTests` sit on the same base.

**Context tests** (`*ApplicationTests`): every module has one that starts the context and nothing else. They catch broken wiring (a missing bean, a bad property) and are the only tests in `Discovery`, `Getaway` and `DB-Postgres`.

**Infrastructure tests** (`*IT`): need something running outside the JVM that Testcontainers does not provide. `ImageStorageIT` talks to the MinIO from `docker compose` and is guarded by `@EnabledIf("minioIsUp")`, so it is skipped when the container is not there.

**Configuration tests**: `MessagesTest` (all five bundles have the same keys and placeholders) and `PermissionsTest` (every `Scope` x `Action` pair the code can ask for exists). They fail on a forgotten file, not on a logic bug, and they are the reason "add a key to all five bundles" is enforced rather than remembered.

## The database harness

`Client-API/src/test/java/org/shpytchuk/clientapi/support/AbstractPostgresTests.java`:

- starts `postgis/postgis:17-3.5` once per JVM (`@Testcontainers(disabledWithoutDocker = true)`),
- applies `DB-Postgres/changelog/changelog-master.yaml` through the Liquibase API, reading the changelog from the sibling module's folder,
- skips the Ukrainian full-text configuration, because the hunspell dictionary only exists in our custom Postgres image (see [docker-image](../deployment/docker-image.md)),
- truncates every table with `RESTART IDENTITY CASCADE` before each test,
- points `spring.datasource.*` at the container through `@DynamicPropertySource`.

`Fixtures` builds entities for these tests. Extend it rather than building entities inline.

A consequence worth knowing: a new migration is exercised by `./mvnw -pl Client-API test` even if you touch nothing in `Client-API`. Run it after writing a changeset.

`Admin-API` declares the Testcontainers dependencies but has no database-backed tests yet; its services are tested through mocks. When adding one, copy the `AbstractPostgresTests` approach rather than inventing a second harness.

## Rules

**A feature ships with its tests.** The minimum is a unit test for the logic and a slice test for the entry point (controller, GraphQL operation, listener). A bug fix starts with a failing test that reproduces it.

**Test the authority, not just the happy path.** Every new admin handler gets two cases: with the right `SCOPE:ACTION` it works, without it the response is 403. `MatchControllerTests` shows the pattern with `Permissions.authority(scope, action)`.

**Do not hit RabbitMQ in tests.** Mock `RabbitTemplate` (`@MockitoBean`) and assert on `convertAndSend`. The publishing side is covered by `ItemEventAspectTest`; the consuming side is covered by calling the listener method directly.

**Do not rely on test order or leftover rows.** The harness truncates between tests; each test seeds what it needs.

**Name tests by behaviour.** `everySupportedLanguageHasItsOwnBundle`, `sendsToEmailAndPhone`, not `test1`.

**Keep `*IT` for real external infrastructure** and make it self-disabling, as `ImageStorageIT` does, so `./mvnw test` on a laptop without the compose stack still passes.

## Running

```bash
./mvnw test                                  # whole reactor
./mvnw -pl Client-API test                   # one module
./mvnw -pl Admin-API test -Dtest=MatchControllerTests
./mvnw -pl Admin-API test -Dtest='*Test'      # unit tests only, by naming convention
```

Docker must be running for the Testcontainers-based tests. Without it they are **skipped, not failed** (`disabledWithoutDocker = true`), so a green build on a machine without Docker proves less than it looks like. Check the surefire summary for the skipped count before trusting it, and run the database tests at least once before opening a pull request.

Remember the timezone gotcha if the JVM default is `Europe/Kiev`: the container rejects it, see [troubleshooting](../developer-guide/troubleshooting.md).
