ALTER TABLE action_items ADD COLUMN created_by BIGINT NULL;
ALTER TABLE action_items ADD CONSTRAINT fk_action_items_created_by FOREIGN KEY (created_by) REFERENCES users (id);
