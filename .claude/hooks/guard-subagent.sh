#!/usr/bin/env bash
# PreToolUse (역할별 서브에이전트 전용): 담당 범위 밖의 파일 수정과 명령을 막는다.
# 사용법: guard-subagent.sh <backend-dev|frontend-dev|test-writer|reviewer>
#   Edit|Write → 역할별 허용 경로 안의 파일만 허용
#     backend-dev:  src/main/java/, src/main/resources/ (static/ 제외)
#     frontend-dev: 프론트의 src/ (테스트, src/seed-design/ 제외), public/, index.html
#     test-writer:  src/test/, src/docs/asciidoc/, 프론트의 *.test.ts(x), src/main/frontend/src/test/
#     reviewer:     없음 (읽기 전용)
#   Bash       → 읽기 전용 명령과 역할별 실행 명령만 허용 (파일 수정은 Edit|Write로만)
#     backend-dev:  ./gradlew
#     frontend-dev: 프론트 테스트·검사용 npm
#     test-writer:  ./gradlew, 프론트 테스트·검사용 npm
#     reviewer:     ./gradlew test·integrationTest·pmdMain·pmdTest, 프론트 테스트·검사용 npm
# exit 2 = 도구 호출을 막고 stderr 내용을 서브에이전트에게 전달한다.
# exit 1 같은 다른 오류로는 호출이 막히지 않으므로, 정상 판정이 아니면 모두 exit 2로 바꾼다.
# 실수와 지름길을 막는 장치이며 샌드박스는 아니다 (코드를 실행하면 그 코드는 제한받지 않는다).
set -uo pipefail

ROLE=${1:-}
finished=false
trap '[ "$finished" = true ] || { echo "$ROLE 가드 오류로 차단했습니다. 메인 세션에 보고하세요." >&2; exit 2; }' EXIT

deny() {
  echo "$ROLE 범위 밖이라 차단했습니다: $1" >&2
  echo "우회하지 말고 메인 세션에 보고하세요." >&2
  finished=true
  exit 2
}

allow() {
  finished=true
  exit 0
}

