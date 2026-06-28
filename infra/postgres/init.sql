CREATE EXTENSION IF NOT EXISTS vector;

-- 임베딩 테이블
CREATE TABLE IF NOT EXISTS job_embeddings (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(10) NOT NULL,
    source_id BIGINT NOT NULL,
    embedding vector(768) NOT NULL,
    embedding_text TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (source, source_id)
);

-- IVFFlat 인덱스 (코사인 유사도 검색 최적화)
-- lists 값은 데이터 크기에 따라 조정 (sqrt(row_count) 권장)
CREATE INDEX IF NOT EXISTS idx_job_embeddings_vector
ON job_embeddings USING ivfflat (embedding vector_cosine_ops)
WITH (lists = 100);

-- source별 조회를 위한 인덱스
CREATE INDEX IF NOT EXISTS idx_job_embeddings_source
ON job_embeddings (source, source_id);
