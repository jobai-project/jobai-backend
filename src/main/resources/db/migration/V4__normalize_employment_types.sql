-- experienceLevel 컬럼 추가
ALTER TABLE private_job_postings ADD COLUMN IF NOT EXISTS experience_level VARCHAR(255);

-- 기존 employmentType raw값 정규화
UPDATE private_job_postings SET employment_type = '정규직' WHERE employment_type IN ('FULL_TIME_WORKER', '정규');
UPDATE private_job_postings SET employment_type = '인턴' WHERE employment_type IN ('INTERN_WORKER');
UPDATE private_job_postings SET employment_type = '병역특례' WHERE employment_type IN ('MILITARY_SERVICE_EXCEPTION');
UPDATE private_job_postings SET employment_type = '계약직' WHERE employment_type IN ('계약');
