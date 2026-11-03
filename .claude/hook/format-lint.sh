#!/usr/bin/env bash
# PostToolUse (Edit|Write): Claude가 수정한 파일 하나만 포맷 + 린트한다.
#   src/main/frontend/*.ts(x), *.js(x) → Prettier → ESLint --fix (남은 오류는 Claude에게 전달)
#   src/**/*.java                      → Spotless (해당 파일만)
# exit 2 = stderr 내용을 Claude에게 피드백으로 전달 (파일 수정 자체는 이미 끝난 상태)
set -uo pipefail

INPUT=$(cat)
if command -v jq >/dev/null 2>&1; then
  FILE=$(printf '%s' "$INPUT" | jq -r '.tool_input.file_path // empty')
else
  FILE=$(printf '%s' "$INPUT" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
fi
[ -n "$FILE" ] && [ -f "$FILE" ] || exit 0

ROOT="${CLAUDE_PROJECT_DIR:-$(pwd)}"
FRONT="$ROOT/src/main/frontend"

case "$FILE" in
  "$FRONT"/node_modules/* | "$FRONT"/dist/*)
    ;;
  "$FRONT"/*.ts | "$FRONT"/*.tsx | "$FRONT"/*.js | "$FRONT"/*.jsx)
    cd "$FRONT" || exit 0
    npx --no-install prettier --write "$FILE" >/dev/null 2>&1
    if ! OUT=$(npx --no-install eslint --fix "$FILE" 2>&1); then
      echo "ESLint가 자동으로 고치지 못한 문제가 있습니다 ($FILE):" >&2
      echo "$OUT" >&2
      exit 2
    fi
    ;;
  "$ROOT"/src/*.java)
    cd "$ROOT" || exit 0
    if ! OUT=$(./gradlew -q spotlessApply -PspotlessIdeHook="$FILE" 2>&1); then
      echo "Spotless 포맷 실패 — 문법 오류일 가능성이 큽니다 ($FILE):" >&2
      echo "$OUT" | tail -n 30 >&2
      exit 2
    fi
    ;;
esac

exit 0
