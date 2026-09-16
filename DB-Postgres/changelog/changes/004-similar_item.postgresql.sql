-- liquibase formatted sql

-- changeset vasil:1789398081101-1 splitStatements:false
CREATE TABLE similar_item
(
    found_item_id BIGINT           NOT NULL,
    lost_item_id  BIGINT           NOT NULL,
    match_order   DOUBLE PRECISION NOT NULL,
    notified_at   TIMESTAMP(6) WITH TIME ZONE,
    notified_by   VARCHAR(100),

    CONSTRAINT "similar_itemPK" PRIMARY KEY (found_item_id, lost_item_id)
);

-- changeset vasil:1789398081101-2 splitStatements:false
ALTER TABLE similar_item
    ADD CONSTRAINT "FK18kanuyefq0emoqxhn2pocfc7" FOREIGN KEY (lost_item_id) REFERENCES lost_item (id);

-- changeset vasil:1789398081101-3 splitStatements:false
ALTER TABLE similar_item
    ADD CONSTRAINT "FKi7nq8pfbxq1vy93mfrcu14v95" FOREIGN KEY (found_item_id) REFERENCES found_item (id);
