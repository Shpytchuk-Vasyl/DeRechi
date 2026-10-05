-- liquibase formatted sql

-- changeset vasil:008-claim-unlock-limit-1 splitStatements:false
ALTER TABLE lost_item_claim
    ADD COLUMN payment_requested_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE found_item_claim
    ADD COLUMN payment_requested_at TIMESTAMP(6) WITH TIME ZONE;

UPDATE lost_item_claim SET payment_requested_at = created_at WHERE payment_product_id IS NOT NULL;
UPDATE found_item_claim SET payment_requested_at = created_at WHERE payment_product_id IS NOT NULL;

-- changeset vasil:008-claim-unlock-limit-2 splitStatements:false
CREATE INDEX lost_item_claim_payment_requested_idx ON lost_item_claim (payment_requested_at)
    WHERE payment_requested_at IS NOT NULL;
CREATE INDEX found_item_claim_payment_requested_idx ON found_item_claim (payment_requested_at)
    WHERE payment_requested_at IS NOT NULL;
