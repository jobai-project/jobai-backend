ALTER TABLE notification_match_batch_items
    ADD COLUMN IF NOT EXISTS job_category VARCHAR(255);
