-- liquibase formatted sql

-- changeset vasil:014-ingest-post-message-id-1
ALTER TABLE ingest_post ADD message_id BIGINT;
-- rollback ALTER TABLE ingest_post DROP COLUMN message_id;
