CREATE TABLE IF NOT EXISTS career_items (
    career_item_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    career_item_type text NOT NULL CHECK (career_item_type IN ('CAREER_HISTORY', 'EXPERIENCE', 'CERTIFICATION')),
    details jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS upload_attempts (
    request_id uuid PRIMARY KEY,
    object_keys text NOT NULL,
    career_item_id bigint REFERENCES career_items(career_item_id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS attachments (
    attachment_id uuid PRIMARY KEY,
    career_item_id bigint NOT NULL REFERENCES career_items(career_item_id),
    original_name text NOT NULL,
    content_type text NOT NULL,
    size_bytes bigint NOT NULL CHECK (size_bytes > 0),
    object_key text NOT NULL UNIQUE,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS attachments_career_item_index
    ON attachments(career_item_id);
