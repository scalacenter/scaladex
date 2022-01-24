CREATE TABLE projects (
    organization             VARCHAR(39)  NOT NULL,
    repository               VARCHAR(100) NOT NULL,
    creation_date            TIMESTAMPTZ,
    -- github status can be: Unknown, Ok, Moved, NotFound, Failed
    github_status            VARCHAR(10)  NOT NULL,
    github_update_date       TIMESTAMPTZ  NOT NULL,
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
    homepage               VARCHAR(2083),
    description            VARCHAR,
    logo                   VARCHAR(2083),
    stars                  INT,
    forks                  INT,
    watchers               INT,
    issues                 INT,
    creation_date          TIMESTAMPTZ,
    readme                 TEXT,
    contributors           VARCHAR,
    commits                INT,
    topics                 VARCHAR(1024) NOT NULL,
    contributing_guide     VARCHAR(2083),
    code_of_conduct        VARCHAR(2083),
    chatroom               VARCHAR(2083),
    open_issues            VARCHAR,
    FOREIGN KEY (organization, repository) REFERENCES projects (organization, repository),
    PRIMARY KEY (organization, repository)
);

CREATE TABLE project_settings (
   organization           VARCHAR(39)  NOT NULL,
   repository             VARCHAR(100) NOT NULL,
   default_stable_version BOOLEAN      NOT NULL,
   default_artifact       VARCHAR,
   strict_versions        BOOLEAN      NOT NULL,
   custom_scaladoc        VARCHAR,
   documentation_links    VARCHAR      NOT NULL,
   deprecated             BOOLEAN      NOT NULL,
   contributors_wanted    BOOLEAN      NOT NULL,
   artifact_deprecations  VARCHAR,
   cli_artifacts          VARCHAR,
   primary_topic          VARCHAR,
   beginner_issues_label  VARCHAR(1024),
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
    is_non_standard_lib BOOLEAN NOT NULL,
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
