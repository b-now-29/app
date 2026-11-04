# CLAUDE.md

카테고리별로 객관식·주관식·서술형 문제를 골라 푸는 퀴즈 앱. 개인 프로젝트.

## 구조

Spring Boot 4 (Java 25, Gradle) 단일 프로젝트. React 화면까지 jar 하나로 빌드한다.

- `src/main/java/` — 백엔드. API는 모두 `/api/` 아래에 매핑한다. 화면은 전부 React가 그리며 서버 템플릿 (Thymeleaf 등)은 쓰지 않는다 → 컨트롤러는 `@RestController`로 JSON만 반환한다
- `src/main/frontend/` — React + TypeScript, React Router v8 (Data mode, `createBrowserRouter`). import는 `react-router` 에서 (`react-router-dom` 아님)
- 프론트 빌드 결과 (`dist/`)는 `bootJar` 때 jar의 `static/`으로 들어가고 Spring이 서빙한다. `SpaConfig`가 없는 경로를 `index.html`로 돌려준다 (`/api/`로 시작하거나 `.`이 포함된 경로는 제외, 404). `src/main/resources/static/`에 직접 파일을 두지 않는다.
- DB: PostgreSQL 18, 마이그레이션은 Flyway (`src/main/resources/db/migration`)

## 명령어

```bash
# 프로젝트 루트
./gradlew bootRun            # API만 :8080 (프론트는 빌드하지 않음)
./gradlew test               # 단위 테스트
./gradlew integrationTest    # Testcontainers — OrbStack이 실행 중이어야 함
./gradlew check              # spotless + PMD + 단위 테스트 (+ 프론트 typecheck/lint/format 검사·테스트)
./gradlew bootJar            # 배포 jar (테스트·REST Docs 생략, 빠름)
./gradlew bootJar -PwithDocs # 배포용: 테스트 → REST Docs 생성 → jar의 /docs/index.html 에 포함

# 프론트 개발 (src/main/frontend/) — bootRun과 함께 띄움, /api는 :8080으로 프록시
npm run dev
npm run check                # typecheck + lint + format 검사 + 테스트

# 배포 jar 로컬 확인 — compose.yaml의 Postgres로 실행 (OrbStack 필요)
scripts/local-run-jar.sh            # bootJar 빌드 후 실행 → http://localhost:8080
scripts/local-run-jar.sh --no-build # 이미 빌드한 jar로 바로 실행
```

CI/CD: GitHub Actions (`.github/workflows/ci-cd.yml`)가 `develop`·`main`으로 향하는 PR과 `develop`·`main` push마다 `./gradlew check integrationTest`를 실행하고, `main` push면 이어서 이미지를 빌드해 서버에 배포한다.

## 배포

- `main`에 push = 운영 배포 (`release/*`·`hotfix/*` 머지로만 반영). AWS Lightsail 인스턴스 1대 (Amazon Linux 2023, 사용자 `ec2-user`, 패키지는 `dnf`)에서 Docker Compose로 Caddy + Spring Boot + PostgreSQL 18 실행. 서버 설정 파일은 `deploy/`.
- Caddy는 HTTPS를 맡고, `/java-quiz/*`는 서버의 `~/app/static/java-quiz/`에서 직접 서빙하며 (저장소 밖 파일), 나머지 요청은 Spring Boot로 넘긴다. 프론트는 같은 도메인의 상대 경로 `/api/...`로 호출한다 (CORS 설정 불필요).
- 운영 비밀값은 서버의 `~/app/.env`에만 있다. 저장소에는 `deploy/.env.example`만 둔다.
- 운영 서버에 직접 SSH 명령을 실행하지 않는다. 필요하면 명령을 제안만 한다.
- 이미지는 GHCR (`ghcr.io/b-now-29/app`)에 커밋 SHA 태그로 올라간다. 롤백은 서버에서 `APP_TAG=<이전 SHA> docker compose up -d`.

## Git

- Git Flow를 따른다. `main`(운영, push 시 배포)과 `develop`(통합)에는 직접 push하지 않고 PR로만 머지한다.
    - `feature/<요약>`: `develop`에서 분기 → `develop`으로 PR
    - `release/<x.y.z>`: `develop`에서 분기 → `build.gradle.kts`의 `version` 올림 → `main`으로 PR → 머지 후 `v<x.y.z>` 태그 →
      `develop`에 다시 머지
    - `hotfix/<x.y.z>`: `main`에서 분기 → `main`으로 PR → 태그 → `develop`에 다시 머지
    - `release/*`, `hotfix/*`를 머지할 때는 squash 하지 않고 merge commit으로 머지한다 (`main`과 `develop` 이력이 어긋나지 않도록)
- 커밋 메시지: Conventional Commits, `type(scope): 제목` — type은 `feat|fix|refactor|test|docs|chore|build|ci`, scope는
  `backend|frontend|db|ci` 중 하나이며 버전 올림·배포 설정처럼 어디에도 속하지 않으면 생략한다. 예: `feat(backend): 회원 가입 API 추가`, `chore: 버전 0.1.2`
