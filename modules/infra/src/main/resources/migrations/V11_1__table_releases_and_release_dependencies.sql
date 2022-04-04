CREATE TABLE releases
(
    organization             VARCHAR(39)  NOT NULL,
    repository               VARCHAR(100) NOT NULL,
    platform                 VARCHAR NOT NULL,
    language_version         VARCHAR NOT NULL,
    version                  VARCHAR NOT NULL,
    release_date             TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (organization, repository, platform, language_version, version)
);
CREATE TABLE release_dependencies
(
    source_organization             VARCHAR(39)  NOT NULL,
    source_repository               VARCHAR(100) NOT NULL,
    source_platform                 VARCHAR NOT NULL,
    source_language_version         VARCHAR NOT NULL,
    source_version                  VARCHAR NOT NULL,
    source_release_date             TIMESTAMPTZ  NOT NULL,
    target_organization             VARCHAR(39)  NOT NULL,
    target_repository               VARCHAR(100) NOT NULL,
    target_platform                 VARCHAR NOT NULL,
    target_language_version         VARCHAR NOT NULL,
    target_version                  VARCHAR NOT NULL,
    target_release_date             TIMESTAMPTZ  NOT NULL,
    scope                           VARCHAR NOT NULL,
    PRIMARY KEY (source_organization, source_repository, source_platform, source_language_version, source_version,
                 target_organization, target_repository, target_platform, target_language_version, target_version, scope)
);

