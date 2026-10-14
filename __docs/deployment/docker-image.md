# Docker image

One `Dockerfile` in the repository root builds every Spring service and the one-shot `db-postgres` migration job (`MODULE=DB-Postgres`). Which module ends up in the image is decided by the `MODULE` build argument.

## Stages

```dockerfile
FROM eclipse-temurin:26-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY --chmod=755 mvnw ./
COPY pom.xml ./
COPY DB-Postgres DB-Postgres
# COPY Getaway Getaway
COPY Client-API Client-API
COPY Admin-API Admin-API
COPY Worker Worker
COPY Notification Notification
COPY Ingest Ingest
RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -B --strict-checksums -DskipTests package
RUN for jar in */target/*.jar; do \
      java -Djarmode=tools -jar "$jar" extract --layers --application-filename app.jar \
        --destination "layers/${jar%%/*}"; \
    done

FROM eclipse-temurin:26-jre AS ingest
# ... Tesseract, /app/tdlib, argument-forwarding entrypoint, see below

FROM eclipse-temurin:26-jre
ARG MODULE
WORKDIR /app
RUN useradd --system --uid 1001 spring
USER spring
COPY --from=build /workspace/layers/${MODULE}/dependencies/ ./
COPY --from=build /workspace/layers/${MODULE}/spring-boot-loader/ ./
COPY --from=build /workspace/layers/${MODULE}/snapshot-dependencies/ ./
COPY --from=build /workspace/layers/${MODULE}/application/ ./
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
```

**Build stage.** Identical for every service: it copies the whole reactor and runs `package` once. Because the stage does not depend on `MODULE`, Docker reuses its layer for all six images, so building the stack compiles the reactor one time, not six. The `--mount=type=cache` keeps `~/.m2` between builds, which is what makes a rebuild after a one-line change take seconds instead of downloading every dependency again. `sharing=locked` stops the parallel image builds from writing to that cache at the same time. `--strict-checksums` fails a download whose checksum does not match. Without it, Maven only warns and keeps the broken jar in the cache, and every later build fails with `ZipException ... zip END header not found`. If that error appears anyway, clear the cache with `docker builder prune --filter type=exec.cachemount -f`.

**Layers.** After `package` the build stage splits every fat jar into Spring Boot's layers (`java -Djarmode=tools ... extract --layers`): `dependencies` (the released libraries, 80+ MB), `spring-boot-loader` and `snapshot-dependencies` (both empty for us, kept so the `COPY` lines never miss a folder) and `application` (`app.jar`, our classes, a few hundred KB, with a manifest `Class-Path` into `lib/`). The runtime stage copies them as four layers, least changed first. A code change rebuilds only the last one, so CI pushes and `docker compose pull` downloads a few hundred KB per service instead of the whole fat jar; the dependency layer changes only when a library does. The extracted libraries keep the timestamps of their jar entries, which keeps that layer identical between builds.

**Runtime stage.** A JRE-only image, a non-root `spring` user and the layers of the requested module, started with `java -jar /app/app.jar` like a fat jar. `JAVA_OPTS` is empty by default and is where compose injects heap limits and any `-D` flags. It must stay the **last** stage: a compose service without `build.target` gets the last stage, and every service except `ingest` relies on that.

**`ingest` stage.** Ingest has its own final stage, picked by `build.target: ingest` in compose, for two reasons:

- It needs native packages the other images should not carry: `tesseract-ocr` (brings English) and `tesseract-ocr-ukr` from Ubuntu, with `TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata`, which is what `derechi.redaction.tessdata` reads. It also creates `/app/tdlib`, owned by `spring`, where the `tdlib-data` volume with the TDLib session is mounted.
- Its entrypoint forwards arguments to the jar, so the one-off session login works: `docker compose run --rm -it ingest --login`.

  ```dockerfile
  ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
  ```

  The trailing `"--"` becomes `$0` of the shell, so everything after the service name lands in `"$@"`. The generic stage drops arguments, and nothing else needs them.

BuildKit only builds the stages the requested target needs, so the other images never run the `apt-get` of this stage. The build stage still needs `https://mvn.mchv.eu/repository/mchv/` (TDLight is not on Maven Central, the repository is declared in `Ingest/pom.xml`). It serves `.sha1` files, so `--strict-checksums` checks TDLight too.

