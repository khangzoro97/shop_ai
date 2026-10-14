-- Home page trials, kept after the job is claimed so the daily caps still count it.
-- The visitor's IP is stored as a SHA-256 hash.
CREATE TABLE trial_run (
    job_id     uuid PRIMARY KEY,
    ip_hash    varchar(64)              NOT NULL,
    created_at timestamp(6) with time zone NOT NULL
);

CREATE INDEX idx_trial_run_created ON trial_run (created_at);
CREATE INDEX idx_trial_run_ip ON trial_run (ip_hash, created_at);
