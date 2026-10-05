ALTER TABLE integration_task
    ADD COLUMN processing_started_at TIMESTAMPTZ;

UPDATE integration_task
SET processing_started_at = updated_at
WHERE status = 'PROCESSING';
