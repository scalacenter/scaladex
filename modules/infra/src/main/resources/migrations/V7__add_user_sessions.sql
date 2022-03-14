CREATE TABLE user_sessions(
    user_id       VARCHAR NOT NULL,
    repos         VARCHAR,
    orgs          VARCHAR,
    login         VARCHAR(39) NOT NULL,
    name          VARCHAR(100),
    avatar_url    VARCHAR NOT NULL,
    secret        VARCHAR NOT NULL,
    PRIMARY KEY (user_id)
);
