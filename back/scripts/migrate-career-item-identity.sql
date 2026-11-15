BEGIN;

LOCK TABLE career_items, attachments, upload_attempts IN ACCESS EXCLUSIVE MODE;

ALTER TABLE career_items RENAME COLUMN material_type TO career_item_type;
ALTER TABLE career_items ADD COLUMN career_item_id bigint GENERATED ALWAYS AS IDENTITY;
ALTER TABLE attachments ADD COLUMN career_item_id bigint;
ALTER TABLE upload_attempts ADD COLUMN career_item_id bigint;

UPDATE attachments AS attachment
SET career_item_id = item.career_item_id
FROM career_items AS item
WHERE attachment.material_id = item.material_id;

UPDATE upload_attempts AS attempt
SET career_item_id = item.career_item_id
FROM career_items AS item
WHERE attempt.material_id = item.material_id;

ALTER TABLE attachments ALTER COLUMN career_item_id SET NOT NULL;
ALTER TABLE attachments DROP COLUMN material_id;
ALTER TABLE upload_attempts DROP COLUMN material_id;
ALTER TABLE career_items DROP COLUMN material_id;
ALTER TABLE career_items ADD PRIMARY KEY (career_item_id);
ALTER TABLE attachments ADD FOREIGN KEY (career_item_id) REFERENCES career_items(career_item_id);
ALTER TABLE upload_attempts ADD FOREIGN KEY (career_item_id) REFERENCES career_items(career_item_id);

CREATE INDEX attachments_career_item_index ON attachments(career_item_id);

COMMIT;
