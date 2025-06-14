\set ON_ERROR_STOP on

CREATE USER transfer_user WITH PASSWORD 'your_transfer_password';
CREATE DATABASE transfer_db;
GRANT ALL PRIVILEGES ON DATABASE transfer_db TO transfer_user;

CREATE USER account_user WITH PASSWORD 'your_account_password';
CREATE DATABASE account_db;
GRANT ALL PRIVILEGES ON DATABASE account_db TO account_user;

CREATE USER camunda_user WITH PASSWORD 'your_camunda_password';
CREATE DATABASE camunda_db;
GRANT ALL PRIVILEGES ON DATABASE camunda_db TO camunda_user;

\connect transfer_db;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO transfer_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO transfer_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO transfer_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO transfer_user;

\connect account_db;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO account_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO account_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO account_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO account_user;

\connect camunda_db;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO camunda_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO camunda_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO camunda_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO camunda_user;