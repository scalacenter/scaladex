CREATE TABLE projects (
  organization VARCHAR(39)  NOT NULL,
  repository   VARCHAR(100)  NOT NULL,
  PRIMARY KEY (organization, repository)
);

CREATE TABLE github_info (
    organization           VARCHAR(39)  NOT NULL,
    repository             VARCHAR(100) NOT NULL,
    name                   VARCHAR(39),
    owner                  VARCHAR(100),
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
    topics                 VARCHAR(1024),
    contributingGuide      VARCHAR(2083),
    codeOfConduct          VARCHAR(2083),
    chatroom               VARCHAR(2083),
    beginnerIssuesLabel    VARCHAR(1024),
    beginnerIssues         VARCHAR,
    selectedBeginnerIssues VARCHAR,
    filteredBeginnerIssues VARCHAR,
  PRIMARY KEY (organization, repository)
);