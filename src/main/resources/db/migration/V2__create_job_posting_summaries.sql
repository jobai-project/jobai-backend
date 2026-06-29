CREATE TABLE job_posting_summaries (
    id                BIGSERIAL PRIMARY KEY,
    job_posting_id    BIGINT NOT NULL UNIQUE,
    summary_json      JSONB NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    source_updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_jps_job_posting_id ON job_posting_summaries(job_posting_id);
