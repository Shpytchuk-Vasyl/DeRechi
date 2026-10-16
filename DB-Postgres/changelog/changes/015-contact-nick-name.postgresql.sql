-- liquibase formatted sql

-- changeset vasil:015-contact-nick-name-1
ALTER TABLE contact_info ADD nick_name VARCHAR(50);
ALTER TABLE contact_info ADD profile_url VARCHAR(255);
ALTER TABLE contact_info ALTER COLUMN phone DROP NOT NULL;
ALTER TABLE contact_info ADD CONSTRAINT contact_info_reachable_ck
    CHECK (phone IS NOT NULL OR (nick_name IS NOT NULL AND coalesce(cardinality(social_medias), 0) = 1));
-- rollback ALTER TABLE contact_info DROP CONSTRAINT contact_info_reachable_ck;
-- rollback ALTER TABLE contact_info ALTER COLUMN phone SET NOT NULL;
-- rollback ALTER TABLE contact_info DROP COLUMN profile_url;
-- rollback ALTER TABLE contact_info DROP COLUMN nick_name;