- 커밋 하나에 논리적 변경 하나. 백엔드와 프론트엔드 변경이 독립적이면 커밋을 나눈다.
- 마이그레이션 파일은 그 마이그레이션을 사용하는 코드와 같은 커밋에 넣는다.
- 내가 요청하기 전에는 commit/push 하지 않는다.
- `main`에 force push, `git reset --hard`, 히스토리 재작성 금지.
- `.env`, `application-local.yml`, 키·인증서 파일은 커밋하지 않는다. staged 목록에 있으면 멈추고 알린다.

## 규칙

- 이미 적용된 마이그레이션 파일은 수정하지 말고 새 버전 파일을 추가한다.

### API

- 모든 API는 `/api/v1/` 아래에 둔다. 리소스는 복수형 명사 (`/categories`, `/questions`), 식별자는 path variable (`/categories/{slug}/questions`).
- 요청·응답은 JSON, 필드명은 camelCase. 날짜는 ISO-8601 (UTC).
- 컨트롤러는 엔티티를 반환하지 않는다. 요청은 `XxxRequest`, 응답은 `XxxResponse` record DTO로 만든다.
- 성공 응답은 래퍼 없이 본문을 그대로 반환한다 (목록은 JSON 배열).
- 에러 응답은 ProblemDetail (RFC 9457). 상태 코드: 입력 검증 실패 400, 없는 리소스 404. 예외는 `@RestControllerAdvice`에서 ProblemDetail로 변환한다.
- 요청 검증은 Bean Validation (`@Valid`, `@NotNull` 등)으로 한다.
- 모든 API는 REST Docs 테스트로 문서화하고 `src/docs/asciidoc/index.adoc`에 추가한다. 문서 없는 API를 만들지 않는다.
- 응답 필드 추가는 괜찮지만, 삭제·이름 변경은 하지 않는다 (필요하면 `/api/v2`).

### 퀴즈 도메인

- 문제 유형: `MULTIPLE_CHOICE`(서버 자동 채점), `SHORT_ANSWER`·`ESSAY`(모범 답안 공개 후 자기 채점).
- 정답 정보 (`choice.is_correct`, `question.model_answer`)는 조회 API로 내려주지 않는다. 답 제출 후 채점 API 응답으로만 공개한다.

### 백엔드 개발 방법

- 패키지는 기능별로 나눈다 (`quiz/category`, `quiz/question`). 각 패키지 안에 Controller · Service · Repository · 엔티티 · DTO를 둔다.
- 계층: Controller (요청 검증·DTO 변환) → Service (비즈니스 로직·트랜잭션) → Repository. 컨트롤러에 로직을 넣지 않는다.
- 도메인 규칙 (생성 시 검증, 채점)은 Service가 아니라 엔티티/도메인 객체의 메서드에 둔다. Service는 조회 → 도메인 메서드 호출 → DTO 변환만 한다.
- Service는 클래스에 `@Transactional(readOnly = true)`, 쓰기 메서드에만 `@Transactional`.
- `open-in-view: false`이므로 엔티티 → DTO 변환은 Service 안 (트랜잭션 안)에서 끝낸다.
- 엔티티: setter를 두지 않고 생성자/정적 팩토리로 만든다. 연관관계는 `@ManyToOne(fetch = LAZY)`. 연관 데이터를 함께 쓰는 조회는 fetch join 또는 `@EntityGraph`로 N+1을 막는다.
- `TEXT` 컬럼에 매핑하는 `String`은 `@Column(columnDefinition = "text")` (`ddl-auto: validate` 통과용).
- DB 변경은 항상 새 Flyway 파일 (`V<n>__<설명>.sql`)로 한다.

### 코드 스타일

- 불변 우선: DTO·값 객체는 record. 엔티티는 setter 없이 의미 있는 메서드로만 상태를 바꾼다. 컬렉션을 밖으로 반환할 때는 불변으로 (`List.copyOf`).
- 도메인 로직은 데이터를 가진 객체가 처리한다 (`question.grade(answer)`). 유형별 분기 (`if (type == ...)`)를 Service 여기저기에 두지 않고, 한 곳 (enum 또는 `switch` 패턴 매칭)에 모은다.
- 변환·계산은 부수효과 없는 메서드로 만든다 (입력 → 출력, 외부 상태 변경 없음). stream은 for문보다 읽기 쉬울 때만 쓴다.
- 조회 결과가 없을 수 있으면 `Optional`을 반환한다. 파라미터와 필드에는 `Optional`을 쓰지 않는다.

### TDD

