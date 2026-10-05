#!/bin/sh
# Runs once, on the first start of an empty volume: the database Keycloak keeps its data in.
# POSTGRES_USER is the one the postgres image itself was started with.
#
# On an existing volume, by hand:
#   docker exec derechi-postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE keycloak OWNER \"$POSTGRES_USER\";"'
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -c "CREATE DATABASE keycloak OWNER \"$POSTGRES_USER\";"
