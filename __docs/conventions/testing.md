# Testing

Tests are JUnit 5 with AssertJ and Spring Boot's test starters. Database-backed tests use Testcontainers against the same PostGIS image and the same Liquibase changelog as production, so a passing suite means the schema, the entities and the queries agree. How to run them day to day is in [testing-locally](../developer-guide/testing-locally.md).

## Kinds of tests

**Unit tests** (`*Test`): no Spring context, plain constructors, fast. For anything that is pure logic.

- `Client-API`: `ContactMaskerTest`, `ItemSortTest`, `ItemMapperTest`, `FoundItemStatisticsServiceTest`, `ItemCreatedEventTest`, `EventTypeScannerTest`, `WithinDaysLocalDateValidatorTest`, `CountriesPropertiesTest`
- `Admin-API`: `FormatsTest`, `NotifyChannelTest`, `ItemFilterTest`, `PermissionsTest`, `MessagesTest`, `CountriesPropertiesTest`, `MatchNotificationServiceTest`, `LostItemAdminServiceTest`, `KeycloakAuthoritiesMapperTest` (realm and client roles to authorities), `PagesTest` (the sort whitelist), `RabbitConfigTest`
- `Notification`: `NotificationSenderTest` (uses NotifyHub's `TestNotifyHub` to capture what would be sent), `NotificationRequestedListenerTest` (a failed delivery is rejected without requeue), `RabbitConfigTest`
- `Worker`: `PhoneLocalesTest`, `LanguageResolverTest`, `ClaimNotifierTest`, `ClaimFollowUpJobTest`, `ItemArchiverTest`, `PaidHandlerTest`, `ClaimListenerTest`, `ArchiveListenerTest`, `ItemCreatedListenerTest`, `ClaimHandlersTest` (`ClaimedHandler` and `ReturnedHandler`, replays included), `RabbitConfigTest`, `MessagesTest`

**Slice tests** (`*Tests`): one layer with a Spring context.

- `Admin-API` controllers: `@WebMvcTest` + `MockMvc`, with `oidcLogin().authorities(...)` from `security-oauth2-client-test` to act as an admin holding specific `SCOPE:ACTION` authorities. Services are `@MockitoBean`s. Examples: `LostItemControllerTests`, `LostItemHistoryControllerTests`, `MatchControllerTests` (list, candidates, notify, 403s), `ItemFormPlaceSearchTests`, `UploadControllerTests`, `GlobalExceptionHandlerTests` (404/408/500 pages). `ItemControllerPermissionsTests` walks every route of the four item controllers: its own `SCOPE:ACTION` opens it, the same action on the neighbouring scope gets 403.
- `Client-API` repositories: `AbstractRepositoryTests` on top of a real database (`thing/ThingRepositoryTests`, `detail/PlaceRepositoryTests`, `detail/ContactInfoRepositoryTests`).
- `Admin-API` on a real database (`repository/AbstractRepositoryTests`): `service/AdminItemServiceTests` (create, update, filter and sort, delete with matches, claims and the claimants' contacts, for live and archived notices), `service/ItemClaimsTests`, `service/matching/MatchServiceTests` (the top-three preview query and the totals).
- `Worker` on a real database (`repository/AbstractRepositoryTests`): `service/ItemServiceTests` (the candidate query and `rankAll`: category, ±3 days, 20 km, relevance order, five at most), `handler/ItemCreatedHandlerTests` (rows in `similar_item`, a redelivered event), `repository/ClaimRepositoryTests` (the follow-up job's derived queries), `cron/ClaimFollowUpsTests` (purge and reminder stamps against the foreign keys), `service/ItemArchiverTests` (history copy, claims re-pointed under the `CHECK`, matches dropped).
- `Client-API` GraphQL: `AbstractGraphQlTests` starts the full context with `@AutoConfigureGraphQlTester`, mocks `RabbitTemplate`, and seeds categories before each test (`LostItemControllerTests`, `FoundItemControllerTests`, `ClaimControllerTests`, `ReferenceControllerTests` for categories, countries and places, `FoundItemStatisticsControllerTests` for `stats`; `ReferenceControllerSliceTests` runs the lighter GraphQL slice). `ItemEventAspectTest` and `ItemServiceTests` sit on the same base.

**Context tests** (`*ApplicationTests`): every module has one that starts the context and nothing else. They catch broken wiring (a missing bean, a bad property) and are the only tests in `Getaway` and `DB-Postgres`. `WorkerApplicationTests` runs on the test container with `ddl-auto=validate`, because the scheduled `ClaimFollowUpJob` starts with the context and writes; the others use the datasource from `application.yaml`. `AdminApiApplicationTests` resolves the Keycloak issuer on startup and fails unless Keycloak is running.

**Infrastructure tests** (`*IT`): need something running outside the JVM that Testcontainers does not provide. `ImageStorageIT` talks to the MinIO from `docker compose` and is guarded by `@EnabledIf("minioIsUp")`, so it is skipped when the container is not there.

**Stack tests**: integration (`tests/integration`, JUnit `*IT` against running services) and load (`tests/load`, k6), outside the Maven reactor, run with `tests/run.sh` on an isolated compose stack. See [stack tests](../developer-guide/stack-tests.md).

**Browser tests** (`Web-Client/e2e`, Playwright): the site in a real browser against the running backend, outside the Maven reactor. What they cover and how to run them: [`Web-Client/e2e/README.md`](../../Web-Client/e2e/README.md).

**Message contract tests**: `RabbitConfigTest` in `Admin-API`, `Worker` and `Notification`. The modules share no event classes, only the `@EventType` id in the `__TypeId__` header; each test pins the ids its module writes or reads through the real `RabbitConfig` converter, so renaming an id on one side fails that side's build.

**Configuration tests**: `MessagesTest` (all five bundles have the same keys and placeholders) and `PermissionsTest` (every `Scope` x `Action` pair the code can ask for exists). They fail on a forgotten file, not on a logic bug, and they are the reason "add a key to all five bundles" is enforced rather than remembered.

## The database harness

`Client-API/src/test/java/org/shpytchuk/clientapi/support/AbstractPostgresTests.java`:

- starts `postgis/postgis:17-3.5` once per JVM (`@Testcontainers(disabledWithoutDocker = true)`),
- applies `DB-Postgres/changelog/changelog-master.yaml` through the Liquibase API, reading the changelog from the sibling module's folder,
- skips the Ukrainian and Polish full-text configurations (`skipHunspellFullTextSearch`), because the hunspell dictionaries only exist in our custom Postgres image (see [docker-image](../deployment/docker-image.md)),
- truncates every table with `RESTART IDENTITY CASCADE` before each test,
- points `spring.datasource.*` at the container through `@DynamicPropertySource`.

`Fixtures` builds entities for these tests. Extend it rather than building entities inline. `Admin-API` and `Worker` have their own `support/Fixtures`.

A consequence worth knowing: a new migration is exercised by `./mvnw -pl Client-API test` even if you touch nothing in `Client-API`. Run it after writing a changeset.

`Admin-API` and `Worker` carry copies of the same `support/AbstractPostgresTests`, with a `repository/AbstractRepositoryTests` (`@DataJpaTest`, `ddl-auto=validate`) on top. A service test on the database extends it and `@Import`s the service. `Worker`'s `Place` maps only the id and the coordinate, so its `AbstractRepositoryTests.place(...)` inserts places with SQL. Remember that `@DataJpaTest` rolls back: call `entityManager.flush()` before asserting, or constraint violations and deferred deletes never reach the database.

## Layout

A test lives in the same package as the class it covers, sub-package included: `service/lost/LostItemAdminServiceTest` next to `service/lost/LostItemAdminService`, `repository/detail/PlaceRepositoryTests` next to `repository/detail/PlaceRepository`. Base classes and fixtures stay at the root of their package (`repository/AbstractRepositoryTests`, `support/Fixtures`) and are public, so the sub-packages can reach them. A test for a class that serves both notice kinds (`ItemServiceTests`, `ClaimControllerTests`) stays at the root like the class.

## Rules

**A feature ships with its tests.** The minimum is a unit test for the logic and a slice test for the entry point (controller, GraphQL operation, listener). A bug fix starts with a failing test that reproduces it.

**A Web-Client feature or change ships with browser tests too.** A new page, form, filter or other user-visible behaviour gets a Playwright test in `Web-Client/e2e/specs`; a change to existing behaviour updates the tests that cover it (search `Web-Client/e2e/specs` by the page or feature). Tests for a removed feature are deleted with it. A bug fix starts with a test that fails on the bug.

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
./mvnw -pl Worker jacoco:prepare-agent test jacoco:report   # coverage, report in Worker/target/site/jacoco
```

JaCoCo is declared in the root `pluginManagement` only and is not bound to the lifecycle, so a plain `test` does not instrument anything. Add `clean` after moving packages, or the report keeps the old classes.

Docker must be running for the Testcontainers-based tests. Without it they are **skipped, not failed** (`disabledWithoutDocker = true`), so a green build on a machine without Docker proves less than it looks like. Check the surefire summary for the skipped count before trusting it, and run the database tests at least once before opening a pull request.

Remember the timezone gotcha if the JVM default is `Europe/Kiev`: the container rejects it, see [troubleshooting](../developer-guide/troubleshooting.md).
