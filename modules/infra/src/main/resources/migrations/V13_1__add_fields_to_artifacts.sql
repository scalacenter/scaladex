ALTER TABLE artifacts
ADD COLUMN is_semantic BOOLEAN NOT NULL default 'true', -- for migration purposes
ADD COLUMN is_prerelease BOOLEAN NOT NULL default 'false';