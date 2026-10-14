-- liquibase formatted sql

-- changeset vasil:013-ingest-post-item-idx-1
CREATE INDEX ingest_post_item_idx ON ingest_post (item_kind, item_id);
-- rollback DROP INDEX ingest_post_item_idx;
