CREATE TABLE system_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at LONG NOT NULL,
    cpu_load DOUBLE NOT NULL,
    memory_used LONG NOT NULL,
    container_data TEXT NOT NULL
);
CREATE INDEX idx_history_created_at ON system_history(created_at);
