# syntax=docker/dockerfile:1

FROM eclipse-temurin:26-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY --chmod=755 mvnw ./
COPY pom.xml ./
COPY DB-Postgres DB-Postgres
COPY Getaway Getaway
COPY Client-API Client-API
COPY Admin-API Admin-API
COPY Worker Worker
COPY Notification Notification

RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -B --strict-checksums -DskipTests package

FROM eclipse-temurin:26-jre
ARG MODULE
WORKDIR /app

RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /workspace/${MODULE}/target/*.jar app.jar

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
