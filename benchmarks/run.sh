#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
cd "$ROOT"

RUN_ID=${RUN_ID:-$(date +%Y%m%d-%H%M%S)}
RESULTS_DIR=${RESULTS_DIR:-benchmarks/results}
DB_NAME=${DB_NAME:-vayu_bench}
API_BASE=${API_BASE:-http://localhost:8080/api/v1}
RECEIVER_URL=${RECEIVER_URL:-http://localhost:9090/webhook}
APP_IMAGE=${APP_IMAGE:-vayu-app:latest}
APP_CONTAINER=${APP_CONTAINER:-vayu-bench-app}
RECEIVER_BIN=${RECEIVER_BIN:-target/bench-receiver}
RECEIVER_PID=${RECEIVER_PID:-target/bench-receiver.pid}
SAMPLER_PID=${SAMPLER_PID:-target/bench-sampler.pid}

DEFAULT_BACKLOG=${DEFAULT_BACKLOG:-100}
CAPACITY_BACKLOG=${CAPACITY_BACKLOG:-10000}
PRELOAD_VUS=${PRELOAD_VUS:-80}
MIXED_RATES=${MIXED_RATES:-"100 200 250 300"}
MIXED_DURATION=${MIXED_DURATION:-60s}
MIXED_PRE_VUS=${MIXED_PRE_VUS:-150}
MIXED_MAX_VUS=${MIXED_MAX_VUS:-1000}
API_RATE=${API_RATE:-100}
API_DURATION=${API_DURATION:-30s}
PHASES=${PHASES:-"default capacity api mixed"}
BUILD_APP=${BUILD_APP:-1}

mkdir -p "$RESULTS_DIR"

log() {
  printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*"
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

require() {
  command -v "$1" >/dev/null 2>&1 || die "missing required command: $1"
}

wait_for_url() {
  local url=$1
  local timeout=${2:-60}
  local started
  started=$(date +%s)

  while true; do
    if curl -fsS "$url" >/dev/null 2>&1; then
      return 0
    fi
    if [ $(( $(date +%s) - started )) -ge "$timeout" ]; then
      return 1
    fi
    sleep 1
  done
}

db_psql() {
  docker compose exec -T db psql -U vayu -d "$DB_NAME" "$@"
}

stop_app() {
  docker rm -f "$APP_CONTAINER" >/dev/null 2>&1 || true
}

start_app() {
  local mode=$1
  stop_app

  local env_args=(
    -e "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/${DB_NAME}"
    -e "SPRING_DATASOURCE_USERNAME=vayu"
    -e "SPRING_DATASOURCE_PASSWORD=vayu"
    -e "SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=64"
    -e "LOGGING_STRUCTURED_FORMAT_CONSOLE="
    -e "LOGGING_LEVEL_ME_TALIM_VAYU=WARN"
    -e "SPRINGDOC_API_DOCS_ENABLED=false"
    -e "SPRINGDOC_SWAGGER_UI_ENABLED=false"
  )

  case "$mode" in
    disabled)
      env_args+=(-e "VAYU_WORKER_ENABLED=false")
      ;;
    default)
      env_args+=(-e "VAYU_WORKER_ENABLED=true")
      ;;
    tuned)
      env_args+=(
        -e "VAYU_WORKER_ENABLED=true"
        -e "VAYU_WORKER_POLL_INTERVAL=10ms"
        -e "VAYU_WORKER_BATCH_SIZE=50"
        -e "VAYU_WORKER_CONCURRENCY=32"
        -e "VAYU_WORKER_QUEUE_CAPACITY=200"
        -e "VAYU_WORKER_LEASE=300s"
        -e "VAYU_WORKER_REAPER_ENABLED=false"
      )
      ;;
    *)
      die "unknown app mode: $mode"
      ;;
  esac

  docker run -d \
    --name "$APP_CONTAINER" \
    --network host \
    "${env_args[@]}" \
    "$APP_IMAGE" >/dev/null

  wait_for_url "$API_BASE/actuator/health" 90 \
    || die "app did not become healthy"
}

