BEGIN;

ALTER TABLE IF EXISTS public.career_materials RENAME TO career_items;
ALTER TABLE IF EXISTS public.career_material_attachments RENAME TO attachments;
ALTER TABLE IF EXISTS public.career_material_attachment_attempts RENAME TO upload_attempts;
ALTER INDEX IF EXISTS public.career_material_attachments_material_index RENAME TO attachments_material_index;

COMMIT;
