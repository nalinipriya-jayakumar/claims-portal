#!/bin/sh
# End-to-end smoke test against the running stack (docker compose up -d --build).
#
# Submits a claim through the portal's edge exactly as the browser does, then waits for the
# confirmation email in Mailpit and checks it carries the same claim number.
#
#   ./scripts/smoke-test.sh                  # defaults to the local compose ports
#   PORTAL_URL=... MAILPIT_URL=... ./scripts/smoke-test.sh
#
# Needs only sh, curl and sed (Git Bash on Windows works).
set -eu

PORTAL_URL="${PORTAL_URL:-http://localhost:3000}"
MAILPIT_URL="${MAILPIT_URL:-http://localhost:8025}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-30}"

fail() { echo "FAIL: $*" >&2; exit 1; }

# A date of loss that is always valid: yesterday (GNU date, then BSD date).
DATE_OF_LOSS=$(date -d yesterday +%F 2>/dev/null || date -v-1d +%F)
EMAIL="smoke-$(date +%s)@example.com"
REQUEST_ID="smoke-$(date +%s)"

echo "1. Portal is up at $PORTAL_URL"
curl -fsS "$PORTAL_URL/healthz" >/dev/null || fail "portal health check failed"

echo "2. Submit a claim through the edge (X-Request-Id: $REQUEST_ID)"
RESPONSE=$(curl -sS -w '\n%{http_code}' -X POST "$PORTAL_URL/api/v1/claims" \
  -H 'Content-Type: application/json' -H "X-Request-Id: $REQUEST_ID" \
  -d "{
    \"customer\": {\"firstName\": \"Smoke\", \"lastName\": \"Test\", \"email\": \"$EMAIL\"},
    \"policyNumber\": \"HO-12345678\",
    \"dateOfLoss\": \"$DATE_OF_LOSS\",
    \"lossType\": \"OTHER\",
    \"propertyAddress\": {\"street\": \"1 Test Street\", \"city\": \"Winnipeg\", \"province\": \"MB\", \"postalCode\": \"R3C 1A1\"},
    \"description\": \"Automated smoke test claim, safe to ignore.\",
    \"consentAcknowledged\": true
  }")
STATUS=$(printf '%s' "$RESPONSE" | tail -n 1)
BODY=$(printf '%s' "$RESPONSE" | sed '$d')
[ "$STATUS" = "201" ] || fail "expected 201, got $STATUS: $BODY"
CLAIM_NUMBER=$(printf '%s' "$BODY" | sed -n 's/.*"claimNumber":"\(CLM-[0-9]\{4\}-[0-9]\{6\}\)".*/\1/p')
[ -n "$CLAIM_NUMBER" ] || fail "no claim number in response: $BODY"
echo "   accepted as $CLAIM_NUMBER"

echo "3. Invalid submission is rejected with problem+json"
INVALID_STATUS=$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$PORTAL_URL/api/v1/claims" \
  -H 'Content-Type: application/json' -d '{"policyNumber": "bad"}')
[ "$INVALID_STATUS" = "400" ] || fail "expected 400 for an invalid claim, got $INVALID_STATUS"

echo "4. Wait up to ${TIMEOUT_SECONDS}s for the confirmation email to $EMAIL"
ELAPSED=0
while [ "$ELAPSED" -lt "$TIMEOUT_SECONDS" ]; do
  if curl -fsS "$MAILPIT_URL/api/v1/search?query=to:$EMAIL" | grep -q "We received your claim $CLAIM_NUMBER"; then
    echo "   email received after ${ELAPSED}s"
    echo "PASS: $CLAIM_NUMBER submitted, published and emailed"
    exit 0
  fi
  sleep 1
  ELAPSED=$((ELAPSED + 1))
done
fail "no email for $CLAIM_NUMBER within ${TIMEOUT_SECONDS}s"
