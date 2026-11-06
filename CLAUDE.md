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

## Git

- Git Flow를 따른다. `main`(운영, push 시 배포)과 `develop`(통합)에는 직접 push하지 않고 PR로만 머지한다.
    - `feature/<요약>`: `develop`에서 분기 → `develop`으로 PR
    - `release/<x.y.z>`: `develop`에서 분기 → `build.gradle.kts`의 `version` 올림 → `main`으로 PR → 머지 후 `v<x.y.z>` 태그 →
      `develop`에 다시 머지
    - `hotfix/<x.y.z>`: `main`에서 분기 → `main`으로 PR → 태그 → `develop`에 다시 머지
    - `release/*`, `hotfix/*`를 머지할 때는 squash 하지 않고 merge commit으로 머지한다 (`main`과 `develop` 이력이 어긋나지 않도록)
- PR을 요청받으면 `mattpocock-skills:pr` 형식으로 제목과 본문을 쓰고, push 후 `gh pr create`까지 한다. 머지는 사용자가 하므로 PR 링크와 머지 방식을 함께 알린다.
    - `feature/*` → `develop` (squash merge)
    - `release/*`, `hotfix/*` → `main` (merge commit)
    - 그 외 브랜치는 PR을 만들지 않고 멈춰서 알린다
- `release/*`, `hotfix/*`가 `main`에 머지됐다고 알려 주면 다음을 한다.
    1. 그 머지 커밋에 `v<x.y.z>` 태그를 달고 push한다.
    2. `main` → `develop` 역머지 PR을 만든다 (`--head main --base develop`). merge commit으로 머지하며 squash하지 않는다.
    3. 충돌이 나면 `main`과 `develop`을 임의로 고치지 않고, 충돌 파일을 알린 뒤 `feature/backmerge-main-<x.y.z>` 브랜치에서 해결한다.
- 커밋 메시지: Conventional Commits, `type(scope): 제목` — type은 `feat|fix|refactor|test|docs|chore|build|ci`, scope는
  `backend|frontend|db|ci` 중 하나이며 버전 올림·배포 설정처럼 어디에도 속하지 않으면 생략한다. 예: `feat(backend): 회원 가입 API 추가`, `chore: 버전 0.1.2`
- 커밋 하나에 논리적 변경 하나. 백엔드와 프론트엔드 변경이 독립적이면 커밋을 나눈다.
- 마이그레이션 파일은 그 마이그레이션을 사용하는 코드와 같은 커밋에 넣는다.
- 내가 요청하기 전에는 commit/push 하지 않는다.
- `main`에 force push, `git reset --hard`, 히스토리 재작성 금지.
- `.env`, `application-local.yml`, 키·인증서 파일은 커밋하지 않는다. staged 목록에 있으면 멈추고 알린다.

## 규칙

- 운영 서버에 직접 SSH 명령을 실행하지 않는다. 필요하면 명령을 제안만 한다.
- 세부 규칙은 `.claude/rules/`의 `backend.md`(API 포함), `frontend.md`, `deploy.md`(CI 포함)에 있다. 관련 파일을 읽거나 고칠 때 자동으로 불러오므로, 파일을 열기 전에 설계할 때는 직접 읽는다.
- 기능 개발을 시작하기 전에 `docs/feature-workflow.md`를 읽는다.

### 퀴즈 도메인

- 문제 유형: `MULTIPLE_CHOICE`(서버 자동 채점), `SHORT_ANSWER`·`ESSAY`(모범 답안 공개 후 자기 채점).
- 정답 정보 (`choice.is_correct`, `question.model_answer`)는 조회 API로 내려주지 않는다. 답 제출 후 채점 API 응답으로만 공개한다.

### TDD

- 도메인 로직과 버그 수정은 테스트 먼저: 실패하는 테스트 작성 → 실행해서 실패 확인 (red) → 통과하는 최소 구현 (green) → 테스트가 통과하는 상태로 리팩터링.
- 버그를 고칠 때는 그 버그를 재현하는 테스트부터 추가한다.
- 커밋은 테스트가 모두 통과하는 상태에서만 한다. red 상태로 커밋하지 않는다.
- Controller·설정·화면은 테스트 먼저를 강제하지 않는다 (구현 후 테스트 작성 가능).

## 작업 이어 가기

다음 할 일은 `docs/roadmap.md`를 바로 읽는다 (없으면 사용자에게 묻는다). 진행 중인 작업은 현재 브랜치와 `git status`로 확인한다. 이어 갈 항목이 기능 개발이면 단계가 중간이어도 에이전트를 부르기 전에 `docs/feature-workflow.md`를 읽는다.

- GitHub Issue는 사용자가 요청할 때만 조회한다. 열린 Issue는 `앞으로 할 일`에 번호로 추가한다.
- 항목에는 우선순위 `[낮음]`·`[보통]`·`[높음]`을 붙인다. 사용자가 따로 말하지 않으면 `[보통]`이다.
- 작업을 시작하면 `이어서 할 일`로 옮기고, 세션을 끝낼 때 멈춘 지점 (브랜치, 남은 단계)을 한 줄로 적는다.
- 머지됐다고 알려 주면 `완료한 일`로 옮기고 PR 번호를 적는다. Issue 작업이면 `gh issue close`로 닫는다.
