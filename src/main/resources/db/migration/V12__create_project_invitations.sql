CREATE TABLE project_invitations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    invited_by BIGINT NOT NULL,
    invited_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    accepted_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_project_invitations_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_invitations_invited_by FOREIGN KEY (invited_by) REFERENCES users (id),
    INDEX idx_project_invitations_project_email_status (project_id, email, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
