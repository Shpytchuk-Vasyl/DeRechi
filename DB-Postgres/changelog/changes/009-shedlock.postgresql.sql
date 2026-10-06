-- liquibase formatted sql

-- changeset vasil:009-shedlock-1
-- ShedLock's table for Worker's scheduled jobs: one instance runs a job at a time.
-- Not an entity; liquibase.properties keeps it out of the diff.
CREATE TABLE shedlock
(
    name       VARCHAR(64)              NOT NULL,
    lock_until TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    locked_at  TIMESTAMP(3) WITH TIME ZONE NOT NULL,
    locked_by  VARCHAR(255)             NOT NULL,
    CONSTRAINT shedlock_pk PRIMARY KEY (name)
);
-- rollback DROP TABLE shedlock;