stop_receiver() {
  if [ -f "$RECEIVER_PID" ]; then
    kill "$(cat "$RECEIVER_PID")" >/dev/null 2>&1 || true
    rm -f "$RECEIVER_PID"
  fi
}

start_receiver() {
  stop_receiver
  nohup "$RECEIVER_BIN" > "$RESULTS_DIR/receiver.log" 2>&1 &
  echo $! > "$RECEIVER_PID"
  wait_for_url "http://localhost:9090/health" 10 \
    || die "receiver did not become healthy"
}

stop_sampler() {
  if [ -f "$SAMPLER_PID" ]; then
    kill "$(cat "$SAMPLER_PID")" >/dev/null 2>&1 || true
    rm -f "$SAMPLER_PID"
  fi
}

start_sampler() {
  local output=$1
  stop_sampler
  ./benchmarks/sample-metrics.sh "$output" "$API_BASE" >/dev/null 2>&1 &
  echo $! > "$SAMPLER_PID"
}

reset_db() {
  stop_app
  docker compose up -d db >/dev/null

  local started
  started=$(date +%s)
  while true; do
    if docker compose exec -T db pg_isready -U vayu -d vayu >/dev/null 2>&1; then
      break
    fi
    if [ $(( $(date +%s) - started )) -ge 60 ]; then
      die "postgres did not become ready"
    fi
    sleep 1
  done

  docker compose exec -T db psql -U vayu -d postgres -v ON_ERROR_STOP=1 \
    -c "DROP DATABASE IF EXISTS ${DB_NAME} WITH (FORCE);" >/dev/null
  docker compose exec -T db psql -U vayu -d postgres -v ON_ERROR_STOP=1 \
    -c "CREATE DATABASE ${DB_NAME};" >/dev/null
}

