CREATE TABLE chat_feedback (
    id                UUID PRIMARY KEY,
    user_id           UUID REFERENCES users (id) ON DELETE SET NULL,
    session_id        UUID,
    user_message      TEXT,
    assistant_answer  TEXT,
    reason            VARCHAR(500) NOT NULL,
    client_ip         VARCHAR(64),
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chat_feedback_created ON chat_feedback (created_at DESC);
CREATE INDEX idx_chat_feedback_status ON chat_feedback (status, created_at DESC);