Tests are skipped in the image build (`-DskipTests`); they run in CI and locally, not during packaging.

## Every module must be in the `COPY` list

Maven reads `<modules>` from the root POM and expects each folder to exist. A module that is listed there but not copied into the build context fails the image build with "Child module ... does not exist". **Adding a module means adding a `COPY <Name> <Name>` line** to the build stage; see [add-a-module](../extending/add-a-module.md).

The other way round works too: `Getaway` is not deployed for now (the API entry is Caddy, see [infrastructure](infrastructure.md#caddy-api-entry)), so its `COPY` line is commented out together with its `<module>` entry, and the two come back together. While both are commented out, the build stage does not see the module at all.

The spelling must match the folder on a case-sensitive filesystem: `COPY Admin-API Admin-API`, not `admin-api`. Windows will not tell you when it is wrong; the Linux build will.

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
docker run --rm -p 8083:8083 -e POSTGRES_HOST=... -e POSTGRES_PASSWORD=... derechi/admin-api
```

### In CI

The `images` workflow (`.github/workflows/images.yml`) pushes the arm64 images to GHCR with one `docker buildx bake` over `docker-bake.hcl`, in a single job. Bake sends every target to the same builder, so the build stage runs once for all images, as in compose. A matrix with a job per image would compile the whole reactor in every job.

Two caches survive between runs:

- **`~/.m2`.** A runner starts with an empty builder, and BuildKit does not put `--mount=type=cache` into the GitHub cache. `reproducible-containers/buildkit-cache-dance` moves the mount in and out of `actions/cache`, keyed by the hash of every `pom.xml` and the wrapper properties. Without it every run downloads all dependencies and Maven itself.
- **Layers**, `type=gha` with a scope per image and the default `min` mode: only the layers of the final images, such as the `apt-get` of `ingest` and the dictionaries of `postgis`. The build stage is not exported: any commit changes its input, so it would be uploaded on every run and never read back.

Run workflow has a `target` dropdown: `default` (the bake group, every image) or one target, handed to bake as
`targets`. A single image still compiles the whole reactor, it just builds and pushes one final stage. The
concurrency group is per choice, so a new run cancels only an older run of the same choice.

A new image is a new target in `docker-bake.hcl` (and in its `default` group) plus an option in the workflow's
`target` dropdown; without the option it is still built by `default`.

### Context

The build context is the repository root. `.dockerignore` keeps `**/target/`, `.git/`, `.idea/`, `docker/`, the compose files and the `Dockerfile` itself out of it. `Web-Client/` is not excluded yet, so its `node_modules/` is sent to the daemon on every build; adding `Web-Client/` to `.dockerignore` would make the context noticeably smaller.

## The PostgreSQL image

`docker/postgres/Dockerfile` builds the database image used by compose:

1. A small Alpine stage downloads the Ukrainian hunspell dictionary from the `dict_uk` project (version pinned by `DICT_UK_VERSION=6.8.5`) and renames the files to `uk_ua.dict` / `uk_ua.affix`, the names PostgreSQL's ispell template expects. The same stage downloads the Polish `pl_PL` dictionary (sjp.pl) from `LibreOffice/dictionaries` at the commit pinned in `DICT_PL_COMMIT`, converts it from ISO-8859-2 to UTF-8 (PostgreSQL reads dictionaries only in UTF-8, the `SET` line of the affix file is rewritten too) and saves it as `pl_pl.dict` / `pl_pl.affix`.
2. The final stage is `postgis/postgis:17-3.5` with those files and `ukrainian.stop` and `polish.stop` (the `stopwords-iso` list) copied into `tsearch_data/`, plus `initdb/` copied into `docker-entrypoint-initdb.d/`.

`initdb/01-keycloak.sh` creates the `keycloak` database on the first start of an empty volume. Migrations `001` and `010` in `DB-Postgres` then create the `ukrainian` and `polish` text-search configurations on top of the dictionaries, which is why the stock `postgis/postgis` image cannot be used directly for a full database and why the test harness skips those changesets.

The image is tagged `derechi/postgis:17-3.5-uk`. A commented line in the Dockerfile points to `imresamu/postgis` for macOS on Apple silicon, where the official image has no arm64 build.
