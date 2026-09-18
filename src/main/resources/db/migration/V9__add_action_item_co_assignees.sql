CREATE TABLE action_item_co_assignees (
    action_item_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (action_item_id, user_id),
    CONSTRAINT fk_action_item_co_assignees_action_item FOREIGN KEY (action_item_id) REFERENCES action_items (id),
    CONSTRAINT fk_action_item_co_assignees_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
