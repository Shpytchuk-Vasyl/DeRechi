# Release

This page describes the intended release process. Parts of it are already how we work (the
image build, the deploy order, migrations applied on `up`); parts of it are not set up yet (CI,
a staging host, tags). Where something does not exist in the repository yet, it says so.

## Versioning

- The Maven reactor is at `1.0-SNAPSHOT`. Modules inherit the version from the root
  `pom.xml` and must not declare their own, so a version bump is a one-line change there.
- Plan: tag releases on `main` as `vX.Y.Z` and bump the root version to the next
  `-SNAPSHOT` right after tagging. Patch for fixes, minor for features, major for a schema or
  API change that breaks the `Web-Client`.
- The `Web-Client` has its own `package.json` version and is released independently; the
  only coupling is the GraphQL schema it was generated against (`pnpm codegen`).

## What a release contains

- One Docker image per service, built from the root `Dockerfile` with `--build-arg MODULE=...`.
  The build stage compiles the whole reactor once and is cached; the runtime stage copies one
  module's jar. `docker-compose.services.yml` already does this for `DB-Postgres`, `Getaway`,
  `Client-API`, `Admin-API`, `Worker` and `Notification`.
- `DB-Postgres` has an image but is not a long-running service: the `db-postgres` container
  applies pending migrations and exits. See [database changes](database-changes.md).
- `Launcher` is a local-development convenience, is commented out in the root `<modules>`, and
  is never shipped.
- Infrastructure configuration that lives in Git and may change with a release:
  `docker/keycloak/realms/derechi-realm.json`, `docker/rabbitmq/definitions.json`,
  `docker/prometheus/*.yml` (scrape configs and `alerts.yml`), `docker/alertmanager/`,
  `docker/minio/derechi-app-policy.json` and `docker/grafana/provisioning`.
  `docker/keycloak/dev/` holds the dev users and is local only: its volume line and the
  `start-dev` command stay commented out in `docker-compose.yml` on the production host.

## Pre-release checklist

- [ ] `main` is green: `./mvnw test-compile` and `./mvnw test` with Docker running.
- [ ] Every merged PR met the [definition of done](definition-of-done.md).
- [ ] The release notes list migrations, Keycloak realm changes and RabbitMQ topology changes
      explicitly; each of those needs a manual step below.
- [ ] `Web-Client` was regenerated against the schema (`pnpm codegen`) if the schema changed.

## Order of operations on an environment

1. **Keycloak realm.** If roles, clients or users changed, apply them first. The realm JSON is
   imported only on first start, so on an existing environment the change is made in the
   console or by re-import. See [Keycloak realm changes](keycloak-realm-changes.md).
2. **RabbitMQ topology.** `rabbitmq-init` imports `definitions.json` over the management API on
   every `up`, and the services wait for it, so a new exchange, queue or binding reaches a
   running broker with the deploy. The import only adds and updates: removing or narrowing a
   binding is still done by hand in the management UI, and a changed queue argument makes the
   import fail, after which the services do not start.
3. **Database.** Nothing to run by hand. The `db-postgres` container applies pending
   changesets during step 5, then validates the schema against the entities. `Client-API`,
   `Admin-API` and `Worker` wait for it to exit with 0. If it fails, Compose reports
   `dependency failed to start` and does not start them (`docker logs derechi-db-postgres`). The first deploy
   to an environment whose schema was applied by hand needs a one-time `liquibase:changelogSync`;
   see [DB-Postgres/README.md](../../DB-Postgres/README.md#a-database-whose-schema-was-applied-by-hand).
4. **Build images** for the services that changed (or all of them; the reactor build is cached):

   ```bash
   docker compose --env-file env.prod -f docker-compose.yml -f docker-compose.services.yml build
   ```

5. **Deploy the services** in any order. `Getaway` last avoids a few seconds of 5xx on
   `/graphql` while `Client-API` restarts. The only start order is the one Compose enforces:
   `db-postgres` before the services that use the database.
6. **Web-Client** is built and started separately with `pnpm build` and `pnpm start`, pointed at
   the gateway through `GRAPHQL_URL` and `NEXT_PUBLIC_FILES_URL`. If the release takes the API
   down for longer than a restart, deploy the web client with `NEXT_PUBLIC_MAINTENANCE=true`
   first and switch it back off once the smoke checks pass. Only the home page and the legal
   pages stay open; see [Web-Client feature flags](../architecture/modules/web-client.md#feature-flags).

With the compose stack this collapses to one command, which is how we run it today:

```bash
docker compose --env-file env.prod -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

## Smoke checks after deploy

- [ ] Every service reports `UP` on `/actuator/health`. Actuator is on the management ports
      (9080, 9082, 9083, 9084, 9085), which are internal to the compose network, so check it
      in Prometheus (`up{job="derechi-services"}` is 1 for all five, no `TargetDown` alert) or
      from inside a container.
- [ ] `/graphql` through the gateway answers, which proves the `client-api` route points at the
      right `CLIENT_API_URI` (the gateway no longer exposes `/actuator/gateway/routes`).
- [ ] Prometheus `Status > Targets` shows every target up, including Keycloak, MinIO and RabbitMQ.
- [ ] Prometheus `Alerts` shows no firing alert, and Alertmanager (port 9093) lists none.
- [ ] GraphiQL through the gateway (`/graphiql`) runs `categories` and `countries`, and a
      `createLostItem` mutation succeeds.
- [ ] `Worker` logs the search for that item and nothing lands in
      `worker.items.dlq` (or any other `*.dlq`; the `DeadLetters` alert fires otherwise).
- [ ] The admin panel logs in through Keycloak and the Matches page renders.
- [ ] Notifying a candidate produces an email at the real mail provider (Mailpit only runs
      with the `dev` profile) and the
      row shows "Notified".
- [ ] An image upload through the gateway lands in the `derechi-files` bucket and is served
      back from `/files/<key>`.

## Rollback

- **Services** roll back by redeploying the previous image. Keep the previous tag around.
- **Migrations** do not roll back with the service. Prefer backward-compatible migrations so
  an old service can run on the new schema. If a migration must be undone, write a new
  changeset that reverses it, or use `liquibase:rollback` when the changeset has a
  `--rollback` line (most of ours do not).
- **Keycloak and RabbitMQ** changes are additive in practice; removing a role or a binding is
  a deliberate manual step, not part of a rollback.

## Where we want to get to

Two pieces exist: the `tests` workflow runs the module suites and the integration tests on
demand, and `images` builds and pushes the images on demand. The plan is to chain them, then apply migrations to staging, roll the compose stack on a
staging host and promote the same images to production after the smoke checks above pass.
The deploy part does not exist yet; until it does, the steps on this page are run by
hand by whoever releases.
