CREATE TABLE choice (
                        id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        question_id BIGINT  NOT NULL REFERENCES question (id) ON DELETE CASCADE,
                        content     TEXT    NOT NULL,
                        is_correct  BOOLEAN NOT NULL DEFAULT FALSE,
                        sort_order  INT     NOT NULL DEFAULT 0
);
CREATE INDEX idx_choice_question_id ON choice (question_id);

-- 문제 하나에 정답 보기는 최대 1개
CREATE UNIQUE INDEX uq_choice_one_correct ON choice (question_id) WHERE is_correct;
