-- Add indexes to improve query performance and reduce connection pool pressure

-- Index for is_latest_version queries (used on every page header)
CREATE INDEX IF NOT EXISTS artifact_latest_version_idx
    ON artifacts (organization, repository)
    WHERE is_latest_version = true;

-- Index for Maven coordinate lookups (group_id, artifact_id)
CREATE INDEX IF NOT EXISTS artifact_maven_coords_idx
    ON artifacts (group_id, artifact_id);

-- Index for dependency lookups by source
CREATE INDEX IF NOT EXISTS artifact_dep_source_idx
    ON artifact_dependencies (source_group_id, source_artifact_id, source_version);

-- Index for reverse dependency lookups by target
CREATE INDEX IF NOT EXISTS artifact_dep_target_idx
    ON artifact_dependencies (target_group_id, target_artifact_id, target_version);
