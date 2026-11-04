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
- 백엔드 세부 규칙은 `.claude/rules/backend.md`, 프론트엔드 세부 규칙은 `.claude/rules/frontend.md`에 있다. 관련 파일을 읽거나 고칠 때 자동으로 불러오므로, 파일을 열기 전에 설계할 때는 직접 읽는다.

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

### TDD

- 도메인 로직과 버그 수정은 테스트 먼저: 실패하는 테스트 작성 → 실행해서 실패 확인 (red) → 통과하는 최소 구현 (green) → 테스트가 통과하는 상태로 리팩터링.
- 버그를 고칠 때는 그 버그를 재현하는 테스트부터 추가한다.
- 커밋은 테스트가 모두 통과하는 상태에서만 한다. red 상태로 커밋하지 않는다.
- Controller·설정·화면은 테스트 먼저를 강제하지 않는다 (구현 후 테스트 작성 가능).

### 기능 개발 순서

1. 메인 세션이 `docs/feature-template.md`를 바탕으로 `docs/features/<slug>.md` 초안을 쓰고 사용자 확인을 받는다. 열린 질문이 남아 있으면 구현을 시작하지 않는다.
2. 마이그레이션 + 먼저 쓰는 테스트: test-writer가 실패하는 테스트를 작성합니다.
3. 개발: backend-dev, frontend-dev가 코드를 작성합니다.
4. 구현 후 테스트: test-writer가 완성된 코드에 Controller·화면 테스트를 추가합니다.
5. 완료된 에이전트 작업 응답 부터 리뷰어가 확인
6. 통과 실패시 재개발, **-dev 에이전트 실행횟수 총 2회
7. 2회 이상 실패시 문제점 docs/report경로에 <기능 (backend-dev|frontend-dev)>.md 로 실패한 이유 또는 제약사항을 작성 후 알린다.
8. `./gradlew check` 통과 → `develop`으로 PR (제목은 Conventional Commits 형식) → squash merge

### 기능 문서

- `docs/features/<slug>.md`는 git으로 추적하지 않는 로컬 작업 문서다. PR을 만들 때 요구사항과 결정 요약을 PR 본문에 옮겨 squash 커밋에 남긴다.
- 문서는 메인 세션만 고친다. 에이전트 실행마다 진행 기록에 한 줄을 남기고, 재시도 횟수는 이 기록으로 센다.
- 에이전트에게는 문서의 정확한 경로를 넘긴다. 요구사항을 프롬프트에 다시 옮겨 적지 않는다. git이 무시하는 파일은 검색 결과에서 빠질 수 있으므로, 검색하지 않고 그 경로를 바로 읽는다.
- 결정이 바뀌면 코드보다 문서를 먼저 고친다.
- 머지하면 상태를 `완료`로 바꾸고 이후에는 갱신하지 않는다. 그 뒤로 현재 동작의 기준은 코드와 REST Docs다.
- 여러 기능에 적용되는 결정은 `.claude/rules/`로 옮긴다.

## 첫 버전 범위 (MVP)

| 포함                                               | 나중에                       |
|----------------------------------------------------|------------------------------|
| 카테고리 목록 보기                                 | 로그인 / 회원별 풀이 기록    |
| 카테고리를 고르면 그 카테고리의 문제를 하나씩 풀기 | 관리자 화면 (문제 등록·수정) |
| 객관식 정답 1개,                                   | 주관식, 서술형               |
| 제출하면 정답 여부 + 해설 표시, 마지막에 점수      | 랭킹, 오답 노트              |
