-- 주 담당자(assignee) + 보조 담당자(coAssignees) 이원 구조를 "담당자(assignees)" 하나로 합친다.
RENAME TABLE action_item_co_assignees TO action_item_assignees;

INSERT IGNORE INTO action_item_assignees (action_item_id, user_id)
SELECT id, assignee_user_id FROM action_items WHERE assignee_user_id IS NOT NULL;

ALTER TABLE action_items DROP FOREIGN KEY fk_action_items_assignee_user;
DROP INDEX idx_action_items_assignee_status ON action_items;
ALTER TABLE action_items DROP COLUMN assignee_user_id;
