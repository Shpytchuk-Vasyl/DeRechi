ARG DICT_UK_VERSION=6.8.5
# postgis/postgis is amd64 only; imresamu/postgis:17-3.5 is the same image for arm64 (Apple silicon, Oracle Ampere)
ARG POSTGIS_IMAGE=postgis/postgis:17-3.5

FROM alpine:3.22 AS dict
ARG DICT_UK_VERSION
RUN apk add --no-cache curl \
    && curl -fsSL -o /tmp/hunspell-uk.zip \
       "https://github.com/brown-uk/dict_uk/releases/download/v${DICT_UK_VERSION}/hunspell-uk_UA_${DICT_UK_VERSION}.zip" \
    && unzip -j /tmp/hunspell-uk.zip uk_UA.dic uk_UA.aff -d /dict \
    && mv /dict/uk_UA.dic /dict/uk_ua.dict \
    && mv /dict/uk_UA.aff /dict/uk_ua.affix

FROM ${POSTGIS_IMAGE}
COPY --from=dict /dict/uk_ua.dict /dict/uk_ua.affix /usr/share/postgresql/17/tsearch_data/

COPY ukrainian.stop /usr/share/postgresql/17/tsearch_data/
COPY initdb/ /docker-entrypoint-initdb.d/
RUN test "$(pg_config --sharedir)" = /usr/share/postgresql/17
