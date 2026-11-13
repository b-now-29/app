-- 회원 권한: 기존 회원은 모두 USER
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER',
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'));

-- soft delete: 풀이 기록이 문제를 참조하므로 행은 지우지 않는다
ALTER TABLE category
    ADD COLUMN deleted_at TIMESTAMPTZ;

ALTER TABLE question
    ADD COLUMN deleted_at TIMESTAMPTZ;
