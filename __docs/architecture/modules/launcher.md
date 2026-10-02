# Launcher

A convenience for local development: one `main` that boots several services as separate
Spring contexts inside a single JVM, so the whole backend can be started with one run
configuration and one debugger. It has no business code and is never deployed.

**Status: disabled.** The module directory exists (`Launcher/`), but its `<module>` entry
in the root `pom.xml` is commented out, so it is not built, and it is not in the
Dockerfile's `COPY` list. Read the "Re-enabling" section before switching it on.

## What it does

`DeRechiLauncher` (`Launcher/src/main/java/org/shpytchuk/launcher/`):

1. Finds each service's `application.yaml` on the class path by its
   `spring.application.name`, so every context gets exactly its own module's configuration
   and nothing else (`spring.config.location` is pointed at a non-existent directory to stop
   the usual lookup).
2. Rewrites every `lb://SERVICE` value in those configs to `http://localhost:<that
   service's port>` and sets `eureka.client.enabled=false`. Inside one JVM there is no need
   for a registry, and this is also why `Discovery` is commented out of the `SERVICES` list.
3. Starts, in order, `Getaway` (reactive), `Client-API`, `Admin-API` and `Worker`
   (servlet) with `SpringApplicationBuilder`, each with a list of auto-configurations
   excluded so that one module's classpath does not leak into another's context (security
   into `Client-API`, GraphQL into `Admin-API`, persistence and AMQP into `Getaway`, the
   gateway into everyone else).
4. Prints the local URLs and closes the contexts in reverse order on shutdown; a context
   that fails to start stops the others and exits with 1.

`Notification` is not in the list. It needs its own exclusions worked out before it can
join.

## Why it is the odd one out

- It is the **only** module that depends on other service modules (`Discovery`, `Getaway`,
  `Client-API`, `Admin-API`, `Worker` are its dependencies). Everything else
  depends only on libraries.
- All service jars are on one classpath, so auto-configuration has to be excluded by hand
  and bean name clashes are possible. A real deployment runs one process per service.
- Because of the first point it cannot be built inside the Docker image without copying
  every module anyway, and the image has no use for it.

## Re-enabling

1. Uncomment `<module>Launcher</module>` in the root `pom.xml`.
2. Add `COPY Launcher Launcher` to the `Dockerfile`'s build stage. Maven refuses to build a
   reactor whose `<module>` directory is missing, so without this line
   `docker compose -f docker-compose.yml -f docker-compose.services.yml up --build` fails
   even though no image is built from `Launcher`. Alternatively put the module in a Maven
   profile that the Docker build does not activate.
3. Build the reactor (`./mvnw -DskipTests package`) so every service's classes and
   `application.yaml` are on the launcher's class path, then run `DeRechiLauncher` from the
   IDE or with `./mvnw -pl Launcher spring-boot:run`.
4. Keep infrastructure running in Docker (`docker compose up -d`). The launcher replaces the
   service processes, not PostgreSQL, RabbitMQ, Keycloak or MinIO.

The simpler alternative, and the one the team uses day to day, is one IDE run
configuration per service; see
[../../deployment/local-development.md](../../deployment/local-development.md).