wait_for_drain() {
  local label=$1
  local timeout=${2:-300}
  local started
  started=$(date +%s)
  local pending

  while true; do
    pending=$(db_psql -tAc "select count(*) from deliveries where status in ('PENDING', 'DELIVERING')")
    pending=${pending//[[:space:]]/}

    if [ "$pending" = "0" ]; then
      log "$label drained in $(( $(date +%s) - started ))s"
      return 0
    fi

    if [ $(( $(date +%s) - started )) -ge "$timeout" ]; then
      log "$label still has $pending pending deliveries after ${timeout}s"
      return 1
    fi

    sleep 1
  done
}

collect_results() {
  local label=$1
  local output="$RESULTS_DIR/${RUN_ID}-${label}-db.txt"

  {
    printf '## delivery counts\n'
    db_psql -f - < benchmarks/sql/delivery-counts.sql
    printf '\n## delivery latency\n'
    db_psql -f - < benchmarks/sql/delivery-latency.sql
    printf '\n## receiver\n'
    curl -fsS http://localhost:9090/stats
    printf '\n'
  } | tee "$output"
}

collect_api_results() {
  local label=$1
  local output="$RESULTS_DIR/${RUN_ID}-${label}-db.txt"

  {
    printf '## event counts\n'
    db_psql -f - < benchmarks/sql/event-counts.sql
    printf '\n## receiver\n'
    curl -fsS http://localhost:9090/stats
    printf '\n'
  } | tee "$output"
}

summarize_metrics() {
  local label=$1
  local csv="$RESULTS_DIR/${RUN_ID}-${label}-metrics.csv"

  if [ ! -f "$csv" ]; then
    return 0
  fi

  awk -F, '
    NR > 1 {
      samples++
      if ($2 + 0 > max_active) max_active = $2 + 0
      if ($3 + 0 > max_queue) max_queue = $3 + 0
      active_sum += $2 + 0
    }
    END {
      printf "%s: samples=%d max_active=%d max_queue_depth=%d avg_active=%.1f\n",
        "'"$label"'", samples, max_active, max_queue, samples ? active_sum / samples : 0
    }
  ' "$csv"
}

run_preload() {
  local label=$1
  local iterations=$2
  local vus=$3

  k6 run \
    --summary-export="$RESULTS_DIR/${RUN_ID}-${label}-preload.json" \
    -e "RUN_ID=${RUN_ID}-${label}" \
    -e "ITERATIONS=${iterations}" \
    -e "VUS=${vus}" \
    -e "BASE_URL=${API_BASE}" \
    -e "RECEIVER_URL=${RECEIVER_URL}" \
    benchmarks/k6/preload.js
}

run_ingest() {
  local label=$1
  local rate=$2
  local duration=${3:-$MIXED_DURATION}

  k6 run \
    --summary-export="$RESULTS_DIR/${RUN_ID}-${label}-ingest.json" \
    -e "RUN_ID=${RUN_ID}-${label}" \
    -e "RATE=${rate}" \
    -e "DURATION=${duration}" \
    -e "PRE_VUS=${MIXED_PRE_VUS}" \
    -e "MAX_VUS=${MIXED_MAX_VUS}" \
    -e "BASE_URL=${API_BASE}" \
    -e "RECEIVER_URL=${RECEIVER_URL}" \
    benchmarks/k6/ingest.js
}

default_worker_phase() {
  local label="default-worker"

  log "default worker: reset database"
  reset_db
  start_receiver
  start_app disabled
  log "default worker: preload ${DEFAULT_BACKLOG} deliveries"
  run_preload "$label" "$DEFAULT_BACKLOG" 10
  stop_app
  start_app default
  start_sampler "$RESULTS_DIR/${RUN_ID}-${label}-metrics.csv"
  wait_for_drain "$label" 180
  stop_sampler
  collect_results "$label"
  summarize_metrics "$label"
  stop_app
}

capacity_phase() {
  local label="delivery-capacity"

  log "delivery capacity: reset database"
  reset_db
  start_receiver
  start_app disabled
  log "delivery capacity: preload ${CAPACITY_BACKLOG} deliveries"
  run_preload "$label" "$CAPACITY_BACKLOG" "$PRELOAD_VUS"
  stop_app
  start_app tuned
  start_sampler "$RESULTS_DIR/${RUN_ID}-${label}-metrics.csv"
  wait_for_drain "$label" 300
  stop_sampler
  collect_results "$label"
  summarize_metrics "$label"
  stop_app
}

api_only_phase() {
  local label="api-only-${API_RATE}rps"

  log "api only: reset database"
  reset_db
  start_receiver
  start_app disabled
  start_sampler "$RESULTS_DIR/${RUN_ID}-${label}-metrics.csv"
  log "api only: run ingest for ${API_DURATION}"
  run_ingest "$label" "$API_RATE" "$API_DURATION"
  stop_sampler
  collect_api_results "$label"
  summarize_metrics "$label"
  stop_app
}

mixed_phase() {
  local rate=$1
  local label="mixed-${rate}rps"

  log "mixed ${rate}/s: reset database"
  reset_db
  start_receiver
  start_app tuned
  start_sampler "$RESULTS_DIR/${RUN_ID}-${label}-metrics.csv"
  log "mixed ${rate}/s: run ingest for ${MIXED_DURATION}"
  run_ingest "$label" "$rate"
  wait_for_drain "$label" 300
  stop_sampler
  collect_results "$label"
  summarize_metrics "$label"
  stop_app
}

cleanup() {
  stop_sampler
  stop_app
  stop_receiver
}

trap cleanup EXIT

require docker
require curl
require k6
require go
require awk

if [ "$BUILD_APP" = "1" ]; then
  log "building application image"
  docker compose build app
fi

if [ ! -x "$RECEIVER_BIN" ]; then
  log "building benchmark receiver"
  (cd benchmarks/receiver && go build -o "../../${RECEIVER_BIN}" .)
fi

log "run id: $RUN_ID"
log "results: $RESULTS_DIR"

for phase in $PHASES; do
  case "$phase" in
    default)
      default_worker_phase
      ;;
    capacity)
      capacity_phase
      ;;
    api)
      api_only_phase
      ;;
    mixed)
      for rate in $MIXED_RATES; do
        mixed_phase "$rate"
      done
      ;;
    *)
      die "unknown phase: $phase"
      ;;
  esac
done

log "benchmark complete"
