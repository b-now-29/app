-- 역할 개편: ADMIN을 없애고 OWNER(운영자, 1명)와 CATEGORY_ADMIN(카테고리 관리자)으로 나눈다

-- 카테고리 관리자의 담당 카테고리
CREATE TABLE category_admin
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users (id),
    category_id BIGINT NOT NULL REFERENCES category (id),
    CONSTRAINT uq_category_admin UNIQUE (user_id, category_id)
);
CREATE INDEX idx_category_admin_category ON category_admin (category_id);

ALTER TABLE users
    DROP CONSTRAINT chk_users_role;

-- id가 가장 작은 ADMIN은 OWNER
UPDATE users
SET role = 'OWNER'
WHERE id = (SELECT min(id) FROM users WHERE role = 'ADMIN');

-- 나머지 ADMIN은 삭제되지 않은 모든 카테고리를 담당하는 CATEGORY_ADMIN
INSERT INTO category_admin (user_id, category_id)
SELECT u.id, c.id
FROM users u
         CROSS JOIN category c
WHERE u.role = 'ADMIN'
  AND c.deleted_at IS NULL;

UPDATE users
SET role = 'CATEGORY_ADMIN'
WHERE role = 'ADMIN'
  AND id IN (SELECT user_id FROM category_admin);

-- 삭제되지 않은 카테고리가 없어 담당이 없는 ADMIN은 USER (담당 0개인 CATEGORY_ADMIN은 없다)
UPDATE users
SET role = 'USER'
WHERE role = 'ADMIN';

ALTER TABLE users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('USER', 'CATEGORY_ADMIN', 'OWNER'));

-- OWNER는 한 명뿐이다
CREATE UNIQUE INDEX uq_users_single_owner ON users ((true)) WHERE role = 'OWNER';
