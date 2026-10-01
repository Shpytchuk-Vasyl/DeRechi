# Release

This page describes the intended release process. Parts of it are already how we work (the
image build, the deploy order, the manual migration); parts of it are not set up yet (CI,
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
  module's jar. `docker-compose.services.yml` already does this for `Discovery`, `Getaway`,
  `Client-API`, `Admin-API`, `Automatic-Search` and `Notification`.
- `DB-Postgres` is not a service and has no image. Its migrations are applied by hand, see
  [database changes](database-changes.md).
- `Launcher` is a local-development convenience, is commented out in the root `<modules>`, and
  is never shipped.
- Infrastructure configuration that lives in Git and may change with a release:
  `docker/keycloak/realms/derechi-realm.json`, `docker/rabbitmq/definitions.json`,
  `docker/prometheus/*.yml`, `docker/grafana/provisioning`.

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
2. **RabbitMQ topology.** `definitions.json` is loaded only when the broker starts with an empty
   volume. A new exchange, queue or binding on a running broker is created by hand in the
   management UI or with `rabbitmqadmin`, matching the file. Do this before the consumer that
   needs it is deployed, otherwise its `@RabbitListener` fails to start.
3. **Database.** `./mvnw -pl DB-Postgres liquibase:update` against the environment's database.
4. **Build images** for the services that changed (or all of them; the reactor build is cached):

   ```bash
   docker compose -f docker-compose.yml -f docker-compose.services.yml build
   ```

5. **Deploy `Discovery` first**, wait for it to be healthy, then the rest. Services register in
   Eureka on start and the gateway routes by `lb://` name, so order among the rest does not
   matter much, but `Getaway` last avoids routing to instances that are not up yet.
6. **Web-Client** is built and started separately with `pnpm build` and `pnpm start`, pointed at
   the gateway through `GRAPHQL_URL` and `NEXT_PUBLIC_FILES_URL`.

With the compose stack this collapses to one command, which is how we run it today:

```bash
docker compose -f docker-compose.yml -f docker-compose.services.yml up -d --build
```

## Smoke checks after deploy

- [ ] Every service answers on `/actuator/health` with `UP` (ports 8761, 8080, 8082, 8083,
      8084, 8085).
- [ ] Eureka at `http://<host>:8761` lists all five services.
- [ ] Prometheus `Status > Targets` shows every target up, including Keycloak, MinIO and RabbitMQ.
- [ ] GraphiQL through the gateway (`/graphiql`) runs `categories` and `countries`, and a
      `createLostItem` mutation succeeds.
- [ ] `Automatic-Search` logs the search for that item and nothing lands in
      `automatic-search.items.dlq`.
- [ ] The admin panel logs in through Keycloak and the Matches page renders.
- [ ] Notifying a candidate produces an email in Mailpit (or the real mail provider) and the
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

The plan is a GitHub Actions pipeline that builds the reactor, runs the tests with Docker,
builds and pushes the images, applies migrations to staging and rolls the compose stack on a
staging host, then promotes the same images to production after the smoke checks above pass.
None of that exists in the repository yet; until it does, the steps on this page are run by
hand by whoever releases.
