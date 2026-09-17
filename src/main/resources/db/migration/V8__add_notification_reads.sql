CREATE TABLE notification_reads (
    user_id BIGINT NOT NULL,
    dedup_key VARCHAR(100) NOT NULL,
    read_date DATE NOT NULL,
    PRIMARY KEY (user_id, dedup_key, read_date),
    CONSTRAINT fk_notification_reads_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
