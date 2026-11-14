-- 로그아웃하면 올라가는 값. 토큰에 담긴 값과 다르면 그 토큰은 무효다.
ALTER TABLE users
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
