# Add a module

A new Spring Boot service in the Maven reactor. Most of the work is wiring, and every forgotten line fails somewhere else (the Docker build, Prometheus, the Gateway), so go through the list in order.

## Before you start

Read [Maven reactor](../architecture/maven-reactor.md), [Maven and modules](../conventions/maven-and-modules.md) and [Docker image](../deployment/docker-image.md). Pick a name in the same style as the others: `Pascal-Case-With-Dashes`. The example adds `Report-Export` on port 8086 with Spring application name `Report-Export`.

## Steps

1. **Folder and POM.** The folder name, the `artifactId` and the `<module>` entry must match **letter for letter**. Git Bash on Windows hides case differences; the Docker build on Linux does not. The module POM has the root as parent, no `<groupId>`, no `<version>`, no `<properties>` with `java.version`, and no versions on dependencies (they come from the root `<dependencyManagement>`):

   ```xml
   <parent>
       <groupId>org.shpytchuk</groupId>
       <artifactId>DeRechi</artifactId>
       <version>1.0-SNAPSHOT</version>
   </parent>
   <artifactId>Report-Export</artifactId>
   ```

   Use the Spring Boot 4 starter names (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-amqp`, the `-test` twin of each starter you use). Actuator and the Prometheus registry are inherited from the root `<dependencies>`. If you use Lombok, copy the `provided` + `optional` dependency and the `annotationProcessorPaths` block of `maven-compiler-plugin` from `Admin-API/pom.xml`.

2. **Register it.** Root `pom.xml`, `<modules>`: add `<module>Report-Export</module>`. Keep `DB-Postgres` first; the list is also the build order hint.

3. **Delete Initializr leftovers** if you generated the skeleton: `mvnw`, `mvnw.cmd`, `.mvn/`, `HELP.md`, `.gitignore`, `.gitattributes` inside the module. One wrapper lives in the root.

4. **`application.yaml`.** Minimum set, copied from a sibling:

   ```yaml
   spring:
     application:
       name: Report-Export
     threads:
       virtual:
         enabled: true
   server:
     port: 8086
   management:
     server:
       port: ${MANAGEMENT_PORT:9086}
     endpoints:
       web:
         exposure:
           include: health,info,prometheus
     endpoint:
       health:
         show-details: never
   ```

   Actuator runs on its own management port, the service port + 1000. Keep `localhost` here; container addresses come from environment variables in compose. Blocking code with virtual threads and `RestClient` is the house style, not WebFlux.

5. **Database, if any.** Copy the entities you need into `org.shpytchuk.reportexport.entity` (they are copied per module on purpose, no module depends on `DB-Postgres`), set `spring.jpa.hibernate.ddl-auto: none`, do not add Liquibase; the schema is owned by `DB-Postgres`. Add the datasource block and, if you touch `place.coordinate`, the `hibernate-spatial` dependency.

6. **Events, if any.** Copy `event/EventType.java`, `event/EventTypeScanner.java` and `config/RabbitConfig.java` from `Worker`, change the package, and follow [Add an event](add-an-event.md).

7. **Dockerfile.** Root `Dockerfile`, build stage: add `COPY Report-Export Report-Export` next to the other modules. Without it Maven sees the module in `<modules>` but the directory is missing and the image build fails for **every** service, not just the new one.

8. **Compose.** `docker-compose.services.yml`: a service block using the `service-base` anchor, `build.args.MODULE: Report-Export`, the port mapping, `container_name: derechi-report-export`, and the environment anchors it needs (`*postgres`, `*rabbitmq`, `*jvm`). No `healthcheck`: nothing waits for a service, and Prometheus's `TargetDown` notices one that stops answering. The management port is not published. Use `depends_on` with health conditions for postgres and rabbitmq like the siblings.

9. **Prometheus.** Both files, both with the `application` label the Grafana dashboard keys on, both on the management port:

   - `docker/prometheus/prometheus.yml`: `host.docker.internal:9086`, `labels: { application: Report-Export }`
   - `docker/prometheus/prometheus-full.yml`: `report-export:9086` (the compose service name), same label

10. **Gateway, if it serves HTTP to the outside.** `Getaway/src/main/resources/application.yaml`: a route with `uri: ${REPORT_EXPORT_URI:http://localhost:8086}` and a `Path=` predicate; set `REPORT_EXPORT_URI: http://report-export:8086` for `getaway` in compose and add it to the env files and [environment-variables](../deployment/environment-variables.md).

11. **Context test.** `src/test/java/.../ReportExportApplicationTests.java` with `@SpringBootTest` and an empty `contextLoads()`; if the module needs the database, extend it with the Testcontainers base from `Client-API` (`AbstractPostgresTests`) rather than pointing tests at a local server.

12. **Launcher.** `Launcher` boots several services in one JVM for local development. It is currently commented out of the root `<modules>`; if you revive it, add the new `*Application` there too, and never add `Launcher` to the `Dockerfile`.

13. **Docs.** Add `..` (what it does, port, queues, config keys) and a row to the module table in [Modules](../architecture/modules/README.md).

## Tests to add or update

The context test above is the floor. Whatever the module does deserves its own tests following [Testing conventions](../conventions/testing.md).

## Migration needed?

Only if the module introduces tables, and then the migration lives in `DB-Postgres` with the entity added there first.

## Web-Client impact

Only if the module exposes something through the Gateway that the web client should call. In that case the route and the contract are the deliverables for the web team.

## Checklist

- [ ] Folder, `artifactId` and `<module>` identical, no groupId/version/properties in the module POM
- [ ] Initializr leftovers deleted
- [ ] `application.yaml` with name, virtual threads, port, management port (port + 1000), actuator exposure, `show-details: never`
- [ ] `ddl-auto: none`, no Liquibase, entity copies if it uses the database
- [ ] `COPY` line in `Dockerfile`
- [ ] Service block in `docker-compose.services.yml`, management port not published
- [ ] Target in both Prometheus configs on the management port, with the `application` label
- [ ] Gateway route with its own `*_URI` variable if it serves HTTP
- [ ] `*ApplicationTests` passes
- [ ] `./mvnw test-compile` and `docker compose -f docker-compose.yml -f docker-compose.services.yml build` succeed
- [ ] Module page under `..`
