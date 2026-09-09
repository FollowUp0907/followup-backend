ALTER TABLE ai_analysis_runs
    ADD COLUMN input_hash VARCHAR(64) NULL AFTER prompt_version;

CREATE INDEX idx_ai_analysis_runs_dedup ON ai_analysis_runs (meeting_id, input_hash, model_name, prompt_version, status);
