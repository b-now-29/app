-- 카테고리별 객관식 시드. 문제 id는 DB가 만들므로 INSERT ... RETURNING으로 받아 보기에 연결한다.

-- Java
WITH q AS (
    INSERT INTO question (category_id, type, content, explanation, sort_order)
    SELECT id, 'MULTIPLE_CHOICE',
           '두 String의 내용이 같은지 비교할 때 써야 하는 것은?',
           '==는 같은 객체를 가리키는지 비교한다. 문자열 내용 비교는 equals()로 한다.',
           10
    FROM category WHERE slug = 'java'
    RETURNING id
)
INSERT INTO choice (question_id, content, is_correct, sort_order)
SELECT q.id, v.content, v.is_correct, v.sort_order
FROM q CROSS JOIN (VALUES ('==', FALSE, 10),
                          ('equals()', TRUE, 20),
                          ('hashCode()', FALSE, 30),
                          ('instanceof', FALSE, 40)) AS v (content, is_correct, sort_order);

WITH q AS (
    INSERT INTO question (category_id, type, content, explanation, sort_order)
    SELECT id, 'MULTIPLE_CHOICE',
           'List.of(1, 2, 3)로 만든 리스트에 add(4)를 호출하면 어떻게 되는가?',
           'List.of는 수정할 수 없는 리스트를 반환한다. 컴파일은 되지만 실행 중 예외가 발생한다.',
           20
    FROM category WHERE slug = 'java'
    RETURNING id
)
INSERT INTO choice (question_id, content, is_correct, sort_order)
SELECT q.id, v.content, v.is_correct, v.sort_order
FROM q CROSS JOIN (VALUES ('4가 추가된다', FALSE, 10),
                          ('컴파일 오류가 난다', FALSE, 20),
                          ('NullPointerException이 발생한다', FALSE, 30),
                          ('UnsupportedOperationException이 발생한다', TRUE, 40)) AS v (content, is_correct, sort_order);

WITH q AS (
    INSERT INTO question (category_id, type, content, explanation, sort_order)
    SELECT id, 'MULTIPLE_CHOICE',
           'record에 대한 설명으로 옳은 것은?',
           'record는 불변 데이터를 담는 클래스로, 접근자·equals·hashCode·toString이 자동으로 생긴다. 인터페이스는 구현할 수 있지만 클래스는 상속할 수 없다.',
           30
    FROM category WHERE slug = 'java'
    RETURNING id
)
INSERT INTO choice (question_id, content, is_correct, sort_order)
SELECT q.id, v.content, v.is_correct, v.sort_order
FROM q CROSS JOIN (VALUES ('컴포넌트마다 접근자가 자동으로 생기고 필드는 final이다', TRUE, 10),
                          ('setter가 자동으로 생긴다', FALSE, 20),
                          ('다른 클래스를 상속할 수 있다', FALSE, 30),
                          ('인스턴스 필드를 추가로 선언할 수 있다', FALSE, 40)) AS v (content, is_correct, sort_order);

-- Spring
WITH q AS (
    INSERT INTO question (category_id, type, content, explanation, sort_order)
    SELECT id, 'MULTIPLE_CHOICE',
           '스프링 빈의 기본 스코프는?',
           '스코프를 지정하지 않으면 컨테이너마다 인스턴스를 하나만 만드는 singleton이 적용된다.',
           10
    FROM category WHERE slug = 'spring'
    RETURNING id
)
INSERT INTO choice (question_id, content, is_correct, sort_order)
SELECT q.id, v.content, v.is_correct, v.sort_order
FROM q CROSS JOIN (VALUES ('prototype', FALSE, 10),
                          ('request', FALSE, 20),
                          ('singleton', TRUE, 30),
                          ('session', FALSE, 40)) AS v (content, is_correct, sort_order);

WITH q AS (
    INSERT INTO question (category_id, type, content, explanation, sort_order)
    SELECT id, 'MULTIPLE_CHOICE',
           '@RestController가 @Controller와 다른 점은?',
           '@RestController는 @Controller에 @ResponseBody를 더한 것으로, 반환값을 뷰 이름이 아니라 응답 본문으로 직렬화한다.',
           20
    FROM category WHERE slug = 'spring'
    RETURNING id
)
INSERT INTO choice (question_id, content, is_correct, sort_order)
SELECT q.id, v.content, v.is_correct, v.sort_order
FROM q CROSS JOIN (VALUES ('메서드 반환값을 응답 본문으로 쓴다', TRUE, 10),
                          ('GET 요청만 처리한다', FALSE, 20),
                          ('트랜잭션을 자동으로 시작한다', FALSE, 30),
                          ('빈으로 등록되지 않는다', FALSE, 40)) AS v (content, is_correct, sort_order);

