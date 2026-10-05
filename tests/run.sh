#!/usr/bin/env bash
# Builds an isolated stack (Client-API, Worker, Notification, PostgreSQL, RabbitMQ, Mailpit,
# Prometheus, Grafana), runs the integration and load tests and writes tests/reports/<run>/.
#
#   tests/run.sh [--profile smoke|load|stress|soak] [--rate N] [--duration 2m] [--items N]
#                [--only integration|load] [--keep]
set -euo pipefail
cd "$(dirname "$0")"

PROFILE=load
RATE=10
DURATION=""
ITEMS=50000
ONLY=""
KEEP=false

while [ $# -gt 0 ]; do
  case "$1" in
    --profile)  PROFILE=$2; shift 2 ;;
    --rate)     RATE=$2; shift 2 ;;
    --duration) DURATION=$2; shift 2 ;;
    --items)    ITEMS=$2; shift 2 ;;
    --only)     ONLY=$2; shift 2 ;;
    --keep)     KEEP=true; shift ;;
    -h|--help)  sed -n '2,6p' "$0"; exit 0 ;;
    *)          echo "unknown option: $1" >&2; exit 2 ;;
  esac
done
if [ -z "$DURATION" ] && [ "$PROFILE" = load ]; then
  DURATION=2m
fi

RUN_ID=$(date +%Y%m%d-%H%M%S)
export TESTS_REPORT_DIR="$PWD/reports/$RUN_ID"
mkdir -p "$TESTS_REPORT_DIR/integration" "$TESTS_REPORT_DIR/load"
chmod -R a+rwx "$TESTS_REPORT_DIR"

dc() { docker compose -f docker-compose.yml "$@"; }

finish() {
  status=$?
  if [ "$KEEP" = true ]; then
    echo "Stack kept running:"
    echo "  Grafana     http://localhost:${TESTS_GRAFANA_PORT:-13000}"
    echo "  Prometheus  http://localhost:${TESTS_PROMETHEUS_PORT:-19090}"
    echo "  Mailpit     http://localhost:${TESTS_MAILPIT_PORT:-18025}"
    echo "Stop it: docker compose -f tests/docker-compose.yml down -v"
  else
    dc down -v --remove-orphans >/dev/null 2>&1 || true
  fi
  exit "$status"
}
trap finish EXIT

docker volume create derechi-tests-m2 >/dev/null

echo "==> build and start the stack"
dc up -d --build --quiet-pull postgres rabbitmq mailpit prometheus grafana client-api worker notification
dc run --rm --quiet-pull ready

integration_status=0
load_status=0

if [ "$ONLY" != load ]; then
  echo "==> integration tests"
  dc run --rm --quiet-pull integration || integration_status=$?
fi

if [ "$ONLY" != integration ]; then
  echo "==> seed: $ITEMS lost + $ITEMS found notices"
  dc exec -T postgres psql -U derechi -d derechi -q -v ON_ERROR_STOP=1 -v items="$ITEMS" < load/seed/seed.sql
  echo "==> load test: $PROFILE"
  dc run --rm --quiet-pull -e PROFILE="$PROFILE" -e RATE="$RATE" -e DURATION="$DURATION" -e TESTID="$RUN_ID" k6 \
    || load_status=$?
fi

cat > "$TESTS_REPORT_DIR/run.json" <<EOF
{"id": "$RUN_ID", "commit": "$(git rev-parse --short HEAD 2>/dev/null || echo unknown)",
 "profile": "$PROFILE", "rate": "$RATE", "duration": "${DURATION:-default}", "items": $ITEMS,
 "only": "$ONLY", "integration_exit": $integration_status, "load_exit": $load_status}
EOF
dc run --rm --quiet-pull report
ln -sfn "$RUN_ID" reports/latest
echo "Report: tests/reports/$RUN_ID/index.html"

[ "$integration_status" -eq 0 ] && [ "$load_status" -eq 0 ]
