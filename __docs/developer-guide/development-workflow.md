# Development workflow

The loop from a ticket to a closed ticket. Phases 1 to 7 are yours; 8 to 13 involve the
team and the environments. None of it is bureaucracy for its own sake: every step exists
because skipping it has bitten us in this repository specifically.

## 1. Understand the task

1. Read the ticket. If anything is ambiguous, ask now, in the ticket, not after the PR.
2. If it is a bug, **reproduce it** locally before touching code. Write down the exact steps;
   they become the "How to test" section of your PR and often the integration test.
3. Find the feature in [features/](../features/README.md). Each page says how the feature
   works today and which classes implement it.
4. Check [extending/](../extending/README.md). For the changes we make often (a new language,
   country, permission, social network, channel, event, module) there is a checklist that
   lists every file to touch. Use it.
5. List the modules you will change. Remember the things that span modules:
   - entities are copied into `Admin-API`, `Client-API` and `Worker`, with the
     source of truth in `DB-Postgres`;
   - events are copied per module too (`ItemCreatedEvent`, `NotificationRequestedEvent`),
     matched by their `@EventType` id;
   - `derechi.countries` is configured in both `Client-API` and `Admin-API`.
6. Decide early whether you need a **migration** ([database changes](../processes/database-changes.md)),
   a **Keycloak realm change** ([Keycloak realm changes](../processes/keycloak-realm-changes.md))
   or a **RabbitMQ topology change** (`docker/rabbitmq/definitions.json`). Each of them is a
   manual step at release time and must be called out in the PR.
7. If the GraphQL schema will change, the `Web-Client` is affected. We only change the backend
   in backend tickets; note what the client has to update and tell its maintainer.

## 2. Set up

1. Sync and branch, following [branches and commits](branches-and-commits.md):

   ```bash
   git checkout main
   git pull --rebase
   git checkout -b feature/123-short-topic
   ```

2. Infrastructure up and schema current:

   ```bash
   docker compose up -d
   ./mvnw -pl DB-Postgres liquibase:update
   ./mvnw test-compile
   ```

## 3. Implement

- Make the smallest change that solves the ticket. Refactoring you discovered on the way goes
  into its own PR.
- Follow the [conventions](../conventions/README.md). The ones most often missed: data as
  records, Lombok only on entities and forms, `LoggerFactory.getLogger` rather than `@Slf4j`,
  dependency versions only in the root `pom.xml`, blocking code on virtual threads rather than
  WebFlux, no text in Thymeleaf templates.
- Change an entity in `DB-Postgres` **and** in every module that carries a copy.
- Add every new message key to **all five** bundles (`messages.properties`, `_uk`, `_pl`,
  `_de`, `_fr`). `MessagesTest` fails otherwise and that is on purpose.
- New admin route: annotate it with `@RequirePermission(scope, action)`; if the pair does not
  exist yet, follow [add an admin permission](../extending/add-an-admin-permission.md).
- New module: `Dockerfile` `COPY`, `docker-compose.services.yml`, both Prometheus configs,
  root `<modules>`. See [add a module](../extending/add-a-module.md).
- Configuration for containers goes into environment variables in
  `docker-compose.services.yml`; `application.yaml` keeps `localhost` defaults for IDEA.

## 4. Tests

- Unit tests for logic without a container (`FormatsTest`, `NotifyChannelTest`,
  `ContactMaskerTest` are the style).
- Slice or integration tests for every entry point: GraphQL controllers in `Client-API`
  extend `AbstractGraphQlTests`, admin controllers use `@WebMvcTest` with the security test
  support, repositories extend `AbstractRepositoryTests`. The Postgres-backed ones start a
  PostGIS Testcontainer and apply the real changelog, so **Docker must be running** or they
  are skipped silently.
- Guards that exist to catch cross-cutting mistakes: `MessagesTest` (bundle parity),
  `PermissionsTest` (scope/action matrix), `CountriesPropertiesTest` (config validation),
  `EventTypeScannerTest` (duplicate event ids).
- Run what you touched often, everything before pushing:

  ```bash
  ./mvnw -pl Admin-API test
  ./mvnw test
  ```

Details and manual recipes are in [testing locally](testing-locally.md); the conventions are
in [conventions/testing.md](../conventions/testing.md).

## 5. Migration, if the schema changed

Follow [database changes](../processes/database-changes.md) exactly: compile, diff, review by
hand, rename, update, validate with `spring-boot:run` on `DB-Postgres`, run the module tests.
Never edit an applied changeset. Backfill `NOT NULL` columns.

## 6. Run it and check by hand