WITH q AS (
    INSERT INTO question (category_id, type, content, explanation, sort_order)
    SELECT id, 'MULTIPLE_CHOICE',
           '스프링 공식 문서가 필수 의존성에 권장하는 주입 방식은?',
           '생성자 주입은 의존성을 final로 둘 수 있고, 빠진 의존성이 객체 생성 시점에 드러나며, 테스트에서 직접 넘기기 쉽다.',
           30
    FROM category WHERE slug = 'spring'
    RETURNING id
)
INSERT INTO choice (question_id, content, is_correct, sort_order)
SELECT q.id, v.content, v.is_correct, v.sort_order
FROM q CROSS JOIN (VALUES ('필드 주입', FALSE, 10),
                          ('setter 주입', FALSE, 20),
                          ('정적 필드 주입', FALSE, 30),
                          ('생성자 주입', TRUE, 40)) AS v (content, is_correct, sort_order);

-- 데이터베이스
WITH q AS (
    INSERT INTO question (category_id, type, content, explanation, sort_order)
    SELECT id, 'MULTIPLE_CHOICE',
           '기본키(PRIMARY KEY) 컬럼의 특징으로 옳은 것은?',
           '기본키는 UNIQUE와 NOT NULL을 합친 제약으로, 각 행을 유일하게 식별한다.',
           10
    FROM category WHERE slug = 'database'
    RETURNING id
)
INSERT INTO choice (question_id, content, is_correct, sort_order)
SELECT q.id, v.content, v.is_correct, v.sort_order
FROM q CROSS JOIN (VALUES ('중복을 허용하지만 NULL은 허용하지 않는다', FALSE, 10),
                          ('중복과 NULL을 허용하지 않는다', TRUE, 20),
                          ('NULL을 허용하지만 중복은 허용하지 않는다', FALSE, 30),
                          ('중복과 NULL을 모두 허용한다', FALSE, 40)) AS v (content, is_correct, sort_order);

WITH q AS (
    INSERT INTO question (category_id, type, content, explanation, sort_order)
    SELECT id, 'MULTIPLE_CHOICE',
           '트랜잭션의 원자성(Atomicity)이 뜻하는 것은?',
           '원자성은 트랜잭션을 더 나눌 수 없는 단위로 다룬다는 뜻이다. 나머지 보기는 순서대로 격리성, 지속성, 일관성이다.',
           20
    FROM category WHERE slug = 'database'
    RETURNING id
)
INSERT INTO choice (question_id, content, is_correct, sort_order)
SELECT q.id, v.content, v.is_correct, v.sort_order
FROM q CROSS JOIN (VALUES ('동시에 실행되는 트랜잭션이 서로 간섭하지 않는다', FALSE, 10),
                          ('커밋한 결과는 장애가 나도 유지된다', FALSE, 20),
                          ('데이터가 항상 제약 조건을 만족한다', FALSE, 30),
                          ('작업이 모두 반영되거나 하나도 반영되지 않는다', TRUE, 40)) AS v (content, is_correct, sort_order);

WITH q AS (
    INSERT INTO question (category_id, type, content, explanation, sort_order)
    SELECT id, 'MULTIPLE_CHOICE',
           '두 테이블에서 조인 조건이 맞는 행만 결과에 남기는 조인은?',
           'INNER JOIN은 양쪽에 짝이 있는 행만 남긴다. LEFT OUTER JOIN은 왼쪽 행을 모두 남기고, CROSS JOIN은 모든 조합을 만든다.',
           30
    FROM category WHERE slug = 'database'
    RETURNING id
)
INSERT INTO choice (question_id, content, is_correct, sort_order)
SELECT q.id, v.content, v.is_correct, v.sort_order
FROM q CROSS JOIN (VALUES ('INNER JOIN', TRUE, 10),
                          ('LEFT OUTER JOIN', FALSE, 20),
                          ('FULL OUTER JOIN', FALSE, 30),
                          ('CROSS JOIN', FALSE, 40)) AS v (content, is_correct, sort_order);
