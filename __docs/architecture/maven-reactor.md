# Maven reactor

One root `pom.xml`, `packaging: pom`, with `spring-boot-starter-parent:4.1.1` as its parent.
Every service and the schema library is a child module. The rules below exist so that a
module pom stays a short list of dependencies and nothing else.

## Modules

```xml
<modules>
    <module>DB-Postgres</module>
    <module>Getaway</module>
    <module>Client-API</module>
    <module>Admin-API</module>
    <module>Worker</module>
    <module>Notification</module>
<!--    <module>Launcher</module>-->
</modules>
```

`Launcher` is commented out at the moment. It is the only module that depends on other
service modules, and it is not part of the Docker image; see
[modules/launcher.md](modules/launcher.md) before re-enabling it.

The directory name, the `artifactId` and the `<module>` entry must match character for
character. Git Bash on Windows hides case differences; the Linux build stage in the
Dockerfile does not.

## What the root owns

**Coordinates.** `groupId` is `org.shpytchuk`, `version` is `1.0-SNAPSHOT`. Modules inherit
both and must not redeclare them, nor `java.version` or any other `<properties>` that the
root already sets.

**Versions.** `<dependencyManagement>` imports the Spring Cloud, Spring Cloud AWS and
Testcontainers BOMs and pins the few libraries outside them: `graphql-java-extended-scalars`,
`language-detector`, the NotifyHub artifacts, ShedLock (`shedlock-spring` and
`shedlock-provider-jdbc-template` at `${shedlock.version}`), springdoc. A module `<dependency>` never carries
a `<version>`. The two exceptions are not module dependencies at all: the Lombok entry in
`annotationProcessorPaths` and the Liquibase plugin's extension dependencies. Even those take
their version from a root property.

**Shared dependencies.** Every module gets `spring-boot-starter-actuator`,
`micrometer-registry-prometheus` and, in test scope, `spring-boot-starter-actuator-test`.
That is what makes "every service exposes `/actuator/prometheus`" (on its management port,
see [observability.md](observability.md)) true without each pom saying so.

**Plugins.** `<pluginManagement>` configures three plugins:

- `spring-boot-maven-plugin`, with no executions bound. Modules that are services bind it
  themselves; `DB-Postgres` does not (see below).
- `liquibase-maven-plugin` at `${liquibase.version}`.
- `maven-compiler-plugin` with `<release>${java.version}</release>` and
  `<parameters>true</parameters>`.

`-parameters` is not optional. Without it the compiler drops constructor parameter names,
and three things break at once: Jackson cannot bind records, `@Argument` in GraphQL
controllers cannot match method parameters to query arguments, and constructor binding of
`@ConfigurationProperties` records such as `CountriesProperties` fails.

**Maven wrapper.** There is one `mvnw` / `mvnw.cmd` / `.mvn/` at the root. Modules do not
carry their own wrapper, `HELP.md`, `.gitignore` or `.gitattributes`; Spring Initializr
generates them and we delete them.

## Lombok

`Admin-API`, `Client-API` and `Worker` declare Lombok as `provided` + `optional`
and add it to `annotationProcessorPaths` in their own `maven-compiler-plugin` block, which
otherwise inherits the root configuration. The other modules do not use Lombok at all.
Where it is used is a convention question, covered in
[../conventions/java-code-style.md](../conventions/java-code-style.md).

## DB-Postgres is a thin jar

`DB-Postgres` does not bind `spring-boot-maven-plugin` to `package`. Nobody depends on it, so
a fat jar would be dead weight, and `repackage` would move the classes under
`BOOT-INF/classes/` where a dependent module could not see them anyway. `spring-boot:run`
still works because the goal is invoked directly and picks up the root plugin management.

It also has an extra `<resource>` that copies `changelog/` (a directory at the module root,
not under `src/`) to `target/classes/changelog`. The Liquibase plugin reads the same files
straight from the module directory through `<searchPath>${project.basedir}</searchPath>`.
Both views resolve the changelog as `changelog/changelog-master.yaml`, which is the point:
see [modules/db-postgres.md](modules/db-postgres.md).

## Useful commands

```bash
./mvnw test-compile                         # compile everything, including tests
./mvnw -pl Client-API spring-boot:run       # run one module
./mvnw -pl Admin-API test                   # test one module
./mvnw -B -DskipTests package               # what the Dockerfile build stage runs
```

Everything runs from the repository root. The Liquibase plugin in particular resolves
`diffChangeLogFile` from the reactor root, which is why that path in
`DB-Postgres/liquibase.properties` starts with `DB-Postgres/`.

See also [../conventions/maven-and-modules.md](../conventions/maven-and-modules.md) for the
checklist when adding a module and [../deployment/docker-image.md](../deployment/docker-image.md)
for how the reactor is built inside the image.
