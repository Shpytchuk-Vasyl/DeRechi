ARG DICT_UK_VERSION=6.8.5
# LibreOffice/dictionaries commit with pl_PL from sjp.pl 20260511
ARG DICT_PL_COMMIT=c78220a23be17268eb08040fdeb5f817776d520c
# postgis/postgis is amd64 only; imresamu/postgis:17-3.5 is the same image for arm64 (Apple silicon, Oracle Ampere)
ARG POSTGIS_IMAGE=postgis/postgis:17-3.5

FROM alpine:3.22 AS dict
ARG DICT_UK_VERSION
ARG DICT_PL_COMMIT
RUN apk add --no-cache curl \
    && curl -fsSL -o /tmp/hunspell-uk.zip \
       "https://github.com/brown-uk/dict_uk/releases/download/v${DICT_UK_VERSION}/hunspell-uk_UA_${DICT_UK_VERSION}.zip" \
    && unzip -j /tmp/hunspell-uk.zip uk_UA.dic uk_UA.aff -d /dict \
    && mv /dict/uk_UA.dic /dict/uk_ua.dict \
    && mv /dict/uk_UA.aff /dict/uk_ua.affix
# pl_PL ships in ISO-8859-2, PostgreSQL reads dictionaries only in UTF-8
RUN for ext in dic aff; do \
        curl -fsSL -o "/tmp/pl_PL.${ext}" \
            "https://raw.githubusercontent.com/LibreOffice/dictionaries/${DICT_PL_COMMIT}/pl_PL/pl_PL.${ext}"; \
    done \
    && iconv -f ISO-8859-2 -t UTF-8 /tmp/pl_PL.dic > /dict/pl_pl.dict \
    && iconv -f ISO-8859-2 -t UTF-8 /tmp/pl_PL.aff | sed 's/^SET ISO8859-2$/SET UTF-8/' > /dict/pl_pl.affix \
    && grep -q '^SET UTF-8$' /dict/pl_pl.affix

FROM ${POSTGIS_IMAGE}
COPY --from=dict /dict/uk_ua.dict /dict/uk_ua.affix /dict/pl_pl.dict /dict/pl_pl.affix /usr/share/postgresql/17/tsearch_data/

COPY ukrainian.stop polish.stop /usr/share/postgresql/17/tsearch_data/
COPY initdb/ /docker-entrypoint-initdb.d/
RUN test "$(pg_config --sharedir)" = /usr/share/postgresql/17
