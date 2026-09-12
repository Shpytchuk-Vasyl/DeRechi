--liquibase formatted sql

--changeset vasil:003-add-base-categories
INSERT INTO  thing_category(key)
values ('DOCUMENTS'), ('WALLET'), ('MACHINERY'), ('JEWELRY'), ('ANIMALS'), ('OTHER');
