--liquibase formatted sql

--changeset vasil:001-enable-postgis
CREATE EXTENSION IF NOT EXISTS postgis;
