#!/usr/bin/env bash
# 실행 중인 서버의 API를 실제로 호출해 상태 코드를 확인한다. 새 API가 생기면 아래 검사를 추가한다.
# 사용법: scripts/local-run-jar.sh 를 띄운 뒤 scripts/api-smoke.sh (실패가 있으면 exit 1)
#         BASE_URL=http://localhost:8080 scripts/api-smoke.sh
set -uo pipefail

BASE=${BASE_URL:-http://localhost:8080}/api/v1
failed=0
total=0

check() { # 이름, 기대 상태 코드, 메서드, 경로, JSON 본문(선택)
  local status
  total=$((total + 1))
  if [ -n "${5:-}" ]; then
    status=$(printf '%s' "$5" | curl -s -o /dev/null -w '%{http_code}' -X "$3" -H 'Content-Type: application/json' -d @- "$BASE$4")
  else
    status=$(curl -s -o /dev/null -w '%{http_code}' -X "$3" "$BASE$4")
  fi
  if [ "$status" = "$2" ]; then
    echo "OK   $1: $status"
  else
    failed=$((failed + 1))
    echo "실패 $1: $status (기대 $2)"
  fi
}

check_true() { # 이름, 조건 결과 (true면 통과)
  total=$((total + 1))
  if [ "$2" = true ]; then
    echo "OK   $1"
  else
    failed=$((failed + 1))
    echo "실패 $1"
  fi
}

curl -s -o /dev/null --retry 45 --retry-all-errors --retry-delay 2 "$BASE/categories" ||
  { echo "서버가 응답하지 않습니다: $BASE" >&2; exit 1; }

check "카테고리 목록" 200 GET /categories
check "없는 카테고리의 문제" 404 GET /categories/no-such-slug/questions

for slug in $(curl -s "$BASE/categories" | jq -r '.[].slug'); do
  questions=$(curl -s "$BASE/categories/$slug/questions")
  check "$slug 문제 목록" 200 GET "/categories/$slug/questions"
  # 정답 정보는 조회 API로 내려주지 않는다
  check_true "$slug 조회 응답에 정답 정보 없음" "$(jq '[.. | objects | keys[]] | unique - ["id","type","content","choices"] == []' <<< "$questions")"
done

questions=$(curl -s "$BASE/categories/java/questions")
choice_id() { jq -r '[.[] | select(.type == "MULTIPLE_CHOICE")][0].id' <<< "$questions"; }
type_id() { jq -r --arg type "$1" '[.[] | select(.type == $type)][0].id' <<< "$questions"; }
mc=$(choice_id)
mc_choice=$(jq -r '[.[] | select(.type == "MULTIPLE_CHOICE")][0].choices[0].id' <<< "$questions")
short=$(type_id SHORT_ANSWER)
essay=$(type_id ESSAY)
long_5000=$(printf '가%.0s' $(seq 1 5000))

check "객관식 제출" 200 POST "/questions/$mc/answers" "{\"choiceId\":$mc_choice}"
check "객관식 choiceId 누락" 400 POST "/questions/$mc/answers" '{}'
check "객관식에 text-answers" 400 POST "/questions/$mc/text-answers" '{"answer":"내 답"}'
check "없는 문제에 text-answers" 404 POST /questions/999999/text-answers '{"answer":"내 답"}'

for pair in "주관식:$short" "서술형:$essay"; do
  name=${pair%%:*}
  id=${pair#*:}
  check "$name 제출" 200 POST "/questions/$id/text-answers" '{"answer":"첫 줄\n둘째 줄"}'
  check "$name 5000자" 200 POST "/questions/$id/text-answers" "{\"answer\":\"$long_5000\"}"
  check "$name 5001자" 400 POST "/questions/$id/text-answers" "{\"answer\":\"${long_5000}가\"}"
  check "$name 공백만" 400 POST "/questions/$id/text-answers" '{"answer":"   "}'
  check "$name에 answers" 400 POST "/questions/$id/answers" '{"choiceId":1}'
done

echo "$total건 중 실패 $failed건"
[ "$failed" -eq 0 ]
