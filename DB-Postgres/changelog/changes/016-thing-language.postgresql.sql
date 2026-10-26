-- liquibase formatted sql

-- changeset vasil:016-thing-language-1
ALTER TABLE lost_item ADD language VARCHAR(2);
ALTER TABLE found_item ADD language VARCHAR(2);
ALTER TABLE lost_item_history ADD language VARCHAR(2);
ALTER TABLE found_item_history ADD language VARCHAR(2);
UPDATE lost_item SET language = 'uk';
UPDATE found_item SET language = 'uk';
UPDATE lost_item_history SET language = 'uk';
UPDATE found_item_history SET language = 'uk';
-- rollback ALTER TABLE found_item_history DROP COLUMN language;
-- rollback ALTER TABLE lost_item_history DROP COLUMN language;
-- rollback ALTER TABLE found_item DROP COLUMN language;
-- rollback ALTER TABLE lost_item DROP COLUMN language;
