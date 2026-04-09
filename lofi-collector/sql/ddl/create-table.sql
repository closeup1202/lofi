CREATE TABLE method_metric (
                               id           INTEGER PRIMARY KEY AUTOINCREMENT,
                               commit_hash  TEXT    NOT NULL,
                               class_name   TEXT    NOT NULL,
                               method_name  TEXT    NOT NULL,
                               elapsed_ms   INTEGER NOT NULL,
                               recorded_at  TEXT    NOT NULL
);

CREATE INDEX idx_commit_hash ON method_metric(commit_hash);