check_backend_file() {
  case "$1" in
    "$ROOT"/src/main/resources/static/*)
      deny "static/에는 파일을 직접 두지 않습니다 ($1)" ;;
    "$ROOT"/src/main/java/* | "$ROOT"/src/main/resources/*)
      allow ;;
    *)
      deny "수정할 수 있는 곳은 src/main/java/, src/main/resources/뿐입니다 ($1)" ;;
  esac
}

check_frontend_file() {
  # 넓게 허용하고 예외를 빼는 규칙이라, src/Test/처럼 대소문자만 다른 경로로 예외를 피하지 못하게 한다.
  shopt -s nocasematch
  local front="$ROOT/src/main/frontend"
  case "$1" in
    "$front"/src/test/* | "$front"/*.test.* | "$front"/*.spec.* | "$front"/*/__tests__/* | "$front"/*/__snapshots__/*)
      deny "테스트 파일은 test-writer가 맡습니다 ($1)" ;;
    "$front"/src/seed-design/*)
      deny "src/seed-design/은 CLI가 생성합니다. 필요한 컴포넌트를 보고하세요 ($1)" ;;
    "$front"/src/* | "$front"/public/* | "$front"/index.html)
      allow ;;
    *)
      deny "수정할 수 있는 곳은 프론트의 src/, public/, index.html뿐입니다 ($1)" ;;
  esac
}

check_test_file() {
  case "$1" in
    "$ROOT"/src/main/frontend/node_modules/* | "$ROOT"/src/main/frontend/dist/*)
      deny "의존성과 빌드 결과는 수정할 수 없습니다 ($1)" ;;
    "$ROOT"/src/test/* | "$ROOT"/src/docs/asciidoc/* | "$ROOT"/src/main/frontend/src/test/*)
      allow ;;
    "$ROOT"/src/main/frontend/*.test.ts | "$ROOT"/src/main/frontend/*.test.tsx)
      allow ;;
    *)
      deny "수정할 수 있는 곳은 src/test/, src/docs/asciidoc/, 프론트 테스트 파일뿐입니다 ($1)" ;;
  esac
}

check_file() {
  case "$1" in
    */../* | */./* | *//*) deny "경로가 정규화되어 있지 않습니다 ($1)" ;;
  esac
  case "$ROLE" in
    backend-dev) check_backend_file "$1" ;;
    frontend-dev) check_frontend_file "$1" ;;
    test-writer) check_test_file "$1" ;;
    reviewer) deny "리뷰어는 파일을 수정하지 않습니다 ($1)" ;;
  esac
}

# 로컬에서는 spotlessJavaCheck가 spotlessJavaApply에 의존해 (build.gradle.kts) check도 소스를 고친다.
# Gradle은 spotlessA 같은 줄임말도 받으므로 금지 목록이 아니라 허용 목록으로 검사한다.
check_reviewer_gradle() {
  local word after_tests=false
  for word in "$@"; do
    if [ "$after_tests" = true ]; then
      after_tests=false
      continue
    fi
    case "$word" in
      test | integrationTest | pmdMain | pmdTest) ;;
      --tests) after_tests=true ;;
      -*) ;;
      *) deny "소스를 고치지 않는 Gradle 작업만 쓸 수 있습니다 ($word)" ;;
    esac
  done
}

check_npm() {
  [ "$ROLE" != backend-dev ] || deny "backend-dev는 npm 명령을 쓸 수 없습니다"
  case "$1 $2" in
    'test '* | 'run test' | 'run typecheck' | 'run lint' | 'run format:check' | 'run check') ;;
    *) deny "프론트 테스트·검사용 npm 명령만 쓸 수 있습니다 (npm $1 $2)" ;;
  esac
  case "$3" in
    *' --fix'* | *' --write'* | *' -w'*) deny "파일을 고치는 옵션은 쓸 수 없습니다" ;;
  esac
}

check_segment() {
  local words args
  read -r -a words <<< "$1"
  args=" ${words[*]:1} "
  case "${words[0]}" in
    cd | ls | cat | head | tail | wc | grep | pwd | echo | diff)
      ;;
    rg)
      case "$args" in
        *' --pre'*) deny "rg --pre는 다른 명령을 실행합니다" ;;
      esac ;;
    find)
      case "$args" in
        *' -delete '* | *' -exec'* | *' -ok'* | *' -fprint'* | *' -fls '*)
          deny "find의 삭제·실행·파일 출력 옵션은 쓸 수 없습니다" ;;
      esac ;;
    git)
      case "${words[1]:-}" in
        status | diff | log | show | blame | ls-files | rev-parse) ;;
        *) deny "읽기 전용 git 명령만 쓸 수 있습니다 (git ${words[1]:-})" ;;
      esac
      case "$args" in
        *' --output'*) deny "git --output은 파일을 씁니다" ;;
      esac ;;
    ./gradlew | "$ROOT"/gradlew)
      [ "$ROLE" != frontend-dev ] || deny "frontend-dev는 ./gradlew를 쓸 수 없습니다"
      [ "$ROLE" != reviewer ] || check_reviewer_gradle "${words[@]:1}"
      case "$args" in
        *' -I'* | *' --init-script'* | *' -c '* | *' --settings-file'* | *' -b '* | *' --build-file'* | *' -p '* | *' --project-dir'*)
          deny "Gradle의 스크립트·프로젝트 지정 옵션은 쓸 수 없습니다" ;;
        *' wrapper '* | *' init '*)
          deny "Gradle wrapper·init 작업은 쓸 수 없습니다" ;;
      esac ;;
    npm)
      check_npm "${words[1]:-}" "${words[2]:-}" "$args" ;;
    *)
      deny "허용 목록에 없는 명령입니다 (${words[0]})" ;;
  esac
}

check_command() {
  local text segment
  case "$1" in
    *'$('* | *'`'* | *'<('* | *'>('*)
      deny "명령 치환은 쓸 수 없습니다" ;;
    *\\\"* | *\\\'*)
      deny "이스케이프한 따옴표는 쓸 수 없습니다" ;;
    *\'*\"* | *\"*\'*)
      deny "작은따옴표와 큰따옴표를 한 명령에 함께 쓸 수 없습니다" ;;
  esac

  # 따옴표 안의 글자는 인자일 뿐이므로 지운다 (grep "List<Question>" 같은 검색이 막히지 않도록).
  text=$(printf '%s\n' "$1" | sed -E "s/'[^']*'/Q/g; s/\"[^\"]*\"/Q/g; s/[0-9]*>&[0-9]+//g; s/[0-9&]*>+[[:space:]]*\/dev\/null//g")
  case "$text" in
    *\'* | *\"*) deny "따옴표 짝이 맞지 않습니다" ;;
    *'>'*) deny "리디렉션으로 파일을 쓸 수 없습니다. Edit이나 Write를 쓰세요" ;;
    *'<<'*) deny "heredoc은 쓸 수 없습니다" ;;
  esac

  while IFS= read -r segment; do
    [ -n "${segment//[[:space:]]/}" ] || continue
    check_segment "$segment"
  done < <(printf '%s\n' "$text" | tr ';|&' '\n\n\n')
  allow
}

case "$ROLE" in
  backend-dev | frontend-dev | test-writer | reviewer) ;;
  *) deny "알 수 없는 역할입니다 ($ROLE)" ;;
esac

input=$(cat)
command -v jq >/dev/null 2>&1 || deny "jq가 없어 입력을 확인할 수 없습니다"
ROOT=${CLAUDE_PROJECT_DIR:?}

tool=$(printf '%s' "$input" | jq -r '.tool_name // empty')
case "$tool" in
  Edit | Write)
    check_file "$(printf '%s' "$input" | jq -r '.tool_input.file_path // empty')" ;;
  Bash)
    check_command "$(printf '%s' "$input" | jq -r '.tool_input.command // empty')" ;;
  *)
    deny "허용하지 않은 도구입니다 ($tool)" ;;
esac
