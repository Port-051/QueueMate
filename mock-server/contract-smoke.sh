#!/usr/bin/env bash
#
# 더미 서버 계약 스모크 테스트
#
# docs/14의 계약을 더미 서버가 실제로 지키는지 확인한다.
# 프론트가 이 서버에 붙기 전에 한 번 돌려서, 서버가 계약대로 답하는지 본다.
#
#   node mock-server/server.js &
#   bash mock-server/contract-smoke.sh
#
# 다른 포트로 띄웠으면 MOCK_PORT로 알려 준다.
#
# 주의 — 중첩된 $( ) 안에 {a,b} 형태의 JSON을 직접 쓰면 셸 중괄호 확장에 걸려
# 콤마 단위로 쪼개진 채 전송된다. 두 키 이상인 payload는 반드시 변수에 먼저 담는다.
#
set -u
B=http://localhost:${MOCK_PORT:-8099}/api/v1
pass=0; fail=0
chk() { # chk <설명> <기대상태> <실제상태> [본문]
  if [ "$2" = "$3" ]; then pass=$((pass+1)); printf "  ✅ %-52s %s\n" "$1" "$3"
  else fail=$((fail+1)); printf "  ❌ %-52s 기대 %s 실제 %s\n     본문: %s\n" "$1" "$2" "$3" "$(cat /tmp/qm_body)"; fi
}
st() { curl -s -o /tmp/qm_body -w '%{http_code}' "$@"; }
bd() { cat /tmp/qm_body; }
# 응답 본문에서 키 하나를 꺼낸다. jq가 없는 기계에서도 돌게 python으로 한다.
jq() { python3 -c "import sys,json;print(json.load(sys.stdin).get('$1',''))" </tmp/qm_body; }

echo "── Auth"
chk "signup 201" 201 "$(st -X POST $B/auth/signup -H 'Content-Type: application/json' -d '{"email":"alpha@queuemate.dev","password":"Qm!passw0rd","nickname":"알파"}')"
ALPHA_ID=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
chk "signup 이메일 중복 409" 409 "$(st -X POST $B/auth/signup -H 'Content-Type: application/json' -d '{"email":"alpha@queuemate.dev","password":"Qm!passw0rd","nickname":"다른닉"}')"
chk "signup 짧은 비밀번호 400" 400 "$(st -X POST $B/auth/signup -H 'Content-Type: application/json' -d '{"email":"x@queuemate.dev","password":"short7c","nickname":"엑스"}')"
st -X POST $B/auth/signup -H 'Content-Type: application/json' -d '{"email":"bravo@queuemate.dev","password":"Qm!passw0rd","nickname":"브라보"}' >/dev/null
BRAVO_ID=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')

chk "login 200" 200 "$(st -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"email":"alpha@queuemate.dev","password":"Qm!passw0rd"}')"
A=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
AR=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["refreshToken"])')
chk "login 틀린 비밀번호 401" 401 "$(st -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"email":"alpha@queuemate.dev","password":"nope!nope1"}')"
st -X POST $B/auth/login -H 'Content-Type: application/json' -d '{"email":"bravo@queuemate.dev","password":"Qm!passw0rd"}' >/dev/null
Bt=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

chk "refresh 200" 200 "$(st -X POST $B/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$AR\"}")"
chk "refresh 재사용 401" 401 "$(st -X POST $B/auth/refresh -H 'Content-Type: application/json' -d "{\"refreshToken\":\"$AR\"}")"

echo "── 인증 필터"
chk "토큰 없이 /users/me 401" 401 "$(st $B/users/me)"
chk "401도 code를 준다" "UNAUTHORIZED" "$(jq code)"
chk "깨진 토큰 401" 401 "$(st $B/users/me -H 'Authorization: Bearer garbage')"

