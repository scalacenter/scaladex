CREATE TABLE discovered_group_id(
    group_id         VARCHAR      NOT NULL,
    source           VARCHAR      NOT NULL,
    discovered_at    TIMESTAMPTZ  NOT NULL,
    last_synced_at   TIMESTAMPTZ,
    sync_summary     VARCHAR,
    project_refs     VARCHAR,
    status           VARCHAR      NOT NULL DEFAULT 'Pending',
    reviewed_by      VARCHAR,
    reviewed_at      TIMESTAMPTZ,
    PRIMARY KEY (group_id)
);

CREATE TABLE discovered_index_cursor(
    id               INT          NOT NULL DEFAULT 1 CHECK (id = 1),
    chain_id         VARCHAR      NOT NULL,
    last_incremental INT          NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (id)
);
