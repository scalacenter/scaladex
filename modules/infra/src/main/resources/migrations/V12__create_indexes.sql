CREATE INDEX artifact_index
    ON artifacts (organization, repository, artifact_name, version);