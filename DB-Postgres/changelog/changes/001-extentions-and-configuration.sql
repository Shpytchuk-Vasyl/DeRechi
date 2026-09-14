--liquibase formatted sql

--changeset vasil:001-enable-postgis
CREATE EXTENSION IF NOT EXISTS postgis;

--changeset vasil:001-ukrainian-fts
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM pg_ts_config WHERE cfgname = 'ukrainian'
-- Require dictionary uk_ua.dict/uk_ua.affix and ukrainian.stop у tsearch_data,
-- they come from docker/postgres/Dockerfile.
CREATE TEXT SEARCH DICTIONARY ukrainian_hunspell (
    TEMPLATE  = ispell,
    DictFile  = uk_ua,
    AffFile   = uk_ua,
    StopWords = ukrainian
);

CREATE TEXT SEARCH CONFIGURATION ukrainian (COPY = simple);

ALTER TEXT SEARCH CONFIGURATION ukrainian
    ALTER MAPPING FOR word, hword, hword_part
    WITH ukrainian_hunspell, simple;
