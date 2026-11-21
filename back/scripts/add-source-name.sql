BEGIN;

ALTER TABLE sources ADD COLUMN IF NOT EXISTS name text;

UPDATE sources
SET name = COALESCE(
    NULLIF(CASE source_type
        WHEN 'CAREER_HISTORY' THEN details ->> 'organization'
        WHEN 'EXPERIENCE' THEN details ->> 'title'
        WHEN 'CERTIFICATION' THEN details ->> 'name'
    END, ''),
    '자료 #' || source_id
)
WHERE name IS NULL;

ALTER TABLE sources ALTER COLUMN name SET NOT NULL;

COMMIT;
