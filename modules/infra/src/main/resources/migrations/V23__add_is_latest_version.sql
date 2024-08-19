ALTER TABLE artifacts
ADD COLUMN IF NOT EXISTS is_latest_version BOOLEAN NOT NULL default 'false';
