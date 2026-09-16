ALTER TABLE meetings ADD COLUMN deleted_at DATETIME NULL;

CREATE INDEX idx_meetings_deleted_at ON meetings (deleted_at);
