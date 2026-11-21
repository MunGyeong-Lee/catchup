BEGIN;

DO $migration$
DECLARE
    schema_name text := current_schema();
    has_career_items boolean := to_regclass(format('%I.career_items', schema_name)) IS NOT NULL;
    has_sources boolean := to_regclass(format('%I.sources', schema_name)) IS NOT NULL;
    has_source boolean := to_regclass(format('%I.source', schema_name)) IS NOT NULL;
    constraint_name record;
    identity_sequence regclass;
BEGIN
    IF (has_career_items::integer + has_sources::integer + has_source::integer) <> 1 THEN
        RAISE EXCEPTION 'Expected exactly one of career_items, sources, or source in schema %', schema_name;
    END IF;
    PERFORM set_config('search_path', format('%I, pg_temp', schema_name), true);

    IF has_career_items THEN
        ALTER TABLE career_items RENAME TO sources;
        ALTER TABLE sources RENAME COLUMN career_item_id TO source_id;
        ALTER TABLE sources RENAME COLUMN career_item_type TO source_type;
        ALTER TABLE attachments RENAME COLUMN career_item_id TO source_id;
        ALTER TABLE upload_attempts RENAME COLUMN career_item_id TO source_id;
        IF EXISTS (
            SELECT 1 FROM pg_attribute
            WHERE attrelid = 'upload_attempts'::regclass
              AND attname = 'target_career_item_id' AND NOT attisdropped
        ) THEN
            ALTER TABLE upload_attempts RENAME COLUMN target_career_item_id TO target_source_id;
        END IF;
    ELSIF has_source THEN
        ALTER TABLE source RENAME TO sources;
    END IF;

    FOR constraint_name IN
        SELECT * FROM (VALUES
            ('sources', 'career_items_pkey', 'sources_pkey'),
            ('sources', 'source_pkey', 'sources_pkey'),
            ('sources', 'career_materials_material_type_check', 'sources_source_type_check'),
            ('sources', 'career_items_career_item_type_check', 'sources_source_type_check'),
            ('sources', 'source_source_type_check', 'sources_source_type_check'),
            ('attachments', 'attachments_career_item_id_fkey', 'attachments_source_id_fkey'),
            ('upload_attempts', 'upload_attempts_career_item_id_fkey', 'upload_attempts_source_id_fkey')
        ) AS names(relation_name, old_name, new_name)
    LOOP
        IF EXISTS (
            SELECT 1 FROM pg_constraint
            WHERE conrelid = to_regclass(format('%I.%I', schema_name, constraint_name.relation_name))
              AND conname = constraint_name.old_name
        ) THEN
            EXECUTE format('ALTER TABLE %I.%I RENAME CONSTRAINT %I TO %I',
                schema_name, constraint_name.relation_name, constraint_name.old_name, constraint_name.new_name);
        END IF;
    END LOOP;

    identity_sequence := pg_get_serial_sequence(format('%I.sources', schema_name), 'source_id')::regclass;
    IF identity_sequence IS NOT NULL
        AND identity_sequence IS DISTINCT FROM to_regclass(format('%I.sources_source_id_seq', schema_name)) THEN
        EXECUTE format('ALTER SEQUENCE %s RENAME TO sources_source_id_seq', identity_sequence);
    END IF;

    ALTER INDEX IF EXISTS attachments_career_item_index RENAME TO attachments_source_index;
    ALTER INDEX IF EXISTS upload_attempts_career_item_index RENAME TO upload_attempts_source_index;
END;
$migration$;

ALTER TABLE upload_attempts ADD COLUMN IF NOT EXISTS target_source_id bigint;
ALTER TABLE upload_attempts ADD COLUMN IF NOT EXISTS completed_at timestamptz;

UPDATE upload_attempts
SET completed_at = created_at
WHERE source_id IS NOT NULL AND completed_at IS NULL;

COMMIT;