Tests do not exercise Eureka, the gateway, RabbitMQ topology or Keycloak. Start the stack
([getting started](getting-started.md)) and check the change where it lives:

| Change | Where to look |
|---|---|
| GraphQL query or mutation | GraphiQL at `http://localhost:8080/graphiql`, through the gateway so routing is covered too |
| Admin page or action | `http://localhost:8083/admin` with the dev user whose role should and should not see it |
| Matching | `Worker` log, `similar_item` table, the Matches page |
| Notification | Mailpit at `http://localhost:8025`, the `Notification` log |
| Anything on RabbitMQ | `http://localhost:15672`: message counts on the queue and, above all, **nothing in a `.dlq`** |
| Image upload | MinIO console at `http://localhost:9001`, and the image served from `http://localhost:8080/files/<key>` |
| Metrics | `http://localhost:9090/targets` |

## 7. Documentation

Docs are part of the change, not a follow-up:

- the feature page in `features/` if behaviour changed;
- the checklist in `extending/` if a procedure gained or lost a step;
- the module page under `architecture/modules/` if responsibilities, queues, ports or
  configuration changed;
- `.claude/CLAUDE.md` if a rule or gotcha changed, so the condensed version stays in step.

## 8. Commit and push

Imperative, lowercase, no trailing period, body explains why. Rebase on `main` before pushing
if it moved. Details in [branches and commits](branches-and-commits.md).

```bash
git fetch origin
git rebase origin/main
git push -u origin feature/123-short-topic
```

## 9. Open a pull request

Use the template in [pull requests](pull-requests.md): what, why, how to test, migration,
Keycloak, Web-Client impact, screenshots for admin UI. Link the ticket. Paste the
[definition of done](../processes/definition-of-done.md) checklist and tick what applies.
Request a reviewer. Open it as a draft if you want early feedback on direction.

## 10. Review

Reviewers follow [code review](../processes/code-review.md). Answer every comment, push fixes
as new commits, resolve conversations you addressed, re-request review. Expect a first
response within a working day; ping after that.

## 11. Merge

Once approved and up to date with `main`: squash merge (or rebase merge if the commits are
deliberately separate), delete the branch. The author merges.

## 12. Staging, then production

The plan is a staging host running the full compose stack
(`docker-compose.yml` + `docker-compose.services.yml`), updated from `main` by a GitHub
Actions pipeline, with migrations applied before the services roll. That pipeline and host do
not exist yet; until they do, "staging" means the full compose stack on your machine:

```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

Run the smoke list from [release](../processes/release.md) against it: health endpoints,
Prometheus targets, a notice through GraphiQL, admin login, a notification in Mailpit.
Production follows the same release page.

## 13. Close the ticket

Write what changed, where it was verified and anything a user or the `Web-Client` maintainer
needs to know. Link the PR. If you found follow-up work, open tickets for it instead of
leaving it in a comment.

## Typical changes and what to read first

| Change | Read |
|---|---|
| Bug fix | The feature page in `features/`; [testing locally](testing-locally.md) to reproduce; [troubleshooting](troubleshooting.md) if it looks environmental |
| New GraphQL field or filter | [features/notices.md](../features/notices.md), [features/search-and-filtering.md](../features/search-and-filtering.md), [extending/add-a-graphql-field.md](../extending/add-a-graphql-field.md), [architecture/modules/client-api.md](../architecture/modules/client-api.md) |
| New admin page or action | [features/admin-panel.md](../features/admin-panel.md), [features/permissions.md](../features/permissions.md), [conventions/admin-ui.md](../conventions/admin-ui.md), [extending/add-an-admin-permission.md](../extending/add-an-admin-permission.md) |
| New language | [features/localization.md](../features/localization.md), [extending/add-a-language.md](../extending/add-a-language.md), [conventions/i18n.md](../conventions/i18n.md) |
| New country | [features/countries-and-currencies.md](../features/countries-and-currencies.md), [extending/add-a-country.md](../extending/add-a-country.md) |
| New social network or notification channel | [features/match-notifications.md](../features/match-notifications.md), [extending/add-a-social-network.md](../extending/add-a-social-network.md), [extending/add-a-notification-channel.md](../extending/add-a-notification-channel.md) |
| New event or consumer | [architecture/messaging.md](../architecture/messaging.md), [extending/add-an-event.md](../extending/add-an-event.md) |
| New module | [architecture/maven-reactor.md](../architecture/maven-reactor.md), [extending/add-a-module.md](../extending/add-a-module.md), [deployment/docker-image.md](../deployment/docker-image.md) |
| Schema change | [processes/database-changes.md](../processes/database-changes.md), [conventions/database-migrations.md](../conventions/database-migrations.md) |
