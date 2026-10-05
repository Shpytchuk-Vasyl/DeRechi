# syntax=docker/dockerfile:1
# Liquibase 5 ships without JDBC drivers. Same versions as the project (liquibase.version,
# postgresql.version from Spring Boot).
FROM liquibase/liquibase:5.0.4
ADD --chmod=644 \
    --checksum=sha256:6e0e4cc2d8cae902084f8a2b18728b073a6fd9d1f87c9d8bff8f298c18185b93 \
    https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar \
    /liquibase/lib/
