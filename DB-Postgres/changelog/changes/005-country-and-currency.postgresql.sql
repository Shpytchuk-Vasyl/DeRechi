-- liquibase formatted sql

-- country (ISO 3166-1 alpha-2), currency (ISO 4217).

-- changeset vasil:005-country-and-currency-1 splitStatements:false
ALTER TABLE place
    ADD COLUMN country_code VARCHAR(2);

UPDATE place
SET country_code = 'UA'
WHERE country_code IS NULL;

ALTER TABLE place
    ALTER COLUMN country_code SET NOT NULL;

-- changeset vasil:005-country-and-currency-2 splitStatements:false
ALTER TABLE lost_item
    ADD COLUMN currency VARCHAR(3);
ALTER TABLE found_item
    ADD COLUMN currency VARCHAR(3);
ALTER TABLE lost_item_history
    ADD COLUMN currency VARCHAR(3);
ALTER TABLE found_item_history
    ADD COLUMN currency VARCHAR(3);

UPDATE lost_item
SET currency = 'UAH'
WHERE currency IS NULL;
UPDATE found_item
SET currency = 'UAH'
WHERE currency IS NULL;
UPDATE lost_item_history
SET currency = 'UAH'
WHERE currency IS NULL;
UPDATE found_item_history
SET currency = 'UAH'
WHERE currency IS NULL;

ALTER TABLE lost_item
    ALTER COLUMN currency SET NOT NULL;
ALTER TABLE found_item
    ALTER COLUMN currency SET NOT NULL;
ALTER TABLE lost_item_history
    ALTER COLUMN currency SET NOT NULL;
ALTER TABLE found_item_history
    ALTER COLUMN currency SET NOT NULL;
