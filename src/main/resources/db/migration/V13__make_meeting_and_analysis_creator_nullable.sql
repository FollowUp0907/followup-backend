ALTER TABLE meetings MODIFY COLUMN created_by BIGINT NULL;
ALTER TABLE ai_analysis_runs MODIFY COLUMN requested_by BIGINT NULL;
ALTER TABLE notifications MODIFY COLUMN action_item_id BIGINT NULL;
