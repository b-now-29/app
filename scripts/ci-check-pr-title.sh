#!/usr/bin/env bash
# PR 제목이 Conventional Commits 형식인지 검사한다. PR 제목은 그대로 머지 커밋 제목이 된다.
# 사용법: scripts/ci-check-pr-title.sh "<PR 제목>"
set -euo pipefail

title=${1:-}
pattern='^(feat|fix|refactor|test|docs|chore|build|ci)(\((backend|frontend|db|ci)\))?: .+'

if [[ ! "$title" =~ $pattern ]]; then
  echo "PR 제목이 형식에 맞지 않습니다: $title" >&2
  echo "형식: type(scope): 제목 (예: feat(backend): 회원 가입 API 추가, chore: 버전 0.1.2)" >&2
  echo "type: feat, fix, refactor, test, docs, chore, build, ci / scope: backend, frontend, db, ci (생략 가능)" >&2
  exit 1
fi
