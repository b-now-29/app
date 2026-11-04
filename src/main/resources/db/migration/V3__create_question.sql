CREATE TABLE question
(
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category_id  BIGINT      NOT NULL REFERENCES category (id),
    type         VARCHAR(20) NOT NULL
        CHECK (type IN ('MULTIPLE_CHOICE', 'SHORT_ANSWER', 'ESSAY')),
    content      TEXT        NOT NULL,
    model_answer TEXT, -- 주관식·서술형 모범 답안
    explanation  TEXT,
    sort_order   INT         NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- 주관식·서술형은 모범 답안이 반드시 있어야 함
    CONSTRAINT chk_question_model_answer
        CHECK (type = 'MULTIPLE_CHOICE' OR model_answer IS NOT NULL)
);
CREATE INDEX idx_question_category_id ON question (category_id);
