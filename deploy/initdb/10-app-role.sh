# Task 31: runs once, when the data folder is new, as the image's superuser
# (docker-entrypoint-initdb.d; sourced, so not executable). The application
# gets a role of its own, owner of its own database but no superuser, and
# Flyway creates everything else as that role: unaccent and pg_trgm are
# trusted extensions, which a database owner may create.
#
# The superuser is then left without a password, so no other container can
# log in as it. Inside this container the local socket and 127.0.0.1 are
# trusted (initdb's default), so a shell here (docker compose exec -u
# postgres) still reaches it, for maintenance.
: "${DB_NAME:?}" "${DB_USER:?}" "${DB_PASSWORD:?}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv app_db DB_NAME
\getenv app_user DB_USER
\getenv app_password DB_PASSWORD
\getenv superuser POSTGRES_USER
CREATE ROLE :"app_user" LOGIN PASSWORD :'app_password';
CREATE DATABASE :"app_db" OWNER :"app_user";
ALTER ROLE :"superuser" PASSWORD NULL;
SQL
