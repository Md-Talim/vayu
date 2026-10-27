#!/usr/bin/env bash
set -euo pipefail

OUT=${1:-benchmark-results/metrics.csv}
BASE_URL=${2:-http://localhost:8080/api/v1}

printf 'timestamp,executor_active,queue_depth,queue_remaining,claimed_total,attempts_total,http_requests_total\n' > "$OUT"

while true; do
  metrics=$(curl -fsS "$BASE_URL/actuator/prometheus" 2>/dev/null || true)
  if [ -n "$metrics" ]; then
    active=$(awk '/^vayu_worker_executor_active\{/ {print $2}' <<< "$metrics")
    depth=$(awk '/^vayu_worker_executor_queue_depth\{/ {print $2}' <<< "$metrics")
    remaining=$(awk '/^vayu_worker_executor_queue_remaining\{/ {print $2}' <<< "$metrics")
    claimed=$(awk '/^vayu_worker_claimed_deliveries_total\{/ {print $2}' <<< "$metrics")
    attempts=$(awk '/^vayu_delivery_attempts_total\{/ {sum += $2} END {print sum + 0}' <<< "$metrics")
    requests=$(awk '/^vayu_delivery_http_duration_seconds_count\{/ {sum += $2} END {print sum + 0}' <<< "$metrics")

    printf '%s,%s,%s,%s,%s,%s,%s\n' \
      "$(date +%s)" \
      "${active:-0}" \
      "${depth:-0}" \
      "${remaining:-0}" \
      "${claimed:-0}" \
      "${attempts:-0}" \
      "${requests:-0}" >> "$OUT"
  fi
  sleep 1
done
