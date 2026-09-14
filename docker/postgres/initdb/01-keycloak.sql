-- Keycloak веде власну схему сам і до DB-Postgres/Liquibase не має стосунку,
-- тому живе в окремій базі того самого сервера.
CREATE DATABASE keycloak OWNER derechi;