echo "── User"
chk "GET /users/me 200" 200 "$(st $B/users/me -H "Authorization: Bearer $A")"
chk "프로필 3필드" "3" "$(bd | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')"
chk "닉네임 중복 409" 409 "$(st -X PATCH $B/users/me -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{"nickname":"브라보"}')"
chk "게임계정 연결 201" 201 "$(st -X POST $B/users/me/game-accounts -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{"game":"LOL","externalGameId":"Alpha#KR1","region":"KR"}')"
GA=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
chk "같은 계정 재연결 409" 409 "$(st -X POST $B/users/me/game-accounts -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{"game":"LOL","externalGameId":"Alpha#KR1","region":"KR"}')"
chk "같은 게임 다른 계정 201" 201 "$(st -X POST $B/users/me/game-accounts -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{"game":"LOL","externalGameId":"Alpha#KR2","region":"KR"}')"
chk "남의 계정 삭제 404" 404 "$(st -X DELETE $B/users/me/game-accounts/$GA -H "Authorization: Bearer $Bt")"
chk "내 계정 삭제 204" 204 "$(st -X DELETE $B/users/me/game-accounts/$GA -H "Authorization: Bearer $A")"

chk "아바타 설정 200" 200 "$(st -X PATCH $B/users/me -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{"avatarUrl":"https://cdn/x.png"}')"
chk "아바타 null → 삭제" "None" "$(st -X PATCH $B/users/me -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{"avatarUrl":null}' >/dev/null; jq avatarUrl | sed 's/^$/None/')"

echo "── Game config"
chk "GET /games 200" 200 "$(st $B/games -H "Authorization: Bearer $A")"
chk "LoL 스키마에 ANY 포함" "True" "$(st $B/games/LOL/match-schema -H "Authorization: Bearer $A" >/dev/null; bd | python3 -c 'import sys,json;print("ANY" in json.load(sys.stdin)["keyCondition"]["values"])')"
chk "발로란트에 ANY 없음" "False" "$(st $B/games/VALORANT/match-schema -H "Authorization: Bearer $A" >/dev/null; bd | python3 -c 'import sys,json;print("ANY" in json.load(sys.stdin)["keyCondition"]["values"])')"
chk "없는 게임 400" 400 "$(st $B/games/OVERWATCH/modes -H "Authorization: Bearer $A")"

echo "── Match"
LOL='{"game":"LOL","modeKey":"SOLO_DUO_RANKED","keyCondition":{"type":"POSITION","value":"MID"},"voicePreference":"OPTIONAL","playPurpose":"RANK_UP"}'
chk "매칭 시작 201" 201 "$(st -X POST $B/match-requests -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "$LOL")"
MR=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
chk "중복 매칭 409" 409 "$(st -X POST $B/match-requests -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "$LOL")"
chk "게임/조건종류 불일치 400" 400 "$(st -X POST $B/match-requests -H "Authorization: Bearer $Bt" -H 'Content-Type: application/json' -d '{"game":"LOL","modeKey":"SOLO_DUO_RANKED","keyCondition":{"type":"ROLE","value":"DUELIST"},"voicePreference":"OPTIONAL","playPurpose":"RANK_UP"}')"
chk "없는 모드 404" 404 "$(st -X POST $B/match-requests -H "Authorization: Bearer $Bt" -H 'Content-Type: application/json' -d '{"game":"LOL","modeKey":"ARAM","keyCondition":{"type":"POSITION","value":"MID"},"voicePreference":"OPTIONAL","playPurpose":"RANK_UP"}')"
chk "enum 소문자 400" 400 "$(st -X POST $B/match-requests -H "Authorization: Bearer $Bt" -H 'Content-Type: application/json' -d '{"game":"LOL","modeKey":"SOLO_DUO_RANKED","keyCondition":{"type":"POSITION","value":"MID"},"voicePreference":"optional","playPurpose":"RANK_UP"}')"
chk "남의 요청 조회 404" 404 "$(st $B/match-requests/$MR -H "Authorization: Bearer $Bt")"
chk "uuid 아닌 경로 400" 400 "$(st $B/match-requests/not-a-uuid -H "Authorization: Bearer $A")"

