ALTER TABLE users
    ADD COLUMN nickname VARCHAR(20),
    ADD COLUMN tag      INTEGER;

-- 기존 회원은 닉네임 '회원', 번호는 가입 순서 (id 순) 1, 2, 3...
UPDATE users
SET nickname = '회원',
    tag      = numbered.rn
FROM (SELECT id, row_number() OVER (ORDER BY id) AS rn FROM users) AS numbered
WHERE users.id = numbered.id;

ALTER TABLE users
    ALTER COLUMN nickname SET NOT NULL,
    ALTER COLUMN tag SET NOT NULL,
    ADD CONSTRAINT users_tag_positive CHECK (tag >= 1),
    ADD CONSTRAINT uk_users_nickname_tag UNIQUE (nickname, tag);
