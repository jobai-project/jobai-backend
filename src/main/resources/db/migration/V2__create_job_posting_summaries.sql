CREATE TABLE IF NOT EXISTS job_posting_summaries (
    id                BIGSERIAL PRIMARY KEY,
    job_posting_id    BIGINT NOT NULL UNIQUE,
    summary_json      JSONB NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP,
    source_updated_at TIMESTAMP NOT NULL
);
