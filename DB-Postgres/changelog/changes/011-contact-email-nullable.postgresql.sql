-- liquibase formatted sql

-- changeset vasil:011-contact-email-nullable
ALTER TABLE contact_info
    ALTER COLUMN email DROP NOT NULL;
