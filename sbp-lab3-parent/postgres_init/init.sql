-- postgres_init/init.sql
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'transfer_user') THEN
        CREATE USER transfer_user WITH PASSWORD 'your_transfer_password';
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'account_user') THEN
        CREATE USER account_user WITH PASSWORD 'your_account_password';
    END IF;
END
$$;

CREATE DATABASE transfer_db;
CREATE DATABASE account_db;

ALTER DATABASE transfer_db OWNER TO transfer_user;
GRANT ALL PRIVILEGES ON DATABASE transfer_db TO transfer_user;

ALTER DATABASE account_db OWNER TO account_user;
GRANT ALL PRIVILEGES ON DATABASE account_db TO account_user;

\c transfer_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO transfer_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO transfer_user;

\c account_db
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO account_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO account_user;