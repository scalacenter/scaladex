CREATE TABLE projects (
    organization         VARCHAR(39)   NOT NULL,
    repository           VARCHAR(100)  NOT NULL,
    created_at           TIMESTAMPTZ,
    esId                 VARCHAR,
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
    beginnerIssuesLabel    VARCHAR(1024),
    beginnerIssues         VARCHAR,
    selectedBeginnerIssues VARCHAR,
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
   FOREIGN KEY (organization, repository) REFERENCES projects (organization, repository),
   PRIMARY KEY (organization, repository)
);

CREATE TABLE releases (
    groupId             VARCHAR NOT NULL,
    artifactId          VARCHAR NOT NULL,
    version             VARCHAR NOT NULL,
    organization        VARCHAR NOT NULL,
    repository          VARCHAR NOT NULL,
    artifact            VARCHAR NOT NULL,
    platform            VARCHAR NOT NULL,
    description         VARCHAR,
    released_at         TIMESTAMPTZ,
    resolver            VARCHAR,
    licenses            VARCHAR NOT NULL,
    isNonStandardLib    BOOLEAN NOT NULL,
    PRIMARY KEY (groupId, artifactId, version)
);

CREATE TABLE release_dependencies (
    source_groupId             VARCHAR NOT NULL,
    source_artifactId          VARCHAR NOT NULL,
    source_version             VARCHAR NOT NULL,
    target_groupId             VARCHAR NOT NULL,
    target_artifactId          VARCHAR NOT NULL,
    target_version             VARCHAR NOT NULL,
    scope                      VARCHAR,
    PRIMARY KEY (source_groupId, source_artifactId, source_version, target_groupId, target_artifactId, target_version, scope)
);

CREATE TABLE project_dependencies
(
    source_organization  VARCHAR(39)  NOT NULL,
    source_repository   VARCHAR(100) NOT NULL,
    target_organization VARCHAR(39)  NOT NULL,
    target_repository   VARCHAR(100) NOT NULL,
    PRIMARY KEY (source_organization, source_repository, target_organization, target_repository)
)
