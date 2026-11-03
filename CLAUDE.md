# CLAUDE.md

<한 줄 소개: 무엇을 하는 앱인지>. 개인 프로젝트.

## 구조

Spring Boot 4 (Java 25, Gradle) 단일 프로젝트. 최종 목표는 React 화면까지 jar 하나로 빌드하는 것이다.

> **현재 상태**: 아래에 **(예정)** 으로 표시한 것은 아직 저장소에 없다. 없는 파일을 찾거나 이미 있다고 가정하지 말고, 해당 작업을 할 때 이 문서의 설명대로 만든다.
> 아직 없는 것: `src/main/frontend/`, `SpaConfig`, `src/main/resources/db/migration/`, `.github/workflows/ci-cd.yml`, `deploy/`, `develop` 브랜치

- `src/main/java/` — 백엔드. API는 모두 `/api/` 아래에 매핑한다. 화면은 전부 React가 그리며 서버 템플릿(Thymeleaf 등)은 쓰지 않는다 → 컨트롤러는 `@RestController`로 JSON만 반환한다
- `src/main/frontend/` **(예정)** — React + TypeScript, React Router v7 (Data mode, `createBrowserRouter`). import는 `react-router`에서 (`react-router-dom` 아님)
- 프론트 빌드 결과(`dist/`)는 `bootJar` 때 jar의 `static/`으로 들어가고 Spring이 서빙한다. `SpaConfig` **(예정)** 가 `/api` 외의 없는 경로를 `index.html`로 돌려준다. `src/main/resources/static/`에 직접 파일을 두지 않는다.
- DB: PostgreSQL 18, 마이그레이션은 <Flyway|Liquibase> (`<마이그레이션 폴더 경로>`)

## 명령어

```bash
# 프로젝트 루트
./gradlew bootRun            # API만 :8080 (프론트는 빌드하지 않음)
./gradlew test               # 단위 테스트
./gradlew integrationTest    # Testcontainers — OrbStack이 실행 중이어야 함
./gradlew check              # spotless + PMD + 단위 테스트 (+ 프론트 typecheck/lint: 예정)
./gradlew bootJar            # 배포 jar (테스트·REST Docs 생략, 빠름)
./gradlew bootJar -PwithDocs # 배포용: 테스트 → REST Docs 생성 → jar의 /docs/index.html 에 포함

# 프론트 개발 (src/main/frontend/, 예정) — bootRun과 함께 띄움, /api는 :8080으로 프록시
npm run dev
npm run check                # typecheck + lint
```

CI **(예정)**: GitHub Actions(`.github/workflows/ci-cd.yml`)가 `./gradlew check integrationTest`를 실행한다. push 전에 로컬에서 같은 명령을 통과시킨다.

## 배포 (예정 — `deploy/`와 CI/CD 파이프라인은 아직 없음)

- `main`에 push = 운영 배포 (`release/*`·`hotfix/*` 머지로만 반영). AWS Lightsail 인스턴스 1대(Amazon Linux 2023, 사용자 `ec2-user`, 패키지는 `dnf`)에서 Docker Compose로 Caddy + Spring Boot + PostgreSQL 18 실행. 서버 설정 파일은 `deploy/`.
- Caddy는 HTTPS만 맡고 모든 요청을 Spring Boot로 넘긴다. 프론트는 같은 도메인의 상대 경로 `/api/...`로 호출한다 (CORS 설정 불필요).
- 운영 비밀값은 서버의 `~/app/.env`에만 있다. 저장소에는 `deploy/.env.example`만 둔다.
- 운영 서버에 직접 SSH 명령을 실행하지 않는다. 필요하면 명령을 제안만 한다.

## Git

- Git Flow를 따른다. `main`(운영, push 시 배포)과 `develop`(통합, **아직 없음** — 첫 `feature/*` 작업 전에 `main`에서 만든다)에는 직접 push하지 않고 PR로만 머지한다.
  - `feature/<요약>`: `develop`에서 분기 → `develop`으로 PR
  - `release/<x.y.z>`: `develop`에서 분기 → `build.gradle.kts`의 `version` 올림 → `main`으로 PR → 머지 후 `v<x.y.z>` 태그 → `develop`에 다시 머지
  - `hotfix/<x.y.z>`: `main`에서 분기 → `main`으로 PR → 태그 → `develop`에 다시 머지
  - `release/*`, `hotfix/*`를 머지할 때는 squash 하지 않고 merge commit으로 머지한다 (`main`과 `develop` 이력이 어긋나지 않도록)
- 커밋 메시지: Conventional Commits, `type(scope): 제목` — type은 `feat|fix|refactor|test|docs|chore|build|ci`, scope는 `backend|frontend|db|ci`. 예: `feat(backend): 회원 가입 API 추가`
- 커밋 하나에 논리적 변경 하나. 백엔드와 프론트엔드 변경이 독립적이면 커밋을 나눈다.
- 마이그레이션 파일은 그 마이그레이션을 사용하는 코드와 같은 커밋에 넣는다.
- 내가 요청하기 전에는 commit/push 하지 않는다.
- `main`에 force push, `git reset --hard`, 히스토리 재작성 금지.
- `.env`, `application-local.yml`, 키·인증서 파일은 커밋하지 않는다. staged 목록에 있으면 멈추고 알린다.

## 규칙

- 이미 적용된 마이그레이션 파일은 수정하지 말고 새 버전 파일을 추가한다.
- <API 응답 형식, 패키지 구조 등 코드만 봐선 모르는 규칙이 있으면 추가>
