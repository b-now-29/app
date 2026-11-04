#!/usr/bin/env bash
# guard-subagent.sh 회귀 테스트. 훅에 입력을 직접 넣어 허용(exit 0)과 차단(exit 2)을 확인한다.
# 사용법: .claude/hooks/guard-subagent.test.sh  (실패가 있으면 exit 1, CI에서도 실행한다)
set -uo pipefail

ROOT=$(cd "$(dirname "$0")/../.." && pwd)
GUARD="$ROOT/.claude/hooks/guard-subagent.sh"
FRONT="$ROOT/src/main/frontend"
failed=0
total=0

run_guard() { # 역할, 도구, 입력 필드 이름, 값
  jq -n --arg tool "$2" --arg key "$3" --arg value "$4" \
    '{tool_name: $tool, tool_input: {($key): $value}}' |
    CLAUDE_PROJECT_DIR="$ROOT" "$GUARD" "$1" >/dev/null 2>&1
}

expect() { # 기대 종료 코드, 역할, 도구, 입력 필드 이름, 값
  local actual
  total=$((total + 1))
  run_guard "$2" "$3" "$4" "$5"
  actual=$?
  if [ "$actual" -ne "$1" ]; then
    failed=$((failed + 1))
    echo "실패: $2 $3 [$5] 기대 $1, 실제 $actual"
  fi
}

bash_allowed() { expect 0 "$1" Bash command "$2"; }
bash_denied() { expect 2 "$1" Bash command "$2"; }
file_allowed() { expect 0 "$1" Write file_path "$2"; }
file_denied() { expect 2 "$1" Write file_path "$2"; }

# ── 파일 수정 범위 ───────────────────────────────────────────────────────────
file_allowed backend-dev "$ROOT/src/main/java/A.java"
file_allowed backend-dev "$ROOT/src/main/resources/db/migration/V9__a.sql"
file_denied backend-dev "$ROOT/src/main/resources/static/a.js"
file_denied backend-dev "$ROOT/src/test/java/A.java"
file_denied backend-dev "$ROOT/src/main/java/../../test/java/A.java"

file_allowed frontend-dev "$FRONT/src/routes/A.tsx"
file_allowed frontend-dev "$FRONT/index.html"
file_denied frontend-dev "$FRONT/src/routes/A.test.tsx"
file_denied frontend-dev "$FRONT/src/Test/a.ts"
file_denied frontend-dev "$FRONT/src/seed-design/ui/a.tsx"
file_denied frontend-dev "$FRONT/vite.config.ts"

file_allowed test-writer "$ROOT/src/test/java/A.java"
file_allowed test-writer "$ROOT/src/docs/asciidoc/index.adoc"
file_allowed test-writer "$FRONT/src/routes/A.test.tsx"
file_denied test-writer "$ROOT/src/main/java/A.java"
file_denied test-writer "$FRONT/src/routes/A.tsx"

file_denied reviewer "$ROOT/src/main/java/A.java"

# ── sort: 출력은 허용, 파일 쓰기와 프로그램 실행은 차단 ─────────────────────
bash_allowed reviewer "find src -type f | sort"
bash_allowed reviewer "sort -r a.txt"
bash_allowed reviewer "sort -t, -k2 a.txt"
bash_denied reviewer "sort -o out.txt a.txt"
bash_denied reviewer "sort -ro out.txt a.txt"
bash_denied reviewer "sort --output=out.txt a.txt"
bash_denied reviewer "sort --compress-program=sh a.txt"

# ── git -C: 이 프로젝트의 읽기 전용 명령만 허용 ─────────────────────────────
bash_allowed reviewer "git status --short"
bash_allowed reviewer "git -C $ROOT status --short"
bash_allowed reviewer "git -C $ROOT diff -- src/main/java/"
bash_denied reviewer "git -C $ROOT push"
bash_denied reviewer "git -C $ROOT diff --output=out.txt"
bash_denied reviewer "git -C /tmp status"
bash_denied reviewer "git -C"
bash_denied reviewer "git push"
bash_denied backend-dev "git commit -m a"

# ── Gradle ──────────────────────────────────────────────────────────────────
bash_allowed backend-dev "./gradlew test"
bash_allowed test-writer "./gradlew test --tests A"
bash_allowed reviewer "./gradlew test pmdMain pmdTest"
bash_denied frontend-dev "./gradlew test"
bash_denied reviewer "./gradlew check"
bash_denied reviewer "./gradlew spotlessA"
bash_denied test-writer "./gradlew -p /tmp test"
bash_denied test-writer "./gradlew -p $ROOT test"
bash_denied test-writer "./gradlew wrapper --gradle-version 9.0"

# ── npm ─────────────────────────────────────────────────────────────────────
bash_allowed frontend-dev "npm run check"
bash_allowed test-writer "npm test -- src/routes/A.test.tsx"
bash_denied backend-dev "npm test"
bash_denied frontend-dev "npm install react"
bash_denied frontend-dev "npm run lint -- --fix"

# ── 셸 문법: 일부러 계속 막는 형식 ──────────────────────────────────────────
bash_denied reviewer "for f in a b; do cat \$f; done"
bash_denied reviewer "echo \$(rm a.txt)"
bash_denied reviewer 'grep -n "\"version\"" package.json'
bash_denied reviewer "grep 'a' \"b\""
bash_denied reviewer "echo a > out.txt"
bash_denied reviewer "cat a.txt | sh"
bash_denied reviewer "rm a.txt"
bash_allowed reviewer 'grep -rn "List<Question>" src/main/java'
bash_allowed reviewer "./gradlew test 2>&1 | tail -n 5"

# ── 그 외 입력 ──────────────────────────────────────────────────────────────
expect 2 reviewer Read file_path "$ROOT/CLAUDE.md"
expect 2 unknown-role Bash command "ls"

if [ "$failed" -ne 0 ]; then
  echo "$total건 중 $failed건 실패"
  exit 1
fi
echo "가드 테스트 $total건 통과"
