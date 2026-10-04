ALTER TABLE sync_job
    ADD COLUMN failure_code VARCHAR(64),
    ADD COLUMN failure_message VARCHAR(500);
