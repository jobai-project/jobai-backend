-- 문자열 "null"로 저장된 값을 실제 NULL로 정리
UPDATE private_job_postings SET location = NULL WHERE location = 'null';
UPDATE private_job_postings SET employment_type = NULL WHERE employment_type = 'null';
UPDATE private_job_postings SET experience_level = NULL WHERE experience_level = 'null';
