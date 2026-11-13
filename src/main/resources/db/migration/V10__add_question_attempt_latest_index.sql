CREATE INDEX idx_question_attempt_user_question_attempted_at ON question_attempt (user_id, question_id, attempted_at DESC, id DESC);
