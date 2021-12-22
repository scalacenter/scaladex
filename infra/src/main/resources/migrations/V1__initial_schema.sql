CREATE TABLE projects (
    organization             VARCHAR(39)  NOT NULL,
    repository               VARCHAR(100) NOT NULL,
    creation_date            TIMESTAMPTZ,
    -- github status can be: Unknown, Ok, Moved, NotFound, Failed
    github_status            VARCHAR(10)  NOT NULL,
    github_update            TIMESTAMPTZ  NOT NULL,
    -- in case of Moved
    new_organization         VARCHAR(39),
    new_repository           VARCHAR(100),
    -- in case of Failed
    error_code               INT,
    error_message            VARCHAR(300),
    PRIMARY KEY (organization, repository)
);

CREATE TABLE github_info (
    organization           VARCHAR(39)  NOT NULL,
    repository             VARCHAR(100) NOT NULL,
    name                   VARCHAR(100) NOT NULL, -- equivalent to repository
    owner                  VARCHAR(39)  NOT NULL, -- equivalent to organization
    homepage               VARCHAR(2083),
    description            VARCHAR,
    logo                   VARCHAR(2083),
    stars                  INT,
    forks                  INT,
    watchers               INT,
    issues                 INT,
    readme                 TEXT,
    contributors           VARCHAR,
    contributorCount       INT,
    commits                INT,
    topics                 VARCHAR(1024) NOT NULL,
    contributingGuide      VARCHAR(2083),
    codeOfConduct          VARCHAR(2083),
    chatroom               VARCHAR(2083),
    beginnerIssues         VARCHAR,
    FOREIGN KEY (organization, repository) REFERENCES projects (organization, repository),
    PRIMARY KEY (organization, repository)
);

CREATE TABLE project_user_data (
   organization         VARCHAR(39)  NOT NULL,
   repository           VARCHAR(100) NOT NULL,
   defaultStableVersion BOOLEAN      NOT NULL,
   defaultArtifact      VARCHAR,
   strictVersions       BOOLEAN      NOT NULL,
   customScalaDoc       VARCHAR,
   documentationLinks   VARCHAR,
   deprecated           BOOLEAN      NOT NULL,
   contributorsWanted   BOOLEAN      NOT NULL,
   artifactDeprecations VARCHAR,
   cliArtifacts         VARCHAR,
   primaryTopic         VARCHAR,
   beginnerIssuesLabel    VARCHAR(1024),
   FOREIGN KEY (organization, repository) REFERENCES projects (organization, repository),
   PRIMARY KEY (organization, repository)
);


CREATE TABLE artifacts (
    group_id            VARCHAR NOT NULL,
    artifact_id         VARCHAR NOT NULL,
    version             VARCHAR NOT NULL,
    artifact_name       VARCHAR NOT NULL,
    platform            VARCHAR NOT NULL,
    organization        VARCHAR NOT NULL,
    repository          VARCHAR NOT NULL,
    description         VARCHAR,
    release_date        TIMESTAMPTZ,
    resolver            VARCHAR,
    licenses            VARCHAR NOT NULL,
    isNonStandardLib    BOOLEAN NOT NULL,
    PRIMARY KEY (group_id, artifact_id, version)
);

CREATE TABLE artifact_dependencies (
    source_group_id            VARCHAR NOT NULL,
    source_artifact_id         VARCHAR NOT NULL,
    source_version             VARCHAR NOT NULL,
    target_group_id            VARCHAR NOT NULL,
    target_artifact_id         VARCHAR NOT NULL,
    target_version             VARCHAR NOT NULL,
    scope                      VARCHAR,
    PRIMARY KEY (source_group_id, source_artifact_id, source_version, target_group_id, target_artifact_id, target_version, scope)
);

CREATE TABLE project_dependencies
(
    source_organization  VARCHAR(39)  NOT NULL,
    source_repository   VARCHAR(100) NOT NULL,
    target_organization VARCHAR(39)  NOT NULL,
    target_repository   VARCHAR(100) NOT NULL,
    PRIMARY KEY (source_organization, source_repository, target_organization, target_repository)
)
