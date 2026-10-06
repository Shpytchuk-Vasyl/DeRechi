# Definition of done

A ticket is done when every line below is true. Copy the checklist into the pull request
description; the reviewer ticks the items they verified, the author ticks the rest.

## Code

- [ ] The change does what the ticket asks and follows the [conventions](../conventions/README.md).
- [ ] Entity changes are mirrored in `DB-Postgres` and in every service copy of the entity.
- [ ] New message keys exist in all five bundles and `MessagesTest` passes.
- [ ] No secrets, personal API keys or machine-specific paths were committed.

## Tests

- [ ] Unit tests cover the new logic; slice or integration tests cover every new entry point
      (GraphQL field, admin route, RabbitMQ consumer).
- [ ] `./mvnw test-compile` and `./mvnw test` pass locally with Docker running, so the
      Testcontainers tests actually ran rather than being skipped.

## Database

- [ ] If the schema changed: the migration was generated with `liquibase:diff`, reviewed by hand,
      renamed to `NNN-meaningful-name.postgresql.sql`, applied locally, and `DB-Postgres`
      starts cleanly with `ddl-auto: validate`. See [database changes](database-changes.md).
- [ ] If Keycloak roles, clients or users changed: the realm JSON in Git reflects it. See
      [Keycloak realm changes](keycloak-realm-changes.md).
- [ ] If RabbitMQ topology changed: `docker/rabbitmq/definitions.json` reflects it.

## Verification

- [ ] The change was exercised by hand in the running application, not only by tests
      (admin panel, GraphiQL, RabbitMQ UI, Mailpit as appropriate). See
      [testing locally](../developer-guide/testing-locally.md).
- [ ] Nothing new lands in a dead-letter queue during that check.

## Documentation

- [ ] The relevant page in [features/](../features/README.md) describes the new behaviour.
- [ ] If a procedure changed, the checklist in [extending/](../extending/README.md) is updated.
- [ ] The module page under `architecture/modules/` is updated if the module's responsibilities,
      ports, queues or configuration changed.
- [ ] `.claude/CLAUDE.md` is updated if a rule or gotcha changed.
- [ ] Impact on the `Web-Client` (schema change, files route, cookie) is written in the PR and
      communicated to whoever maintains it.

## Delivery

- [ ] The PR was reviewed and approved per [code review](code-review.md), merged, and the
      branch deleted.
- [ ] The change was verified on staging after deploy (once a staging environment exists; until
      then, verified on the full compose stack with
      `docker compose --profile dev -f docker-compose.yml -f docker-compose.services.yml up -d --build`).
- [ ] The ticket is closed with a note on what changed and how it was verified.
