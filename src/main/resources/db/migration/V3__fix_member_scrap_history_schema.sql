-- member_scrap_history: 공기업 전용 FK(job_posting_id) 설계에서 source+source_id 방식으로 전환.
-- 사기업(private_job_postings)은 공기업(job_postings)과 완전히 별개 테이블이라
-- 단일 FK로는 둘 다 참조할 수 없어, JobEmbedding과 동일한 source/source_id 범용 식별자로 변경했다.
--
-- 이 테이블은 이번 변경 전까지 실제 저장 API가 전혀 없었기 때문에(Entity만 존재, Repository/Service/Controller 없음)
-- 운영 환경에 이미 배포되어 있더라도 유효한 데이터가 쌓였을 수 없다. ddl-auto=update는 컬럼을 자동으로
-- 지우지 않으므로, 예전 job_posting_id NOT NULL 컬럼이 남아 신규 INSERT가 실패하는 것을 방지하기 위해
-- 안전하게(TRUNCATE 후) 스키마를 정리한다.

-- Flyway는 Hibernate ddl-auto보다 먼저 실행되므로, 이 시점엔 members 테이블이 아직 없을 수 있다
-- (완전히 새로운 환경/CI). V1·V2와 동일하게 Hibernate 관리 테이블에는 FK를 걸지 않는다.
CREATE TABLE IF NOT EXISTS member_scrap_history (
    id          BIGSERIAL   PRIMARY KEY,
    member_id   BIGINT      NOT NULL,
    source      VARCHAR(10) NOT NULL,
    source_id   BIGINT      NOT NULL,
    scrapped_at TIMESTAMP   DEFAULT NOW()
);

TRUNCATE TABLE member_scrap_history;

DO $$
DECLARE
    fk_name text;
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'member_scrap_history' AND column_name = 'job_posting_id'
    ) THEN
        SELECT tc.constraint_name INTO fk_name
        FROM information_schema.table_constraints tc
        JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name
        WHERE tc.table_name = 'member_scrap_history'
          AND tc.constraint_type = 'FOREIGN KEY'
          AND kcu.column_name = 'job_posting_id'
        LIMIT 1;

        IF fk_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE member_scrap_history DROP CONSTRAINT %I', fk_name);
        END IF;

        ALTER TABLE member_scrap_history DROP COLUMN job_posting_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'member_scrap_history' AND column_name = 'source'
    ) THEN
        ALTER TABLE member_scrap_history ADD COLUMN source VARCHAR(10) NOT NULL;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'member_scrap_history' AND column_name = 'source_id'
    ) THEN
        ALTER TABLE member_scrap_history ADD COLUMN source_id BIGINT NOT NULL;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_member_scrap_target
    ON member_scrap_history (member_id, source, source_id);
