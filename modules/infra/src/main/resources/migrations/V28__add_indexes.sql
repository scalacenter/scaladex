CREATE INDEX IF NOT EXISTS artifact_latest_version_index
  ON artifacts (organization, repository)
  WHERE is_latest_version;

-- the following indexes were created manually on prod; reconciled here so fresh databases match
CREATE INDEX IF NOT EXISTS "artifact-dependencies-index"
  ON artifact_dependencies (target_group_id, target_artifact_id, target_version);

CREATE INDEX IF NOT EXISTS artifact_index_4
  ON artifacts (organization, repository, group_id, artifact_id, release_date);
