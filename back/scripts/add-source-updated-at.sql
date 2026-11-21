BEGIN;

ALTER TABLE sources ADD COLUMN IF NOT EXISTS updated_at timestamptz;

UPDATE sources
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE sources
    ALTER COLUMN updated_at SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN updated_at SET NOT NULL;

COMMIT;
