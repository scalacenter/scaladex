ALTER TABLE artifacts
ADD COLUMN is_latest_version BOOLEAN NOT NULL default 'false';
