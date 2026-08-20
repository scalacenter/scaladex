CREATE INDEX IF NOT EXISTS artifact_latest_version_index
  ON artifacts (organization, repository)
  WHERE is_latest_version;
