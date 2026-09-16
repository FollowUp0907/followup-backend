CREATE TABLE notifications (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    action_item_id BIGINT NOT NULL,
    task_title VARCHAR(255) NOT NULL,
    remind_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    read_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_notifications_action_item UNIQUE (action_item_id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_notifications_action_item FOREIGN KEY (action_item_id) REFERENCES action_items (id),
    INDEX idx_notifications_user_remind_at (user_id, remind_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