echo "── 매칭 성사 (자동)"
st -X POST $B/match-requests -H "Authorization: Bearer $Bt" -H 'Content-Type: application/json' -d '{"game":"LOL","modeKey":"SOLO_DUO_RANKED","keyCondition":{"type":"POSITION","value":"TOP"},"voicePreference":"OPTIONAL","playPurpose":"RANK_UP"}' >/dev/null
BMR=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
sleep 2
st $B/match-requests/$MR -H "Authorization: Bearer $A" >/dev/null
chk "제안이 붙어 PROPOSED" "PROPOSED" "$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')"
PID=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["proposalId"])')
chk "알파 수락 200" 200 "$(st -X POST $B/proposals/$PID/accept -H "Authorization: Bearer $A")"
chk "확정 전 partyId null" "None" "$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["partyId"])')"
chk "브라보 수락 200" 200 "$(st -X POST $B/proposals/$PID/accept -H "Authorization: Bearer $Bt")"
chk "확정되어 CONFIRMED" "CONFIRMED" "$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')"
PARTY=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["partyId"])')
chk "확정 후 재수락 409" 409 "$(st -X POST $B/proposals/$PID/accept -H "Authorization: Bearer $A")"

echo "── Party"
chk "파티 조회 200" 200 "$(st $B/parties/$PARTY -H "Authorization: Bearer $A")"
chk "알파 ready 200" 200 "$(st -X POST $B/parties/$PARTY/ready -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{"ready":true}')"
chk "아직 OPEN" "OPEN" "$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')"
chk "브라보 ready 200" 200 "$(st -X POST $B/parties/$PARTY/ready -H "Authorization: Bearer $Bt" -H 'Content-Type: application/json' -d '{"ready":true}')"
chk "전원 준비 → READY" "READY" "$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["status"])')"
chk "ready 누락 400" 400 "$(st -X POST $B/parties/$PARTY/ready -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{}')"
chk "나가기 204" 204 "$(st -X POST $B/parties/$PARTY/leave -H "Authorization: Bearer $A")"
chk "두 번 나가기 409" 409 "$(st -X POST $B/parties/$PARTY/leave -H "Authorization: Bearer $A")"

echo "── Reservation"
R='{"condition":{"game":"VALORANT","modeKey":"COMPETITIVE","keyCondition":{"type":"ROLE","value":"DUELIST"},"voicePreference":"REQUIRED","playPurpose":"RANK_UP"},"availableFrom":"2026-09-20T21:00:00Z","availableTo":"2026-09-20T23:00:00Z","playAmount":"TWO_PLUS"}'
chk "예약 생성 201" 201 "$(st -X POST $B/reservations -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "$R")"
RID=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
chk "createdAt 있음" "True" "$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["createdAt"] is not None)')"
chk "30분 격자 위반 400" 400 "$(st -X POST $B/reservations -H "Authorization: Bearer $Bt" -H 'Content-Type: application/json' -d "$(echo $R | sed 's/21:00:00Z/21:15:00Z/')")"
chk "시간 겹침 409" 409 "$(st -X POST $B/reservations -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "$(echo $R | sed 's/21:00:00Z/22:00:00Z/;s/23:00:00Z/23:30:00Z/')")"
chk "경계 인접 201" 201 "$(st -X POST $B/reservations -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "$(echo $R | sed 's/T21:00:00Z/T23:00:00Z/;s/T23:00:00Z\"/T23:30:00Z\"/2')")"
chk "PUT 부분수정 400" 400 "$(st -X PUT $B/reservations/$RID -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{"playAmount":"ONE_GAME"}')"
chk "남의 예약 404" 404 "$(st $B/reservations/$RID -H "Authorization: Bearer $Bt")"
chk "예약 취소 204" 204 "$(st -X DELETE $B/reservations/$RID -H "Authorization: Bearer $A")"
chk "재취소 204(멱등)" 204 "$(st -X DELETE $B/reservations/$RID -H "Authorization: Bearer $A")"

