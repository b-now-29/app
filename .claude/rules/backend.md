---
paths:
  - "src/main/java/**"
  - "src/main/resources/**"
  - "src/test/**"
  - "src/docs/**"
---

# 백엔드 규칙

## API

- 모든 API는 `/api/v1/` 아래에 둔다. 리소스는 복수형 명사 (`/categories`, `/questions`), 식별자는 path variable (`/categories/{slug}/questions`).
- 요청·응답은 JSON, 필드명은 camelCase. 날짜는 ISO-8601 (UTC).
- 컨트롤러는 엔티티를 반환하지 않는다. 요청은 `XxxRequest`, 응답은 `XxxResponse` record DTO로 만든다.
- 성공 응답은 래퍼 없이 본문을 그대로 반환한다 (목록은 JSON 배열).
- 에러 응답은 ProblemDetail (RFC 9457). 상태 코드: 입력 검증 실패 400, 인증 실패 401, 권한 없음 403, 없는 리소스 404, 중복 등 충돌 409. 예외는 `@RestControllerAdvice`에서 ProblemDetail로 변환한다. 보안 필터의 401·403은 advice까지 오지 않으므로 `AuthenticationEntryPoint`·`AccessDeniedHandler`가 같은 형식으로 쓴다.
- 인증은 HttpOnly 쿠키 `access_token`의 JWT로 한다. API는 기본 공개이고, 인증이 필요한 경로만 `SecurityConfig`에 명시한다. 잘못되거나 만료된 토큰은 거부하지 않고 비회원으로 처리한다. 비밀번호 해시와 토큰은 응답 본문에 넣지 않는다.
- 요청 검증은 Bean Validation (`@Valid`, `@NotNull` 등)으로 한다.
- 모든 API는 REST Docs 테스트로 문서화하고 `src/docs/asciidoc/index.adoc`에 추가한다. 문서 없는 API를 만들지 않는다.
- 응답 필드 추가는 괜찮지만, 삭제·이름 변경은 하지 않는다 (필요하면 `/api/v2`).

## 백엔드 개발 방법

- 패키지는 기능별로 나눈다 (`quiz/category`, `quiz/question`). 각 패키지 안에 Controller · Service · Repository · 엔티티 · DTO를 둔다.
- 계층: Controller (요청 검증·DTO 변환) → Service (비즈니스 로직·트랜잭션) → Repository. 컨트롤러에 로직을 넣지 않는다.
- 도메인 규칙 (생성 시 검증, 채점)은 Service가 아니라 엔티티/도메인 객체의 메서드에 둔다. Service는 조회 → 도메인 메서드 호출 → DTO 변환만 한다.
- Service는 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`.
- `open-in-view: false`이므로 엔티티 → DTO 변환은 Service 안 (트랜잭션 안)에서 끝낸다.
- 엔티티: setter를 두지 않고 생성자/정적 팩토리로 만든다. 연관관계는 `@ManyToOne(fetch = LAZY)`. 연관 데이터를 함께 쓰는 조회는 fetch join 또는 `@EntityGraph`로 N+1을 막는다.
- `TEXT` 컬럼에 매핑하는 `String`은 `@Column(columnDefinition = "text")` (`ddl-auto: validate` 통과용).
- DB 변경은 항상 새 Flyway 파일 (`V<n>__<설명>.sql`)로 한다. 이미 적용된 마이그레이션 파일은 수정하지 않는다.

## 코드 스타일

- 불변 우선: DTO·값 객체는 record. 엔티티는 setter 없이 의미 있는 메서드로만 상태를 바꾼다. 컬렉션을 밖으로 반환할 때는 불변으로 (`List.copyOf`).
- 도메인 로직은 데이터를 가진 객체가 처리한다 (`question.grade(answer)`). 유형별 분기 (`if (type == ...)`)를 Service 여기저기에 두지 않고, 한 곳 (enum 또는 `switch` 패턴 매칭)에 모은다.
- 변환·계산은 부수효과 없는 메서드로 만든다 (입력 → 출력, 외부 상태 변경 없음). stream은 for문보다 읽기 쉬울 때만 쓴다.
- 조회 결과가 없을 수 있으면 `Optional`을 반환한다. 파라미터와 필드에는 `Optional`을 쓰지 않는다.

## 테스트

- 도메인 (엔티티): 로직이 있는 메서드 (생성 검증, 채점)는 순수 JUnit 단위 테스트 (Spring·DB 없이). getter뿐인 엔티티는 테스트하지 않는다.
- Repository·쿼리·DB 제약·엔티티 매핑: `@DataJpaTest` + Testcontainers, `@Tag("integration")`.
- Service: 도메인에 위임만 하면 따로 테스트하지 않는다. 분기·조합 로직이 생기면 단위 테스트 (Repository는 mock).
- 컨트롤러: `@WebMvcTest` + REST Docs. Service는 `@MockitoBean`으로 대체한다.
