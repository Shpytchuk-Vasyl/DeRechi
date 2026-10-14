# syntax=docker/dockerfile:1

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
WORKDIR /app

RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-ukr \
 && rm -rf /var/lib/apt/lists/*
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/5/tessdata

RUN useradd --system --uid 1001 spring \
 && mkdir /app/tdlib \
 && chown spring /app/tdlib
USER spring

COPY --from=build /workspace/layers/Ingest/dependencies/ ./
COPY --from=build /workspace/layers/Ingest/spring-boot-loader/ ./
COPY --from=build /workspace/layers/Ingest/snapshot-dependencies/ ./
COPY --from=build /workspace/layers/Ingest/application/ ./

ENV JAVA_OPTS="--enable-native-access=ALL-UNNAMED"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]

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
