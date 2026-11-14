-- dev 프로필 전용 더미 데이터 (application-dev.yaml 의 flyway.locations 에서만 읽는다).
-- 이미 시드됐으면 (users 에 '@dev.local' 회원이 있으면) 통째로 건너뛴다.
-- 내용을 바꾼 뒤 다시 넣으려면 로컬 DB를 초기화한다: docker compose down -v

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO
$$
    DECLARE
        -- dev 회원 20명 (user01~20@dev.local). 퀴즈왕 3명은 tag 1~3 확인용, 18번 '신입'은 풀이 0건, 20번은 ADMIN
        nicknames  text[] := ARRAY['퀴즈왕', '퀴즈왕', '퀴즈왕', '코딩러', '코딩러', '달리기', '문제풀이', '알고왕', '자바킹', '스프링',
            '리액트', 'DB마스터', '백엔드', '프론트', '새벽반', '야근러', '만점왕', '신입', '복습러', '운영자'];
        -- 회원별 정답 확률 (0.3~0.9). user01~user20 순서 (퀴즈왕 #1 0.9, #2 0.5, #3 0.3)
        accuracies numeric[] := ARRAY[
            0.9, 0.5, 0.3, 0.75, 0.45,
            0.65, 0.55, 0.8, 0.35, 0.7,
            0.6, 0.85, 0.4, 0.5, 0.75,
            0.3, 0.9, 0.6, 0.55, 0.6];
        i          int;
        uid        bigint;
        plan       record;
        deleted_q  bigint[];
        bulk_id    bigint;
        deleted_id bigint;
        qid        bigint;
        pos        int;
        cnt        int;
    BEGIN
        IF EXISTS (SELECT 1 FROM users WHERE email LIKE '%@dev.local') THEN
            RETURN;
        END IF;

        PERFORM setseed(0.29);

        CREATE TEMP TABLE dev_seed_plan
        (
            user_id  bigint,
            accuracy numeric,
            cnt      int,
            re_cnt   int
        );

        -- 1. 테스트 계정: TestAccountInitializer 보다 먼저 실행되므로 없을 때만 만든다
        INSERT INTO users (email, password_hash, role, nickname, tag, created_at)
        SELECT 'test@example.com', crypt('test1234!', gen_salt('bf')), 'USER', '테스트',
               COALESCE((SELECT max(tag) FROM users WHERE nickname = '테스트'), 0) + 1, now()
        WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'test@example.com');

        INSERT INTO users (email, password_hash, role, nickname, tag, created_at)
        SELECT 'admin@example.com', crypt('admin1234!', gen_salt('bf')), 'ADMIN', '관리자',
               COALESCE((SELECT max(tag) FROM users WHERE nickname = '관리자'), 0) + 1, now()
        WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@example.com');

        -- test 계정: 정답 약 60%, 풀이 60건 + 다시 푼 기록 10건
        INSERT INTO dev_seed_plan (user_id, accuracy, cnt, re_cnt)
        SELECT id, 0.6, 60, 10
        FROM users
        WHERE email = 'test@example.com';

        -- 2. dev 회원 20명. tag 는 닉네임별 기존 최대값 + 1
        FOR i IN 1..20
            LOOP
                INSERT INTO users (email, password_hash, role, nickname, tag, created_at)
                VALUES ('user' || lpad(i::text, 2, '0') || '@dev.local',
                        crypt('dev1234!', gen_salt('bf')),
                        CASE WHEN i = 20 THEN 'ADMIN' ELSE 'USER' END,
                        nicknames[i],
                        COALESCE((SELECT max(tag) FROM users WHERE nickname = nicknames[i]), 0) + 1,
                        -- 풀이 기록(최근 30일)보다 먼저 가입하도록 30~60일 전, 신입은 1일 전
                        CASE WHEN i = 18 THEN now() - interval '1 day'
                             ELSE now() - interval '30 days' - random() * interval '30 days' END)
                RETURNING id INTO uid;

                -- 1~17번: 30~60건, 19번: 5건, 18번(신입)·20번(관리자): 0건
                cnt := CASE
                           WHEN i <= 17 THEN 30 + floor(random() * 31)::int
                           WHEN i = 19 THEN 5
                           ELSE 0 END;
                INSERT INTO dev_seed_plan (user_id, accuracy, cnt, re_cnt)
                VALUES (uid, accuracies[i], cnt, cnt / 8);
            END LOOP;

        -- 3. 문제 추가
        INSERT INTO category (slug, name, description, sort_order, deleted_at)
        VALUES ('dev-bulk', '더미 문제 모음', '목록·스크롤 확인용 더미 문제', 900, NULL)
        RETURNING id INTO bulk_id;

        INSERT INTO category (slug, name, description, sort_order, deleted_at)
        VALUES ('dev-deleted', '삭제된 카테고리', '삭제된 카테고리 확인용', 901, now() - interval '3 days')
        RETURNING id INTO deleted_id;

        -- dev-bulk: 객관식 1~40, 주관식 41~50, 서술형 51~60 (39번 객관식, 59번 서술형은 삭제)
        FOR i IN 1..60
            LOOP
                INSERT INTO question (category_id, type, content, model_answer, explanation, sort_order, deleted_at)
                VALUES (bulk_id,
                        CASE WHEN i <= 40 THEN 'MULTIPLE_CHOICE' WHEN i <= 50 THEN 'SHORT_ANSWER' ELSE 'ESSAY' END,
                        CASE WHEN i <= 40 THEN '더미 객관식 문제 ' || i
                             WHEN i <= 50 THEN '더미 주관식 문제 ' || i
                             ELSE '더미 서술형 문제 ' || i END,
                        CASE WHEN i > 40 THEN '더미 모범 답안 ' || i END,
                        '더미 해설 ' || i,
                        i,
                        CASE WHEN i IN (39, 59) THEN now() - interval '2 days' END)
                RETURNING id INTO qid;

                IF i <= 40 THEN
                    INSERT INTO choice (question_id, content, is_correct, sort_order)
                    SELECT qid, '보기 ' || (p + 1), p = i % 4, p + 1
                    FROM generate_series(0, 3) AS p;
                END IF;
            END LOOP;

        -- dev-deleted: 삭제된 카테고리의 객관식 5개
        FOR i IN 61..65
            LOOP
                INSERT INTO question (category_id, type, content, model_answer, explanation, sort_order, deleted_at)
                VALUES (deleted_id, 'MULTIPLE_CHOICE', '더미 객관식 문제 ' || i, NULL, '더미 해설 ' || i, i, NULL)
                RETURNING id INTO qid;

                INSERT INTO choice (question_id, content, is_correct, sort_order)
                SELECT qid, '보기 ' || (p + 1), p = i % 4, p + 1
                FROM generate_series(0, 3) AS p;
            END LOOP;

        -- 4. 풀이 기록 (삭제되지 않은 문제 중에서 무작위, 최근 30일)
        FOR plan IN SELECT * FROM dev_seed_plan ORDER BY user_id
            LOOP
                INSERT INTO question_attempt (user_id, question_id, correct, attempted_at)
                SELECT plan.user_id, q.id, random() < plan.accuracy, now() - random() * interval '30 days'
                FROM question q
                         JOIN category c ON c.id = q.category_id
                WHERE q.deleted_at IS NULL
                  AND c.deleted_at IS NULL
                ORDER BY random()
                LIMIT plan.cnt;

                -- 같은 문제를 다시 푼 기록 (오답 노트는 문제별 최근 기록 기준)
                INSERT INTO question_attempt (user_id, question_id, correct, attempted_at)
                SELECT a.user_id, a.question_id, random() < least(plan.accuracy + 0.1, 1),
                       least(now(), a.attempted_at + random() * interval '5 days')
                FROM question_attempt a
                WHERE a.user_id = plan.user_id
                ORDER BY random()
                LIMIT plan.re_cnt;
            END LOOP;

        -- 삭제된 문제·카테고리에 대한 기록 20건 (dev 회원 user01~05 에 돌려 가며)
        SELECT array_agg(q.id ORDER BY q.id)
        INTO deleted_q
        FROM question q
                 JOIN category c ON c.id = q.category_id
        WHERE q.deleted_at IS NOT NULL
           OR c.deleted_at IS NOT NULL;

        INSERT INTO question_attempt (user_id, question_id, correct, attempted_at)
        SELECT (SELECT id FROM users WHERE email = 'user0' || (g % 5 + 1) || '@dev.local'),
               deleted_q[1 + g % array_length(deleted_q, 1)],
               random() < 0.5,
               now() - random() * interval '30 days'
        FROM generate_series(1, 20) AS g;

        DROP TABLE dev_seed_plan;
    END
$$;
