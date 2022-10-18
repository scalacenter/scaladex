CREATE TABLE user_sessions(
    user_id       VARCHAR NOT NULL,
    repos         VARCHAR NOT NULL,
    orgs          VARCHAR NOT NULL,
    login         VARCHAR(39) NOT NULL,
    name          VARCHAR,
    avatar_url    VARCHAR NOT NULL,
    secret        VARCHAR NOT NULL,
    PRIMARY KEY (user_id)
);