-- 카테고리별 주관식 시드. 기존 객관식(sort_order 10~30) 뒤에 나오도록 40으로 둔다.

INSERT INTO question (category_id, type, content, model_answer, explanation, sort_order)
SELECT id, 'SHORT_ANSWER',
       'Java에서 equals()를 재정의할 때 hashCode()도 함께 재정의해야 하는 이유는?',
       'equals()가 같다고 판단하는 두 객체는 hashCode()도 같아야 하기 때문이다. 그렇지 않으면 HashMap, HashSet 같은 해시 기반 컬렉션에서 같은 객체를 찾지 못한다.',
       '해시 기반 컬렉션은 hashCode()로 저장 위치를 정한 뒤 equals()로 비교한다. 두 메서드의 규약이 어긋나면 같은 값인데도 다른 위치에 저장된다.',
       40
FROM category WHERE slug = 'java';

INSERT INTO question (category_id, type, content, model_answer, explanation, sort_order)
SELECT id, 'SHORT_ANSWER',
       '스프링의 의존성 주입(DI)이 무엇이고 왜 쓰는지 설명하세요.',
       '객체가 필요한 의존 객체를 직접 만들지 않고 컨테이너가 밖에서 넣어 주는 방식이다. 객체 사이의 결합도가 낮아지고 테스트에서 대체 객체를 넣기 쉽다.',
       '의존 객체를 직접 new 하면 구현 클래스에 묶인다. 컨테이너가 주입하면 구현을 바꾸거나 테스트용 객체로 교체하기 쉽다.',
       40
FROM category WHERE slug = 'spring';

INSERT INTO question (category_id, type, content, model_answer, explanation, sort_order)
SELECT id, 'SHORT_ANSWER',
       '데이터베이스에서 인덱스를 만드는 이유와 단점을 설명하세요.',
       '조회 속도를 높이기 위해 만든다. 단점은 쓰기 작업이 느려지고 저장 공간을 더 쓴다는 점이다.',
       '인덱스는 정렬된 별도 구조라서 검색은 빨라지지만, 행을 추가하거나 수정할 때 인덱스도 함께 갱신해야 한다.',
       40
FROM category WHERE slug = 'database';
