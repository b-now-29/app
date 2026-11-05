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

# 로그인: 가입 검사는 계정을 만들고 테스트 계정은 dev 프로필에만 있으므로 로컬에서만 실행한다
case "$BASE" in
  http://localhost:* | http://127.0.0.1:*)
    cookies=$(mktemp)
    trap 'rm -f "$cookies"' EXIT

    check_auth() { # 이름, 기대 상태 코드, 메서드, 경로, JSON 본문(선택). 쿠키 파일을 보내고 갱신한다
      local status
      total=$((total + 1))
      if [ -n "${5:-}" ]; then
        status=$(printf '%s' "$5" | curl -s -o /dev/null -w '%{http_code}' -b "$cookies" -c "$cookies" -X "$3" -H 'Content-Type: application/json' -d @- "$BASE$4")
      else
        status=$(curl -s -o /dev/null -w '%{http_code}' -b "$cookies" -c "$cookies" -X "$3" "$BASE$4")
      fi
      if [ "$status" = "$2" ]; then
        echo "OK   $1: $status"
      else
        failed=$((failed + 1))
        echo "실패 $1: $status (기대 $2)"
      fi
    }

    email="smoke-$(date +%s)-$$@example.com"
    password=smoke-password-1234
    credentials="{\"email\":\"$email\",\"password\":\"$password\"}"
    long_password=$(printf 'a%.0s' $(seq 1 73))

    check_auth "비로그인 /users/me" 401 GET /users/me
    check "짧은 비밀번호로 가입" 400 POST /users "{\"email\":\"$email\",\"password\":\"short\"}"
    check "73바이트 비밀번호로 가입" 400 POST /users "{\"email\":\"$email\",\"password\":\"$long_password\"}"

    check_auth "가입" 201 POST /users "$credentials"
    check_auth "가입 후 /users/me" 200 GET /users/me
    me=$(curl -s -b "$cookies" "$BASE/users/me")
    check_true "/users/me 응답은 id, email만" "$(jq 'keys == ["email","id"]' <<< "$me")"
    check "대문자만 다른 이메일로 가입" 409 POST /users "{\"email\":\"$(tr '[:lower:]' '[:upper:]' <<< "$email")\",\"password\":\"$password\"}"

    check_auth "로그아웃" 204 POST /auth/logout
    check_auth "로그아웃 후 /users/me" 401 GET /users/me
    check_auth "비로그인 로그아웃" 204 POST /auth/logout

    login_headers=$(printf '%s' "$credentials" | curl -s -o /dev/null -D - -H 'Content-Type: application/json' -d @- "$BASE/auth/login")
    check_true "로그인 쿠키는 HttpOnly, SameSite=Lax, Path=/api" "$(grep -i '^set-cookie: access_token=' <<< "$login_headers" | grep -i 'HttpOnly' | grep -i 'SameSite=Lax' | grep -qi 'Path=/api' && echo true || echo false)"

    check_auth "로그인" 200 POST /auth/login "$credentials"
    check_auth "로그인 후 /users/me" 200 GET /users/me
    check "테스트 계정 로그인" 200 POST /auth/login '{"email":"test@example.com","password":"test1234!"}'

    wrong_password=$(curl -s -H 'Content-Type: application/json' -d "{\"email\":\"$email\",\"password\":\"wrong-password\"}" "$BASE/auth/login")
    unknown_email=$(curl -s -H 'Content-Type: application/json' -d '{"email":"no-such-user@example.com","password":"wrong-password"}' "$BASE/auth/login")
    check "틀린 비밀번호" 401 POST /auth/login "{\"email\":\"$email\",\"password\":\"wrong-password\"}"
    check "없는 이메일" 401 POST /auth/login '{"email":"no-such-user@example.com","password":"wrong-password"}'
    check_true "틀린 비밀번호와 없는 이메일의 응답이 같음" "$([ "$wrong_password" = "$unknown_email" ] && echo true || echo false)"
    check "로그인 빈 비밀번호" 400 POST /auth/login "{\"email\":\"$email\",\"password\":\"\"}"

    forged_public=$(curl -s -o /dev/null -w '%{http_code}' -b 'access_token=forged' "$BASE/categories")
    forged_me=$(curl -s -o /dev/null -w '%{http_code}' -b 'access_token=forged' "$BASE/users/me")
    check_true "위조 쿠키로 공개 API는 200" "$([ "$forged_public" = 200 ] && echo true || echo false)"
    check_true "위조 쿠키로 /users/me는 401" "$([ "$forged_me" = 401 ] && echo true || echo false)"
    ;;
  *)
    echo "건너뜀 로그인 검사: 로컬 주소가 아닙니다 ($BASE)"
    ;;
esac

echo "$total건 중 실패 $failed건"
[ "$failed" -eq 0 ]
