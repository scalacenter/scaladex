CREATE TABLE projects (
  organization VARCHAR(39)  NOT NULL,
  repository   VARCHAR(100)  NOT NULL,
  PRIMARY KEY (organization, repository)
);
