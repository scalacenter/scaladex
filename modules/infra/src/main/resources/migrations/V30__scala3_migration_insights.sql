CREATE TABLE scala3_migration_insights (
  migrated BOOLEAN NOT NULL,
  project_count INTEGER NOT NULL,
  PRIMARY KEY (migrated)
);
