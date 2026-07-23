CREATE TABLE IF NOT EXISTS notification_match_batches (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    item_count INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_match_batches_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS notification_match_batch_items (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    source VARCHAR(20) NOT NULL,
    job_id BIGINT NOT NULL,
    title VARCHAR(500) NOT NULL,
    company_name VARCHAR(255),
    location VARCHAR(255),
    employment_type VARCHAR(255),
    deadline DATE,
    match_score INTEGER NOT NULL,
    detail_link_url VARCHAR(500) NOT NULL,
    CONSTRAINT fk_notification_match_batch_items_batch
        FOREIGN KEY (batch_id) REFERENCES notification_match_batches(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_notification_match_batches_member_created
    ON notification_match_batches(member_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_match_batch_items_batch_order
    ON notification_match_batch_items(batch_id, display_order);
