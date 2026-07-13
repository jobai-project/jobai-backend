CREATE TABLE IF NOT EXISTS tech_cards (
    id             BIGSERIAL    PRIMARY KEY,
    source         VARCHAR(30)  NOT NULL,
    external_id    VARCHAR(500) NOT NULL,
    original_title VARCHAR(500) NOT NULL,
    original_url   VARCHAR(1000) NOT NULL,
    headline       VARCHAR(200) NOT NULL,
    subtext        VARCHAR(300) NOT NULL,
    published_at   TIMESTAMP,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_tech_card_source_external_id UNIQUE (source, external_id)
);

CREATE INDEX IF NOT EXISTS idx_tech_cards_source_created_at ON tech_cards (source, created_at DESC);
