# Tech stack

Versions are pinned in exactly one place each: the root `pom.xml` for Java libraries,
`docker-compose.yml` for infrastructure images. If a number here disagrees with one of
those files, the file wins and this page needs a fix.

## Runtime

| Component | Version | Notes |
|---|---|---|
| Java | 26 | `<release>26</release>` in the root `maven-compiler-plugin` config |
| Spring Boot | 4.1.1 | root `<parent>` is `spring-boot-starter-parent` |
| Spring Cloud | 2025.1.3 | Eureka client/server, Gateway (WebFlux), Resilience4j |
| Spring Cloud AWS | 4.1.1 | `spring-cloud-aws-starter-s3`, pointed at MinIO |
| PostgreSQL | 17 with PostGIS 3.5 | custom image built from `docker/postgres/Dockerfile` |
| RabbitMQ | 4.3.5 | management plugin on, quorum queues |
| Keycloak | 26.4 | `start-dev --import-realm` |
| MinIO | RELEASE.2025-09-07 | plus an `mc` init container that creates the bucket |
| Mailpit | 1.28 | SMTP sink with a web UI, development only |
| Prometheus | v3.14.0 | |
| Grafana | 13.2.1 | |

### Spring Boot 4 specifics

Boot 4 renamed the starters, and the project uses the new names throughout:
`spring-boot-starter-webmvc` (not `-web`), `spring-boot-starter-security-oauth2-client`,
`spring-boot-starter-liquibase`. Test support is split per starter too, so a module that uses
`spring-boot-starter-amqp` adds `spring-boot-starter-amqp-test` in `test` scope rather than a
single `spring-boot-starter-test`. The root pom adds `spring-boot-starter-actuator-test` to
every module.

Jackson is the `tools.jackson` 3.x line. The RabbitMQ converters are
`JacksonJsonMessageConverter` and `DefaultJacksonJavaTypeMapper` from Spring AMQP, built on a
`tools.jackson.databind.json.JsonMapper`.

### PostgreSQL image

The compose file builds `derechi/postgis:17-3.5-uk` from `postgis/postgis:17-3.5`. The
Dockerfile downloads the Ukrainian hunspell dictionary from `brown-uk/dict_uk`, copies it and
`ukrainian.stop` into `tsearch_data`, and drops `initdb/01-keycloak.sql` into the init
directory so the `keycloak` database exists on first start. Migration 001 then creates the
`ukrainian` text search configuration on top of that dictionary. Tests use the stock
`postgis/postgis:17-3.5` image and skip the Ukrainian configuration, see
[../conventions/testing.md](../conventions/testing.md).

One note on versions: `.claude/CLAUDE.md` mentions PostgreSQL 18.6, and the `Europe/Kiev`
timezone quirk in `DB-Postgres/README.md` is a PostgreSQL 18 behaviour. The image compose
actually builds today is 17. Treat 17 as the truth until the Dockerfile changes.

## Threading model

Virtual threads are on (`spring.threads.virtual.enabled: true`) in every blocking service:
`Client-API`, `Admin-API`, `Worker`, `Notification`. That is why the code is plain
blocking Spring MVC, JPA and `RestClient`; there is no WebFlux and no `RestTemplate` in the
services. The one reactive module is `Getaway`, because Spring Cloud Gateway's server is
WebFlux-based.

## Libraries

| Library | Where | Why |
|---|---|---|
| Spring Data JPA + Hibernate 7 | all DB services | entities, specifications, repositories |
| `hibernate-spatial` + JTS | `Client-API`, `Admin-API`, `Worker`, `DB-Postgres` | `geography(Point,4326)` on `place.coordinate`, `distanceWithin` predicates |
| Liquibase 5.0.4 + `liquibase-hibernate7` | `DB-Postgres` | migrations, and diffing them from the entities |
| Spring for GraphQL + `graphql-java-extended-scalars` 24.0 | `Client-API` | the public API; `Date` scalar |
| Spring Security OAuth2 client | `Admin-API` | OIDC login against Keycloak |
| Thymeleaf, Bulma (CDN), htmx (CDN), Font Awesome | `Admin-API` | server-rendered admin UI with partial updates |
| Caffeine | `Admin-API` | the `categories` cache |
| libphonenumber | `Admin-API`, `Worker` | phone formatting in `Formats`; the recipient's language for claim messages (`PhoneLocales`) |
| optimaize `language-detector` 0.6 | `Worker` | picks the PostgreSQL text search configuration from the title |
| NotifyHub 1.1.0 (`notify-spring-boot-starter`, `notify-email`, `notify-telegram` managed) | `Notification` | one API over email / SMS / messengers |
| Lombok | `Admin-API`, `Client-API`, `Worker` | entities and forms only; see [../conventions/java-code-style.md](../conventions/java-code-style.md) |
| Testcontainers (PostgreSQL module) | `Client-API`, `Admin-API` | integration tests against real PostGIS |
| Micrometer Prometheus registry | all | `/actuator/prometheus` |

`springdoc-openapi-starter-webmvc-ui` is version-managed in the root pom but not used by any
module: there is no REST API to document. It is there in case one appears.

## Not a Maven module

`Web-Client` is a Next.js application built with pnpm. It is a sibling of the reactor, not a
part of it. See [modules/web-client.md](modules/web-client.md).
