#!/usr/bin/env bash
# 로컬에서 배포 jar를 그대로 띄워 확인한다 (compose.yaml의 Postgres 사용)
# 사용법: scripts/run-jar.sh            → 빌드 후 실행
#         scripts/run-jar.sh --no-build → 이미 만든 jar로 바로 실행
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose up -d --wait postgres

if [[ "${1:-}" != "--no-build" ]]; then
  ./gradlew bootJar
fi

JAR=$(ls build/libs/app-*.jar | grep -v -- '-plain' | head -1)

SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mydatabase \
SPRING_DATASOURCE_USERNAME=myuser \
SPRING_DATASOURCE_PASSWORD=secret \
exec java -jar "$JAR"