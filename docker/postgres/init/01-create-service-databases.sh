#!/bin/sh
# Runs once, on first start of an empty Postgres volume.
# Creates a database and an owning login for each service so neither can read the other's data.
set -eu

create_service_database() {
  db_name="$1"
  db_user="$2"
  db_password="$3"

  psql -v ON_ERROR_STOP=1 \
       --username "$POSTGRES_USER" --dbname postgres \
       -v db_name="$db_name" -v db_user="$db_user" -v db_password="$db_password" <<'SQL'
CREATE ROLE :"db_user" LOGIN PASSWORD :'db_password';
CREATE DATABASE :"db_name" OWNER :"db_user";
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
SQL
}

create_service_database "$CLAIMS_DB_NAME" "$CLAIMS_DB_USER" "$CLAIMS_DB_PASSWORD"
create_service_database "$NOTIFICATION_DB_NAME" "$NOTIFICATION_DB_USER" "$NOTIFICATION_DB_PASSWORD"
