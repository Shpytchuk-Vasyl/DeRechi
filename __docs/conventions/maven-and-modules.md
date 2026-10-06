# Maven and modules

One reactor, one wrapper, versions declared once. The full picture of the build is in [maven-reactor](../architecture/maven-reactor.md); this page is the checklist a module must pass before it is committed. To add a module from scratch follow [add-a-module](../extending/add-a-module.md).

## Names must match letter for letter

The module folder, its `<artifactId>` and the `<module>` entry in the root `pom.xml` are the same string, case included: `Admin-API`, `Client-API`, `DB-Postgres`, `Worker`.

Git Bash and NTFS on Windows are case-insensitive, so `admin-api` and `Admin-API` look like the same folder locally. The Docker build runs on Linux and `COPY Admin-API Admin-API` will not find `admin-api`. Also, the `ARG MODULE` in the `Dockerfile` becomes the jar path `/workspace/${MODULE}/target/*.jar`, so compose and Maven must agree on the spelling.

## A module POM inherits, it does not redeclare

The root is `packaging: pom` with `spring-boot-starter-parent:4.1.1` as parent. A module POM has:

```xml
<parent>
    <groupId>org.shpytchuk</groupId>
    <artifactId>DeRechi</artifactId>
    <version>1.0-SNAPSHOT</version>
</parent>
<artifactId>Admin-API</artifactId>
```

and **not**:

- its own `<groupId>` or `<version>` (inherited: `org.shpytchuk`, `1.0-SNAPSHOT`);
- `<properties>` with `java.version` (the root sets `<release>26</release>` in `pluginManagement`);
- a `<version>` on any `<dependency>`.

Versions live in the root, either as a BOM import or a managed version in `<dependencyManagement>` (Spring Cloud, Spring Cloud AWS, Testcontainers, springdoc, NotifyHub, graphql-extended-scalars, language-detector, Liquibase, ShedLock via `shedlock.version`). If a new library is needed, add its version to the root first, then reference it without a version in the module.

Two things are not module dependencies and therefore do not go through `<dependencyManagement>`, but still take their version from root `<properties>`: `annotationProcessorPaths` for Lombok in the module's `maven-compiler-plugin`, and the extra `<dependencies>` of `liquibase-maven-plugin` in `DB-Postgres` (`liquibase-hibernate7`, `spring-orm`, `hibernate-spatial`, ...).

## Shared dependencies come from the root

`spring-boot-starter-actuator`, `micrometer-registry-prometheus` and `spring-boot-starter-actuator-test` are declared in the root `<dependencies>` and reach every module. Do not repeat them. If a dependency is needed by every module, that is where it goes; otherwise it stays in the module.

## Spring Boot 4 starter names

Boot 4 renamed several starters and introduced a `-test` starter per starter. Use the new names:

| Old | New |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-oauth2-client` | `spring-boot-starter-security-oauth2-client` |
| `liquibase-core` | `spring-boot-starter-liquibase` |
| `spring-boot-starter-test` only | plus `spring-boot-starter-webmvc-test`, `-data-jpa-test`, `-amqp-test`, `-graphql-test`, `-thymeleaf-test`, `-validation-test`, `-security-oauth2-client-test`, `-aspectj-test` |

Slice annotations moved with them (`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, `org.springframework.boot.graphql.test.autoconfigure.tester.AutoConfigureGraphQlTester`). Without the matching `-test` starter a slice test does not compile.

## Compiler flags

The root `pluginManagement` configures `maven-compiler-plugin` with `<release>26</release>` and `<parameters>true</parameters>`. A module that declares the plugin (to add Lombok to `annotationProcessorPaths`) inherits that configuration; do not reset it. Without `-parameters`:

- Jackson cannot map JSON to record components,
- `@Argument` in GraphQL controllers cannot resolve argument names,
- constructor binding of `@ConfigurationProperties` records fails.

## One wrapper, no Initializr leftovers

`mvnw`, `mvnw.cmd` and `.mvn/` exist once, in the repository root. When a module is generated with Spring Initializr, delete from it before the first commit:

```
mvnw  mvnw.cmd  .mvn/  HELP.md  .gitignore  .gitattributes
```

A nested `.gitignore` would silently change what is tracked under that module, and a nested wrapper drifts from the root one.

## Packaging

Every service module declares `spring-boot-maven-plugin` in its `<build>`; under `spring-boot-starter-parent` that alone binds `repackage`, so `./mvnw package` produces the fat jar the `Dockerfile` copies. `DB-Postgres` does **not**: it is a library that nobody depends on, and `repackage` would hide its classes under `BOOT-INF/classes/`. `spring-boot:run` still works there because the plugin is configured in root `pluginManagement`.

`Launcher` depends on the other service modules and is the only module allowed to. It is currently commented out in the root `<modules>` and absent from the `Dockerfile` on purpose; see [launcher](../architecture/modules/launcher.md).

## Build commands

```bash
./mvnw test-compile                   # compile everything, including tests
./mvnw -pl Client-API test            # one module
./mvnw -pl Admin-API spring-boot:run  # run one module
./mvnw -B -DskipTests package         # what the Dockerfile runs
```

Run Maven from the repository root. `-pl` is what selects a module; `cd`-ing into a module and using the root wrapper by relative path works but breaks the Liquibase plugin, whose `diffChangeLogFile` is resolved from the reactor root.
