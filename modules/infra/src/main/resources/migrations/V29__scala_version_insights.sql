CREATE TABLE scala_version_insights (
  kind VARCHAR NOT NULL,
  language_version VARCHAR NOT NULL,
  project_count INTEGER NOT NULL,
  PRIMARY KEY (kind, language_version)
);
