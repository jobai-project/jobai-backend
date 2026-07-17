CREATE TABLE IF NOT EXISTS member_career_types (
    member_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    career_type VARCHAR(20) NOT NULL,
    CONSTRAINT fk_member_career_types_member
        FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
    CONSTRAINT uk_member_career_types UNIQUE (member_id, career_type)
);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'members' AND column_name = 'career_type'
    ) THEN
        INSERT INTO member_career_types (member_id, display_order, career_type)
        SELECT id,
               0,
               CASE career_type
                   WHEN '경력' THEN '경력직'
                   ELSE career_type
               END
        FROM members
        WHERE career_type IS NOT NULL
          AND career_type IN ('인턴', '신입', '경력', '경력직', '계약직')
        ON CONFLICT DO NOTHING;
    END IF;
END $$;

ALTER TABLE members DROP COLUMN IF EXISTS career_type;