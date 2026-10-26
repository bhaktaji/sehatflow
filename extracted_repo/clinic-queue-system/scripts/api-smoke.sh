#!/usr/bin/env bash
# API smoke test for the clinic queue backend. Usage: ./scripts/api-smoke.sh [base_url]
set -u
BASE="${1:-http://localhost:8080}"
COOKIES=/tmp/cq-cookies.txt
PASS=0; FAIL=0

cq() { curl -s --noproxy '*' -c "$COOKIES" -b "$COOKIES" "$@"; }

check() { # name expected actual
  if [ "$2" = "$3" ]; then PASS=$((PASS+1)); echo "PASS $1";
  else FAIL=$((FAIL+1)); echo "FAIL $1: expected [$2] got [$3]"; fi
}

rm -f "$COOKIES"
# Unique business date per run keeps the smoke test idempotent on a dev database.
BIZDATE=$(date -d "+$(( RANDOM % 80 + 1 )) days" +%Y-%m-%d)
cq "$BASE/api/v1/auth/csrf" > /dev/null
XSRF=$(grep -i "XSRF-TOKEN" "$COOKIES" | awk '{print $NF}')
[ -n "$XSRF" ] && { PASS=$((PASS+1)); echo "PASS csrf cookie set"; } || { FAIL=$((FAIL+1)); echo "FAIL csrf cookie set"; }

LOGIN=$(cq -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"username":"owner","password":"owner123"}')
ROLE=$(echo "$LOGIN" | sed -n 's/.*"role":"\([A-Z_]*\)".*/\1/p')
check "R-001 owner login" "OWNER" "$ROLE"

BADLOGIN=$(cq -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"username":"owner","password":"wrong"}')
check "R-002 invalid password 401" "401" "$BADLOGIN"
BADLOGIN_BODY=$(cq -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"username":"owner","password":"wrong"}')
check "R-002 generic error code" "INVALID_CREDENTIALS" "$(echo "$BADLOGIN_BODY" | sed -n 's/.*"code":"\([A-Z_]*\)".*/\1/p')"

ME=$(cq "$BASE/api/v1/auth/me")
check "auth/me role" "OWNER" "$(echo "$ME" | sed -n 's/.*"role":"\([A-Z_]*\)".*/\1/p')"

DOCS=$(cq "$BASE/api/v1/doctors")
DOCID=$(echo "$DOCS" | sed -n 's/.*"id":"\([0-9a-f-]*\)".*/\1/p' | head -1)
[ -n "$DOCID" ] && { PASS=$((PASS+1)); echo "PASS doctors list"; } || { FAIL=$((FAIL+1)); echo "FAIL doctors list"; }

