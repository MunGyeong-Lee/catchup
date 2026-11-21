CREATE TABLE IF NOT EXISTS sources (
    source_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_type text NOT NULL CONSTRAINT sources_source_type_check CHECK (
        source_type IN ('CAREER_HISTORY', 'EXPERIENCE', 'CERTIFICATION', 'TARGET_COMPANY', 'WRITING_STYLE', 'OTHER_TEXT')
    ),
    name text NOT NULL,
    details jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS upload_attempts (
    request_id uuid PRIMARY KEY,
    object_keys text NOT NULL,
    source_id bigint REFERENCES sources(source_id),
    target_source_id bigint,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS attachments (
    attachment_id uuid PRIMARY KEY,
    source_id bigint NOT NULL REFERENCES sources(source_id),
    original_name text NOT NULL,
    content_type text NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    object_key text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS attachments_source_index
    ON attachments(source_id);

CREATE INDEX IF NOT EXISTS upload_attempts_source_index
    ON upload_attempts(source_id);
