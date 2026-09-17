-- uk_notifications_action_item는 fk_notifications_action_item(action_item_id -> action_items.id)를
-- 뒷받침하는 유일한 인덱스라, 대체 인덱스 없이 바로 DROP하면 FK 제약 때문에 실패한다.
-- 그래서 같은 컬럼에 대한 일반 인덱스를 먼저 추가하고 나서 유니크 인덱스를 지운다.
ALTER TABLE notifications
    ADD INDEX idx_notifications_action_item (action_item_id),
    DROP INDEX uk_notifications_action_item;
