-- region 컬럼 불필요 (location 필드에 정규화 값을 덮어쓰는 방식으로 변경)
ALTER TABLE private_job_postings DROP COLUMN IF EXISTS region;
