#!/usr/bin/env bash
# Stop: Claude가 응답을 끝내기 직전, 이번에 바뀐 Java 파일이 있으면 PMD를 돌린다.
# Gradle 린트는 파일 단위로 돌리기 어렵고 느려서 편집마다가 아니라 턴 끝에 한 번만 실행한다.
# exit 2 = 종료를 막고 stderr 내용을 Claude에게 전달 → Claude가 수정을 이어간다.
set -uo pipefail

INPUT=$(cat)

# 이미 한 번 막아서 Claude가 고치는 중이면 다시 막지 않는다 (무한 반복 방지)
if printf '%s' "$INPUT" | grep -q '"stop_hook_active"[[:space:]]*:[[:space:]]*true'; then
  exit 0
fi

ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$ROOT" || exit 0

# 바뀐(새 파일 포함) Java 파일이 없으면 건너뛴다
git status --porcelain -uall -- 'src/*.java' 2>/dev/null | grep -q . || exit 0

if ! OUT=$(./gradlew -q pmdMain pmdTest 2>&1); then
  echo "PMD 위반(또는 컴파일 오류)이 있습니다. 수정해 주세요:" >&2
  echo "$OUT" | tail -n 50 >&2
  exit 2
fi

exit 0
