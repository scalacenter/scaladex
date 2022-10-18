DROP TABLE project_dependencies;

CREATE TABLE project_dependencies (
  source_organization  VARCHAR(39)  NOT NULL,
  source_repository   VARCHAR(100) NOT NULL,
  source_version VARCHAR NOT NULL,
  target_organization VARCHAR(39)  NOT NULL,
  target_repository   VARCHAR(100) NOT NULL,
  target_version VARCHAR NOT NULL,
  scope VARCHAR,
  PRIMARY KEY (source_organization, source_repository, source_version, target_organization, target_repository, target_version, scope)
);