SESSION=$(cq -X POST "$BASE/api/v1/queue-sessions" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" \
  -d "{\"doctorId\":\"$DOCID\",\"businessDate\":\"$BIZDATE\"}")
SID=$(echo "$SESSION" | sed -n 's/.*"id":"\([0-9a-f-]*\)".*/\1/p' | head -1)
DSECRET=$(echo "$SESSION" | sed -n 's/.*"publicDisplaySecret":"\([0-9a-f]*\)".*/\1/p')
[ -n "$SID" ] && { PASS=$((PASS+1)); echo "PASS create session"; } || { FAIL=$((FAIL+1)); echo "FAIL create session: $SESSION"; }

MAYA_SECRET=""; ARUN_SECRET=""
BODY='{"displayName":"Riya"}'
TOKEN=$(cq -X POST "$BASE/api/v1/queue-sessions/$SID/tokens" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" -d "$BODY")
RIYA_LABEL=$(echo "$TOKEN" | sed -n 's/.*"label":"\([A-Z0-9]*\)".*/\1/p')
RIYA_SECRET=$(echo "$TOKEN" | sed -n 's#.*"patientUrl":"http://localhost:5173/p/\([0-9a-f]*\)".*#\1#p')
check "R-003 first token A1" "A1" "$RIYA_LABEL"

for NAME in Maya Arun; do
  TOKEN=$(cq -X POST "$BASE/api/v1/queue-sessions/$SID/tokens" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $XSRF" -d "{\"displayName\":\"$NAME\"}")
  LABEL=$(echo "$TOKEN" | sed -n 's/.*"label":"\([A-Z0-9]*\)".*/\1/p')
  SECRET=$(echo "$TOKEN" | sed -n 's#.*"patientUrl":"http://localhost:5173/p/\([0-9a-f]*\)".*#\1#p')
  if [ -n "$LABEL" ] && [ -n "$SECRET" ]; then PASS=$((PASS+1)); echo "PASS checkin token $LABEL"; else FAIL=$((FAIL+1)); echo "FAIL checkin token: $TOKEN"; fi
  if [ "$NAME" = "Maya" ]; then MAYA_SECRET="$SECRET"; MAYA_LABEL="$LABEL"; fi
  if [ "$NAME" = "Arun" ]; then ARUN_SECRET="$SECRET"; ARUN_LABEL="$LABEL"; ARUN_ID=$(echo "$TOKEN" | sed -n 's/.*"id":"\([0-9a-f-]*\)".*/\1/p'); fi
done

QUEUE=$(cq "$BASE/api/v1/queue-sessions/$SID/queue")
check "R-003 queue has 3 waiting" "3" "$(echo "$QUEUE" | sed -n 's/.*"peopleWaiting":\([0-9]*\).*/\1/p')"

NEXT=$(cq -X POST "$BASE/api/v1/queue-sessions/$SID/call-next" -H "X-XSRF-TOKEN: $XSRF")
check "R-005 call next A1" "A1" "$(echo "$NEXT" | sed -n 's/.*"label":"\([A-Z0-9]*\)".*/\1/p')"
CALLED_ID=$(echo "$NEXT" | sed -n 's/.*"id":"\([0-9a-f-]*\)".*/\1/p')
NEXT2=$(cq -X POST "$BASE/api/v1/queue-sessions/$SID/call-next" -H "X-XSRF-TOKEN: $XSRF")
check "R-006 second call next A2" "A2" "$(echo "$NEXT2" | sed -n 's/.*"label":"\([A-Z0-9]*\)".*/\1/p')"

MAYA_ID=$(echo "$QUEUE" | grep -o '"id":"[0-9a-f-]*","tokenNumber":2' | head -1 | sed 's/"id":"//;s/".*//')
CONFLICT=$(cq -X POST "$BASE/api/v1/tokens/$MAYA_ID/complete" -H "X-XSRF-TOKEN: $XSRF")
check "R-010 complete waiting rejected" "TOKEN_STATE_CONFLICT" "$(echo "$CONFLICT" | sed -n 's/.*"code":"\([A-Z_]*\)".*/\1/p')"

START=$(cq -X POST "$BASE/api/v1/tokens/$CALLED_ID/start" -H "X-XSRF-TOKEN: $XSRF")
check "start consultation" "IN_CONSULTATION" "$(echo "$START" | sed -n 's/.*"status":"\([A-Z_]*\)".*/\1/p')"

PUB=$(cq "$BASE/api/v1/public/tokens/$RIYA_SECRET")
check "R-016 public label A1" "A1" "$(echo "$PUB" | sed -n 's/.*"tokenLabel":"\([A-Z0-9]*\)".*/\1/p')"
check "R-016 no displayName leak" "" "$(echo "$PUB" | sed -n 's/.*"displayName":"\([^"]*\)".*/\1/p')"
check "R-016 no secret leak" "" "$(echo "$PUB" | sed -n 's/.*"patientUrl".*/\1/p')"

MAYAPUB=$(cq "$BASE/api/v1/public/tokens/$MAYA_SECRET")
check "R-016 Maya called public status" "CALLED" "$(echo "$MAYAPUB" | sed -n 's/.*"status":"\([A-Z_]*\)".*/\1/p')"

BADPUB=$(curl -s --noproxy '*' -o /dev/null -w "%{http_code}" "$BASE/api/v1/public/tokens/deadbeef")
check "R-017 invalid public secret 404" "404" "$BADPUB"

DISP=$(cq "$BASE/api/v1/public/queues/$DSECRET")
# At this point A2 was the most recent call-next, so it is the current 'now serving'.
check "public display now serving A2" "A2" "$(echo "$DISP" | sed -n 's/.*"nowServingLabel":"\([A-Z0-9]*\)".*/\1/p')"

COMPLETE=$(cq -X POST "$BASE/api/v1/tokens/$CALLED_ID/complete" -H "X-XSRF-TOKEN: $XSRF")
check "R-009 complete" "COMPLETED" "$(echo "$COMPLETE" | sed -n 's/.*"status":"\([A-Z_]*\)".*/\1/p')"

BADPRIO=$(cq -X POST "$BASE/api/v1/tokens/$ARUN_ID/priority" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" -d '{"enabled":true,"reason":""}')
check "R-012 priority without reason" "VALIDATION_ERROR" "$(echo "$BADPRIO" | sed -n 's/.*"code":"\([A-Z_]*\)".*/\1/p')"

PRIO=$(cq -X POST "$BASE/api/v1/tokens/$ARUN_ID/priority" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" -d '{"enabled":true,"reason":"Approved by clinician"}')
check "R-013 priority set" "1" "$(echo "$PRIO" | grep -c '"priority":true')"
REORDER=$(cq -X POST "$BASE/api/v1/tokens/$ARUN_ID/reorder" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" -d '{"targetPosition":1,"reason":"Reception correction"}')
check "R-013 reorder to front" "1" "$(echo "$REORDER" | sed -n 's/.*"queuePosition":\([0-9]*\).*/\1/p')"
BADREORDER=$(cq -X POST "$BASE/api/v1/tokens/$ARUN_ID/reorder" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" -d '{"targetPosition":1,"reason":""}')
check "R-012 reorder without reason" "VALIDATION_ERROR" "$(echo "$BADREORDER" | sed -n 's/.*"code":"\([A-Z_]*\)".*/\1/p')"

cq -X POST "$BASE/api/v1/queue-sessions/$SID/pause" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" -d '{"message":"Doctor temporarily delayed"}' > /dev/null
PAUSED=$(cq -X POST "$BASE/api/v1/queue-sessions/$SID/tokens" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" -d '{}')
check "R-011 create while paused rejected" "QUEUE_PAUSED" "$(echo "$PAUSED" | sed -n 's/.*"code":"\([A-Z_]*\)".*/\1/p')"
PAUSED_CALL=$(cq -X POST "$BASE/api/v1/queue-sessions/$SID/call-next" -H "X-XSRF-TOKEN: $XSRF")
check "R-011 call-next while paused rejected" "QUEUE_PAUSED" "$(echo "$PAUSED_CALL" | sed -n 's/.*"code":"\([A-Z_]*\)".*/\1/p')"
PAUSED_PUB=$(cq "$BASE/api/v1/public/tokens/$MAYA_SECRET")
check "R-011 paused public ETA unavailable" "UNAVAILABLE" "$(echo "$PAUSED_PUB" | sed -n 's/.*"estimateQuality":"\([A-Z_]*\)".*/\1/p')"

cq -X POST "$BASE/api/v1/queue-sessions/$SID/resume" -H "X-XSRF-TOKEN: $XSRF" > /dev/null

SKIP=$(cq -X POST "$BASE/api/v1/tokens/$MAYA_ID/skip" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" -d '{"reason":"Patient not present"}')
check "skip token" "SKIPPED" "$(echo "$SKIP" | sed -n 's/.*"status":"\([A-Z_]*\)".*/\1/p')"
REJOIN=$(cq -X POST "$BASE/api/v1/tokens/$MAYA_ID/return-to-waiting" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $XSRF" -d '{"reason":"Back at desk"}')
check "R-014 rejoin after skip" "WAITING" "$(echo "$REJOIN" | sed -n 's/.*"status":"\([A-Z_]*\)".*/\1/p')"

AUDIT=$(cq "$BASE/api/v1/queue-sessions/$SID/audit")
check "R-025 audit TOKEN_CREATED present" "yes" "$(echo "$AUDIT" | grep -q TOKEN_CREATED && echo yes)"
check "R-025 audit TOKEN_PRIORITY_CHANGED present" "yes" "$(echo "$AUDIT" | grep -q TOKEN_PRIORITY_CHANGED && echo yes)"
check "R-025 audit actor recorded" "yes" "$(echo "$AUDIT" | grep -q '"actorDisplayName"' && echo yes)"
check "R-025 audit reason recorded" "yes" "$(echo "$AUDIT" | grep -q 'Approved by clinician' && echo yes)"

SUM=$(cq "$BASE/api/v1/queue-sessions/$SID/summary")
check "summary completed count" "1" "$(echo "$SUM" | sed -n 's/.*"completedCount":\([0-9]*\).*/\1/p')"

NOAUTH=$(curl -s --noproxy '*' -o /dev/null -w "%{http_code}" "$BASE/api/v1/queue-sessions/$SID/queue")
check "R-020 unauth staff endpoint 401" "401" "$NOAUTH"

NOCSRF=$(curl -s --noproxy '*' -b "$COOKIES" -o /dev/null -w "%{http_code}" -X POST "$BASE/api/v1/queue-sessions/$SID/resume" -H "X-XSRF-TOKEN: wrong")
check "R-029 wrong csrf 403" "403" "$NOCSRF"

# R-024: abusive public requests get throttled
for i in $(seq 1 400); do curl -s --noproxy '*' -o /dev/null "$BASE/api/v1/public/tokens/deadbeef"; done
RL=$(curl -s --noproxy '*' -o /dev/null -w "%{http_code}" "$BASE/api/v1/public/tokens/deadbeef")
check "R-024 rate limit 429" "429" "$RL"

echo ""
echo "SMOKE RESULT: PASS=$PASS FAIL=$FAIL"
exit $FAIL
