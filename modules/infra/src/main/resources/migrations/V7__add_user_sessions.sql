CREATE TYPE ENV AS ENUM ('local', 'dev', 'prod');

CREATE TABLE user_info(
    login         VARCHAR(39) NOT NULL,
    name          VARCHAR(100),
    avatar_url    VARCHAR NOT NULL,
    secret        VARCHAR NOT NULL,
    PRIMARY KEY (login)
);
CREATE TABLE user_sessions(
    user_id  VARCHAR NOT NULL,
    repos    VARCHAR,
    orgs     VARCHAR,
    login    VARCHAR(39) NOT NULL,
    env ENV  NOT NULL,
    FOREIGN KEY (login) REFERENCES user_info(login),
    PRIMARY KEY (user_id)
);
