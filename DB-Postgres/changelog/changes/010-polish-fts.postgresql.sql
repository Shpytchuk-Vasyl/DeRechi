-- liquibase formatted sql

-- changeset vasil:010-polish-fts
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM pg_ts_config WHERE cfgname = 'polish'
-- Require dictionary pl_pl.dict/pl_pl.affix and polish.stop in tsearch_data,
-- they come from docker/postgres/Dockerfile.
CREATE TEXT SEARCH DICTIONARY polish_hunspell (
    TEMPLATE  = ispell,
    DictFile  = pl_pl,
    AffFile   = pl_pl,
    StopWords = polish
);

CREATE TEXT SEARCH CONFIGURATION polish (COPY = simple);

ALTER TEXT SEARCH CONFIGURATION polish
    ALTER MAPPING FOR word, hword, hword_part
    WITH polish_hunspell, simple;
