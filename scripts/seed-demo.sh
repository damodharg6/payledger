#!/usr/bin/env bash
# Seeds a demo merchant with a dashboard login and a mix of payments (every mock-bank scenario).
# DEV/DEMO ONLY: the default password below is a throwaway test value, never use it for real.
#
#   scripts/seed-demo.sh                      # against http://localhost:8080
#   BASE_URL=https://my-app.onrender.com DEMO_EMAIL=me+demo@example.com scripts/seed-demo.sh
#
# Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DEMO_EMAIL="${DEMO_EMAIL:-demo@payledger.test}"
DEMO_PASSWORD="${DEMO_PASSWORD:-demo-password-123}"
json() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

echo "Registering demo merchant $DEMO_EMAIL at $BASE_URL ..."
response=$(curl -s -w '\n%{http_code}' -X POST "$BASE_URL/api/v1/merchants" -H 'Content-Type: application/json' \
  -d "{\"name\":\"Chai Point Demo\",\"email\":\"$DEMO_EMAIL\",\"dashboardPassword\":\"$DEMO_PASSWORD\"}")
status=$(tail -n1 <<<"$response"); body=$(sed '$d' <<<"$response")
if [[ "$status" == "409" ]]; then
  echo "A merchant with $DEMO_EMAIL already exists. Use another DEMO_EMAIL, or reset the database (docker compose down -v)." >&2
  exit 1
elif [[ "$status" != "201" ]]; then
  echo "Registration failed ($status): $body" >&2
  exit 1
fi
API_KEY=$(json "['apiKey']" <<<"$body")

pay() {  # pay <n> <amount in paise> <customerRef>
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/v1/payments" \
    -H "X-API-Key: $API_KEY" -H "Idempotency-Key: seed-$1" -H 'Content-Type: application/json' \
    -d "{\"amountPaise\":$2,\"customerRef\":\"$3\"}")
  printf '  %-3s %-28s %10s paise -> HTTP %s\n' "$1" "$3" "$2" "$code"
}

echo "Creating payments ..."
n=0
for amount in 25000 49900 120000 7500 260000 15000 99900 45000 318000 6000 180000 22500; do
  n=$((n + 1)); pay $n $amount "sim_success-cust$((n % 5 + 1))"
done
pay 13 55000  "sim_decline-cust2"
pay 14 89900  "sim_decline-cust4"
pay 15 140000 "sim_debit_fail-cust1"        # FAILED with T+1 deadline; auto-reversed within ~30 s
pay 16 32000  "sim_debit_fail-cust3"
pay 17 75000  "sim_timeout_success-cust5"   # bank times out, status inquiry finds SUCCESS
pay 18 64000  "sim_timeout_pending-cust2"   # stays PROCESSING until the inquiry job resolves it
pay 19 41000  "sim_bank_down-cust3"         # bank unreachable, no record -> FAILED, nothing to reverse
pay 20 210000 "cust6"                       # random outcome from the configured rates

cat <<MSG

Done. Sign in to the dashboard with:
  email:    $DEMO_EMAIL
  password: (DEMO_PASSWORD, default in this script)
Merchant API key (shown once, for curl or Swagger "Authorize"): ${API_KEY:0:12}…  (full key printed to seed-demo.key)
MSG
umask 077
echo "$API_KEY" > seed-demo.key
