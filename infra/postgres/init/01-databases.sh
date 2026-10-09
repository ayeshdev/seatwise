#!/bin/sh
# Runs once, on an empty data volume. The app database (POSTGRES_DB) is created
# by the image itself; Keycloak gets its own role and database so identity data
# never shares a schema with application tables.
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE ROLE keycloak LOGIN PASSWORD '${KEYCLOAK_DB_PASSWORD}';
	CREATE DATABASE keycloak OWNER keycloak;
EOSQL
