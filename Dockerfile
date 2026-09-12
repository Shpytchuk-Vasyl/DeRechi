# syntax=docker/dockerfile:1

# ---- build: один раз на весь реактор, шар кешується між усіма сервісами ----
FROM eclipse-temurin:26-jdk AS build
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY DB-Postgres DB-Postgres
COPY Discovery Discovery
COPY Auth Auth
COPY Getaway Getaway
COPY Client-API Client-API
COPY Admin-API Admin-API
COPY Automatic-Search Automatic-Search

RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

# ---- runtime: тонкий шар, свій на кожен сервіс ----
FROM eclipse-temurin:26-jre
ARG MODULE
WORKDIR /app

RUN useradd --system --uid 1001 spring
USER spring

COPY --from=build /workspace/${MODULE}/target/*.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
