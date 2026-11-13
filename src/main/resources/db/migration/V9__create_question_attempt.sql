CREATE TABLE question_attempt
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id      BIGINT      NOT NULL REFERENCES users (id),
    question_id  BIGINT      NOT NULL REFERENCES question (id),
    correct      BOOLEAN     NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_question_attempt_user_attempted_at ON question_attempt (user_id, attempted_at DESC);
