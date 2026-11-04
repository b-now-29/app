---
paths:
  - "deploy/**"
  - ".github/**"
  - "Dockerfile"
  - "compose.yaml"
  - "scripts/**"
---

# 배포·CI 규칙

CI/CD: GitHub Actions (`.github/workflows/ci-cd.yml`)가 `develop`·`main`으로 향하는 PR과 `develop`·`main` push마다 실행한다.

- PR·push: 서브에이전트 가드 테스트, `./gradlew check integrationTest`
- PR만: PR 제목 형식 검사, 기존 마이그레이션 파일 변경 차단 (`scripts/ci-check-*.sh`)
- `main`으로 향하는 PR만: `./gradlew bootJar -PwithDocs`. Asciidoctor 경고 (스니펫 누락 포함)도 실패로 처리한다
- `main` push: 검사를 통과하면 이미지를 빌드해 서버에 배포한다

- `main`에 push = 운영 배포 (`release/*`·`hotfix/*` 머지로만 반영). AWS Lightsail 인스턴스 1대 (Amazon Linux 2023, 사용자 `ec2-user`, 패키지는 `dnf`)에서 Docker Compose로 Caddy + Spring Boot + PostgreSQL 18 실행. 서버 설정 파일은 `deploy/`.
- Caddy는 HTTPS를 맡고, `/java-quiz/*`는 서버의 `~/app/static/java-quiz/`에서 직접 서빙하며 (저장소 밖 파일), 나머지 요청은 Spring Boot로 넘긴다. 프론트는 같은 도메인의 상대 경로 `/api/...`로 호출한다 (CORS 설정 불필요).
- 운영 비밀값은 서버의 `~/app/.env`에만 있다. 저장소에는 `deploy/.env.example`만 둔다.
- 이미지는 GHCR (`ghcr.io/b-now-29/app`)에 커밋 SHA 태그로 올라간다. 롤백은 서버에서 `APP_TAG=<이전 SHA> docker compose up -d`.
