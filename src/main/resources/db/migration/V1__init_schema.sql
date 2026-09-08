CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE projects (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(150) NOT NULL,
    description TEXT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_projects_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE project_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    joined_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE meetings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    scheduled_at DATETIME NOT NULL,
    content LONGTEXT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_meetings_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_meetings_created_by FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_meetings_project_scheduled_at ON meetings (project_id, scheduled_at);

CREATE TABLE meeting_participants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    meeting_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_meeting_participants_meeting_user UNIQUE (meeting_id, user_id),
    CONSTRAINT fk_meeting_participants_meeting FOREIGN KEY (meeting_id) REFERENCES meetings (id),
    CONSTRAINT fk_meeting_participants_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_analysis_runs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    meeting_id BIGINT NOT NULL,
    requested_by BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    model_name VARCHAR(100) NULL,
    prompt_version VARCHAR(30) NULL,
    draft_json JSON NULL,
    error_message TEXT NULL,
    created_at DATETIME NOT NULL,
    confirmed_at DATETIME NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ai_analysis_runs_meeting FOREIGN KEY (meeting_id) REFERENCES meetings (id),
    CONSTRAINT fk_ai_analysis_runs_requested_by FOREIGN KEY (requested_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_ai_analysis_runs_meeting_created_at ON ai_analysis_runs (meeting_id, created_at);

CREATE TABLE decisions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    meeting_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    source_analysis_id BIGINT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_decisions_meeting FOREIGN KEY (meeting_id) REFERENCES meetings (id),
    CONSTRAINT fk_decisions_source_analysis FOREIGN KEY (source_analysis_id) REFERENCES ai_analysis_runs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE action_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    origin_meeting_id BIGINT NULL,
    assignee_user_id BIGINT NULL,
    source_analysis_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    due_date DATE NULL,
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL,
    priority_reason TEXT NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_action_items_project FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_action_items_origin_meeting FOREIGN KEY (origin_meeting_id) REFERENCES meetings (id),
    CONSTRAINT fk_action_items_assignee_user FOREIGN KEY (assignee_user_id) REFERENCES users (id),
    CONSTRAINT fk_action_items_source_analysis FOREIGN KEY (source_analysis_id) REFERENCES ai_analysis_runs (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_action_items_project_status ON action_items (project_id, status);
CREATE INDEX idx_action_items_project_due_date ON action_items (project_id, due_date);
CREATE INDEX idx_action_items_assignee_status ON action_items (assignee_user_id, status);

CREATE TABLE meeting_action_links (
    id BIGINT NOT NULL AUTO_INCREMENT,
    meeting_id BIGINT NOT NULL,
    action_item_id BIGINT NOT NULL,
    link_type VARCHAR(20) NOT NULL,
    linked_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_meeting_action_links_meeting_action UNIQUE (meeting_id, action_item_id),
    CONSTRAINT fk_meeting_action_links_meeting FOREIGN KEY (meeting_id) REFERENCES meetings (id),
    CONSTRAINT fk_meeting_action_links_action_item FOREIGN KEY (action_item_id) REFERENCES action_items (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
