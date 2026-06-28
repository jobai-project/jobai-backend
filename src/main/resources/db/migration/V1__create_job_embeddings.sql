CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS job_embeddings (
    id         BIGSERIAL    PRIMARY KEY,
    source     VARCHAR(10)  NOT NULL,
    source_id  BIGINT       NOT NULL,
    embedding  vector(768)  NOT NULL,
    embedding_text TEXT,
    created_at TIMESTAMP    DEFAULT NOW(),
    UNIQUE (source, source_id)
);

CREATE INDEX IF NOT EXISTS idx_job_embeddings_source
    ON job_embeddings (source, source_id);
