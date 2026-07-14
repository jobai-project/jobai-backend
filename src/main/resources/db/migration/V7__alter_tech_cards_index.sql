DROP INDEX IF EXISTS idx_tech_cards_created_at;
CREATE INDEX IF NOT EXISTS idx_tech_cards_source_created_at ON tech_cards (source, created_at DESC);
