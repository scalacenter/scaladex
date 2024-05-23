ALTER TABLE project_settings RENAME COLUMN default_stable_version TO prefer_stable_version;
ALTER TABLE project_settings RENAME COLUMN artifact_deprecations TO deprecated_artifacts;

ALTER TABLE project_settings
  DROP COLUMN strict_versions,
  DROP COLUMN deprecated,
  DROP COLUMN primary_topic,
  DROP COLUMN beginner_issues_label,
  ADD chatroom VARCHAR(2083);

ALTER TABLE github_info DROP COLUMN chatroom;
