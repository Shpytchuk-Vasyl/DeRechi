# Docker image

One `Dockerfile` in the repository root builds every Spring service. Which service ends up in the image is decided by the `MODULE` build argument.

## Two stages

```dockerfile
FROM eclipse-temurin:26-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY --chmod=755 mvnw ./
COPY pom.xml ./
COPY DB-Postgres DB-Postgres
COPY Discovery Discovery
COPY Getaway Getaway
COPY Client-API Client-API
COPY Admin-API Admin-API
COPY Worker Worker
COPY Notification Notification
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:26-jre
ARG MODULE
WORKDIR /app
RUN useradd --system --uid 1001 spring
USER spring
COPY --from=build /workspace/${MODULE}/target/*.jar app.jar
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
```

**Build stage.** Identical for every service: it copies the whole reactor and runs `package` once. Because the stage does not depend on `MODULE`, Docker reuses its layer for all six images, so building the stack compiles the reactor one time, not six. The `--mount=type=cache` keeps `~/.m2` between builds, which is what makes a rebuild after a one-line change take seconds instead of downloading every dependency again.

**Runtime stage.** A JRE-only image, a non-root `spring` user and the single fat jar of the requested module. `JAVA_OPTS` is empty by default and is where compose injects heap limits and any `-D` flags.

Tests are skipped in the image build (`-DskipTests`); they run in CI and locally, not during packaging.

## Every module must be in the `COPY` list

Maven reads `<modules>` from the root POM and expects each folder to exist. A module that is listed there but not copied into the build context fails the image build with "Child module ... does not exist". **Adding a module means adding a `COPY <Name> <Name>` line** to the build stage; see [add-a-module](../extending/add-a-module.md).

The spelling must match the folder on a case-sensitive filesystem: `COPY Admin-API Admin-API`, not `admin-api`. Windows will not tell you when it is wrong; the Linux build will.

`Launcher` is intentionally absent. It is a local-only module that starts several services in one JVM and has no place in an image. It is also commented out of the root `<modules>` at the moment; if it is re-enabled it must still stay out of the `Dockerfile`, which means the build would fail unless it is moved into a Maven profile that is off by default.

## Building

Compose passes the argument:

```yaml
admin-api:
  build:
    context: .
    dockerfile: Dockerfile
    args:
      MODULE: Admin-API
```

By hand:

```bash
docker build --build-arg MODULE=Admin-API -t derechi/admin-api .
docker run --rm -p 8083:8083 -e SPRING_DATASOURCE_URL=... derechi/admin-api
```

The build context is the repository root. `.dockerignore` keeps `**/target/`, `.git/`, `.idea/`, `docker/`, the compose files and the `Dockerfile` itself out of it. `Web-Client/` is not excluded yet, so its `node_modules/` is sent to the daemon on every build; adding `Web-Client/` to `.dockerignore` would make the context noticeably smaller.

## The PostgreSQL image

`docker/postgres/Dockerfile` builds the database image used by compose:

1. A small Alpine stage downloads the Ukrainian hunspell dictionary from the `dict_uk` project (version pinned by `DICT_UK_VERSION=6.8.5`) and renames the files to `uk_ua.dict` / `uk_ua.affix`, the names PostgreSQL's ispell template expects.
2. The final stage is `postgis/postgis:17-3.5` with those files and `ukrainian.stop` copied into `tsearch_data/`, plus `initdb/` copied into `docker-entrypoint-initdb.d/`.

`initdb/01-keycloak.sql` creates the `keycloak` database on the first start of an empty volume. Migration `001` in `DB-Postgres` then creates the `ukrainian` text-search configuration on top of the dictionary, which is why the stock `postgis/postgis` image cannot be used directly for a full database and why the test harness skips that changeset.

The image is tagged `derechi/postgis:17-3.5-uk`. A commented line in the Dockerfile points to `imresamu/postgis` for macOS on Apple silicon, where the official image has no arm64 build.