echo "── Social"
chk "친구요청 201" 201 "$(st -X POST $B/friend-requests -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "{\"targetUserId\":\"$BRAVO_ID\"}")"
FR=$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["id"])')
chk "direction SENT" "SENT" "$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["direction"])')"
chk "내가 보낸 중복 409" 409 "$(st -X POST $B/friend-requests -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "{\"targetUserId\":\"$BRAVO_ID\"}")"
chk "상대가 보낸 것 INVERSE 409" 409 "$(st -X POST $B/friend-requests -H "Authorization: Bearer $Bt" -H 'Content-Type: application/json' -d "{\"targetUserId\":\"$ALPHA_ID\"}")"
chk "  → code INVERSE_REQUEST_PENDING" "INVERSE_REQUEST_PENDING" "$(bd | python3 -c 'import sys,json;print(json.load(sys.stdin)["code"])')"
chk "발신자가 수락 404" 404 "$(st -X POST $B/friend-requests/$FR/accept -H "Authorization: Bearer $A")"
chk "수신자가 수락 200" 200 "$(st -X POST $B/friend-requests/$FR/accept -H "Authorization: Bearer $Bt")"
chk "  → FriendView(friendedAt 존재)" "True" "$(bd | python3 -c 'import sys,json;d=json.load(sys.stdin);print(d.get("friendedAt") is not None and "direction" not in d)')"
chk "자기자신 차단 409" 409 "$(st -X POST $B/blocks -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "{\"targetUserId\":\"$ALPHA_ID\"}")"
chk "차단 201" 201 "$(st -X POST $B/blocks -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "{\"targetUserId\":\"$BRAVO_ID\"}")"
chk "  → 3필드(avatarUrl 없음)" "3" "$(bd | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')"
chk "차단이 친구관계 정리" "0" "$(st $B/friends -H "Authorization: Bearer $A" >/dev/null; bd | python3 -c 'import sys,json;print(len(json.load(sys.stdin)))')"
chk "차단 해제 204" 204 "$(st -X DELETE $B/blocks/$BRAVO_ID -H "Authorization: Bearer $A")"
chk "차단 안 한 사람 해제 404" 404 "$(st -X DELETE $B/blocks/$BRAVO_ID -H "Authorization: Bearer $A")"

echo "── Misc"
chk "recent-players 200" 200 "$(st $B/recent-players -H "Authorization: Bearer $A")"
chk "limit=0 400 (실물은 500)" 400 "$(st "$B/recent-players?limit=0" -H "Authorization: Bearer $A")"
REP1="{\"targetUserId\":\"$BRAVO_ID\",\"reason\":\"ABUSIVE_LANGUAGE\",\"description\":\"욕설\"}"
chk "신고 201" 201 "$(st -X POST $B/reports -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "$REP1")"
chk "  → 본문 없음" "0" "$(wc -c </tmp/qm_body | tr -d ' ')"
REP2="{\"targetUserId\":\"$ALPHA_ID\",\"reason\":\"OTHER\"}"
chk "자기자신 신고 409" 409 "$(st -X POST $B/reports -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "$REP2")"
REP3="{\"targetUserId\":\"$BRAVO_ID\",\"reason\":\"SPAM\"}"
chk "enum 밖 사유 400" 400 "$(st -X POST $B/reports -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "$REP3")"
chk "  → code VALIDATION_FAILED" "VALIDATION_FAILED" "$(jq code)"

echo "── 횡단"
chk "Content-Type 없이 415" 415 "$(st -X POST $B/blocks -H "Authorization: Bearer $A" -H 'Content-Type: text/plain' -d '{}')"
chk "깨진 JSON 400" 400 "$(st -X POST $B/blocks -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{')"
chk "201에 Location 없음" "" "$(curl -s -D- -o /dev/null -X POST $B/friend-requests -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d "{\"targetUserId\":\"$BRAVO_ID\"}" | grep -i '^location:' | tr -d '\r')"

echo
echo "════════ 통과 $pass · 실패 $fail ════════"
[ "$fail" -eq 0 ]