- 도메인 로직과 버그 수정은 테스트 먼저: 실패하는 테스트 작성 → 실행해서 실패 확인 (red) → 통과하는 최소 구현 (green) → 테스트가 통과하는 상태로 리팩터링.
- 버그를 고칠 때는 그 버그를 재현하는 테스트부터 추가한다.
- 커밋은 테스트가 모두 통과하는 상태에서만 한다. red 상태로 커밋하지 않는다.
- Controller·설정·화면은 테스트 먼저를 강제하지 않는다 (구현 후 테스트 작성 가능).

### 테스트

- 도메인 (엔티티): 로직이 있는 메서드 (생성 검증, 채점)는 순수 JUnit 단위 테스트 (Spring·DB 없이). getter뿐인 엔티티는 테스트하지 않는다.
- Repository·쿼리·DB 제약·엔티티 매핑: `@DataJpaTest` + Testcontainers, `@Tag("integration")`.
- Service: 도메인에 위임만 하면 따로 테스트하지 않는다. 분기·조합 로직이 생기면 단위 테스트 (Repository는 mock).
- 컨트롤러: `@WebMvcTest` + REST Docs. Service는 `@MockitoBean`으로 대체한다.

### 프론트엔드 구조

- `src/api/` — API 호출 함수와 응답 타입. 타입 이름·필드는 백엔드 DTO와 맞춘다. 컴포넌트에서 `fetch`를 직접 쓰지 않는다.
- `src/quiz/` — 풀이 흐름 상태 (현재 문제, 제출 여부, 자기 채점 결과, 점수 집계)를 다루는 reducer. React를 import하지 않는 순수 TypeScript. 채점·정답 판단 같은 도메인 규칙은 프론트에 두지 않는다 (서버 책임).
- `src/components/` — 재사용 UI 컴포넌트 (보기 목록, 유형별 답 입력칸 등).
- `src/routes/` — 화면 + `loader`/`action`. 에러 화면은 route의 `ErrorBoundary`.
- `src/seed-design/` — SEED Design CLI가 생성한 코드 (린트·포맷 제외). 직접 고치지 않고, 추가는 `npx @seed-design/cli@latest add ui:<이름>`. 화면은 SEED 컴포넌트를 먼저 쓰고, 없을 때만 CSS를 직접 작성한다. 당근 로고·이름·캐릭터, 당근 고유 컴포넌트 (`manner-temp` 등)는 쓰지 않는다.
- 서버 데이터는 `loader`, 화면 상태는 `useState`. 여러 값이 같이 바뀌는 상태 (풀이 진행)는 `useReducer` + `src/quiz/`의 reducer. 별도 상태 관리 라이브러리는 쓰지 않는다.

### 프론트엔드 코드 스타일

- `any` 금지. 모르는 값은 `unknown`으로 받고 타입을 좁혀서 쓴다.
- 불변: state·props를 직접 수정하지 않는다 (spread, `map`, `filter`로 새 값을 만든다). 도메인 타입은 `readonly`.
- 컴포넌트는 props → 화면만 담당한다. 계산 로직은 `src/quiz/` 함수로 빼서 테스트한다.
- 문제 유형 분기는 union 타입 (`type: 'MULTIPLE_CHOICE' | 'SHORT_ANSWER' | 'ESSAY'`) + `switch` 한 곳에서 하고, `default`에서 `never` 검사로 빠진 유형이 없게 한다.
- 함수 컴포넌트 + hooks만 쓴다.

### 프론트엔드 테스트

- Vitest + React Testing Library. 테스트 파일은 대상 파일 옆에 `*.test.ts(x)`.
- 풀이 흐름 reducer (`src/quiz/`): 상태 전이마다 단위 테스트. TDD 대상.
- 컴포넌트: 사용자 관점으로 테스트한다 (`getByRole`, `getByLabelText`로 찾고 `user-event`로 조작). 내부 state나 CSS class는 검사하지 않는다. 핵심 상호작용 (답 선택·제출·결과 표시)만.
- API 호출은 `vi.mock`으로 대체한다.
- TDD 규칙은 백엔드와 같다: 도메인 로직과 버그 수정은 실패 테스트 먼저 (red 확인 → green → 리팩터링). 화면은 강제하지 않는다.

### 기능 개발 순서

1. 마이그레이션 + 엔티티 + 도메인 단위 테스트
2. Repository 통합 테스트
3. Service
4. Controller + REST Docs 테스트
5. 프론트 (`src/api/` → `src/routes/`)
6. `./gradlew check` 통과 → `develop`으로 PR (제목은 Conventional Commits 형식) → squash merge

## 첫 버전 범위 (MVP)

| 포함                                               | 나중에                       |
|----------------------------------------------------|------------------------------|
| 카테고리 목록 보기                                 | 로그인 / 회원별 풀이 기록    |
| 카테고리를 고르면 그 카테고리의 문제를 하나씩 풀기 | 관리자 화면 (문제 등록·수정) |
| 객관식 정답 1개,                                   | 주관식, 서술형               |
| 제출하면 정답 여부 + 해설 표시, 마지막에 점수      | 랭킹, 오답 노트              |
