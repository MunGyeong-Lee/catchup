BEGIN;

DO $migration$
DECLARE
    existing_column text;
    matching_columns integer;
BEGIN
    SELECT min(attname::text), count(*) INTO existing_column, matching_columns
    FROM pg_attribute
    WHERE attrelid = 'sources'::regclass
      AND attname IN ('attributes', 'detail', 'details')
      AND attnum > 0
      AND NOT attisdropped;

    IF matching_columns <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one of attributes, detail, or details on sources';
    END IF;

    IF existing_column <> 'details' THEN
        EXECUTE format('ALTER TABLE sources RENAME COLUMN %I TO details', existing_column);
    END IF;
END;
$migration$;

COMMIT;
