#!/usr/bin/env bash
# 기존 Flyway 마이그레이션 파일의 수정·삭제·이름 변경을 막는다. 새 파일 추가만 허용한다.
# 테스트는 빈 DB에서 시작하므로 체크섬 불일치를 잡지 못하고, 운영 서버 시작 때 처음 실패한다.
# 사용법: scripts/ci-check-migrations.sh <기준 커밋> <대상 커밋>
set -euo pipefail

base=${1:?기준 커밋이 필요합니다}
head=${2:?대상 커밋이 필요합니다}
dir=src/main/resources/db/migration

# --no-renames: 이름 변경을 삭제 + 추가로 보여 줘서 삭제 쪽에서 걸리게 한다.
changes=$(git diff --name-status --no-renames "$base" "$head" -- "$dir")
violations=$(printf '%s\n' "$changes" | grep -Ev '^(A[[:space:]]|$)' || true)

if [ -n "$violations" ]; then
  echo "기존 마이그레이션 파일을 바꿀 수 없습니다. 새 버전 파일을 추가하세요:" >&2
  echo "$violations" >&2
  exit 1
fi
