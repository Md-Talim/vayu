# Benchmarks

These benchmarks measure the two paths that matter in vayu:

1. HTTP ingestion: `POST /api/v1/events` with one endpoint subscription, so each event creates one delivery.
2. Delivery: the worker claims the delivery, signs the payload, POSTs it to a Go receiver, and records the attempt in PostgreSQL.

The load generator is k6. The receiver is a small Go server that returns `200` and records request counts. PostgreSQL is the source of truth for accepted events and delivery state. The application and database run in Docker; k6 and the receiver run on the host.

## Environment

- CPU: 11th Gen Intel Core i3-1115G4, 2 cores, 4 threads
- RAM: 11 GiB
- Disk: NVMe SSD
- OS: Fedora Linux 44
- Java: 21 runtime in the application image
- PostgreSQL: 18.6 in Docker, default durability settings
- k6: 2.3.0
- Go receiver: Go 1.26

The host is a laptop. The application, PostgreSQL, k6 and the receiver share the same CPU and disk. These are local numbers, not production capacity.

## Worker profiles

| Setting                            | Default | Tuned  |
| ---------------------------------- | ------- | ------ |
| `vayu.worker.poll-interval`        | `100ms` | `10ms` |
| `vayu.worker.batch-size`           | `20`    | `50`   |
| `vayu.worker.concurrency`          | `16`    | `32`   |
| `vayu.worker.queue-capacity`       | `200`   | `200`  |
| `vayu.worker.max-batches-per-poll` | `10`    | `10`   |
| `vayu.worker.lease`                | `60s`   | `300s` |
| `vayu.worker.reaper.enabled`       | `true`  | `false` |
| Hikari maximum pool size           | default | `64`   |

The tuned profile is a benchmark configuration, not the default. It gives the executor enough database connections to keep 32 delivery threads busy. The reaper is disabled because the test measures a finite backlog and does not need lease recovery.

For both profiles, application logging was set to WARN and the ECS console format was disabled. API docs were disabled. This keeps log and documentation I/O out of the measured path.

## Results

The final numbers use `20261027-optimized` for the default worker and API-only phases, and `20261027-optimized-q200` for the capacity and mixed phases. The q200 follow-up reran the tuned phases after the executor queue was reduced from 1,000 to 200. The default and API-only phases do not use the tuned queue.

### Default worker

100 deliveries, default worker:

- Drain time: 0.830s
- Drain rate: 120.53 deliveries/sec
- Delivered: 100/100
- Failed: 0
- Retries: 0
- Average active threads: 8.0/16 (50%)
- Max executor queue depth: 66

The old default claimed one batch of 4 every second, which capped the drain at about 4/sec. The default now uses a 100ms poll, 20 deliveries per batch, 16 threads, a 200-slot queue and up to 10 batches per poll. This is a short drain of a 100-delivery backlog, not a steady-state capacity test.

Raw data: [`20261027-optimized-default-worker-db.txt`](results/20261027-optimized-default-worker-db.txt), [`20261027-optimized-default-worker-preload.json`](results/20261027-optimized-default-worker-preload.json), [`20261027-optimized-default-worker-metrics.csv`](results/20261027-optimized-default-worker-metrics.csv).

### Delivery capacity

10,000 deliveries preloaded into PostgreSQL, then the tuned worker started:

- Drain time: 21.585s
- Throughput: 463.29 deliveries/sec from database timestamps
- Receiver rate: 493.17 requests/sec
- Delivered: 10,000/10,000
- Failed: 0
- Retries: 0
- Average active threads: 30.7/32 (96%)
- Max executor queue depth: 184

The delivery p50/p95/p99 for this test is not shown because the preload ran before the worker started, so the end-to-end latency mostly measures backlog wait. Use the mixed runs for latency.

Raw data: [`20261027-optimized-q200-delivery-capacity-db.txt`](results/20261027-optimized-q200-delivery-capacity-db.txt), [`20261027-optimized-q200-delivery-capacity-preload.json`](results/20261027-optimized-q200-delivery-capacity-preload.json), [`20261027-optimized-q200-delivery-capacity-metrics.csv`](results/20261027-optimized-q200-delivery-capacity-metrics.csv).

### Mixed ingest and delivery

Each run targets a fixed event rate for 60 seconds, with the tuned worker running. `Actual ingest` is the k6 iteration rate. `Delivered` is the number of accepted events that reached `DELIVERED`.

| Target  | Actual ingest | API p50 | API p95 | API p99 | Delivery p50 | Delivery p95 | Delivery p99 | Avg active threads | Max executor queue | Delivered       |
| ------- | ------------- | ------- | ------- | ------- | ------------ | ------------ | ------------ | ------------------ | ------------------ | --------------- |
| 100/sec | 98.9/sec      | 4.9ms   | 618ms   | 1.17s   | 15ms         | 720ms        | 1.61s        | 1.2/32 (4%)        | 24                 | 6,001/6,001     |
| 200/sec | 192.3/sec     | 15.8ms  | 1.10s   | 2.08s   | 41ms         | 3.66s        | 4.73s        | 8.9/32 (28%)       | 179                | 11,574/11,574   |
| 250/sec | 232.5/sec     | 111.7ms | 2.14s   | 3.00s   | 13.5s        | 17.7s        | 18.2s        | 30.2/32 (94%)      | 190                | 14,008/14,008   |
| 300/sec | 271.1/sec     | 212.7ms | 1.85s   | 2.60s   | 25.9s        | 29.1s        | 29.7s        | 29.6/32 (93%)      | 190                | 16,672/16,672   |

- `API` is k6 `http_req_duration`.
- `Delivery` is `completed_at - created_at` in PostgreSQL. It includes the ingest write and commit, queueing and worker time.
- `Avg active threads` is the mean number of delivery threads running over all samples in the run, including idle samples. The maximum was 32/32 in every tuned run.
- `Max executor queue` is the maximum executor queue depth observed by the one-second Prometheus sampler. It is not the full database backlog.
- `Delivered` counts accepted events. All accepted events were delivered, with no failed deliveries and no retries.

k6 dropped 0, 427, 993 and 1,328 scheduled iterations at 100, 200, 250 and 300/sec. A dropped iteration is a request k6 did not start because its VU pool or arrival schedule was saturated. Those requests were never sent to vayu and are not events lost by the service. The 250 and 300/sec targets are above the worker's comfortable range on this host, so the delivery tail grows even though the API keeps accepting events.

Raw data:

- [`20261027-optimized-q200-mixed-100rps-ingest.json`](results/20261027-optimized-q200-mixed-100rps-ingest.json), [`20261027-optimized-q200-mixed-100rps-db.txt`](results/20261027-optimized-q200-mixed-100rps-db.txt), [`20261027-optimized-q200-mixed-100rps-metrics.csv`](results/20261027-optimized-q200-mixed-100rps-metrics.csv)
- [`20261027-optimized-q200-mixed-200rps-ingest.json`](results/20261027-optimized-q200-mixed-200rps-ingest.json), [`20261027-optimized-q200-mixed-200rps-db.txt`](results/20261027-optimized-q200-mixed-200rps-db.txt), [`20261027-optimized-q200-mixed-200rps-metrics.csv`](results/20261027-optimized-q200-mixed-200rps-metrics.csv)
- [`20261027-optimized-q200-mixed-250rps-ingest.json`](results/20261027-optimized-q200-mixed-250rps-ingest.json), [`20261027-optimized-q200-mixed-250rps-db.txt`](results/20261027-optimized-q200-mixed-250rps-db.txt), [`20261027-optimized-q200-mixed-250rps-metrics.csv`](results/20261027-optimized-q200-mixed-250rps-metrics.csv)
- [`20261027-optimized-q200-mixed-300rps-ingest.json`](results/20261027-optimized-q200-mixed-300rps-ingest.json), [`20261027-optimized-q200-mixed-300rps-db.txt`](results/20261027-optimized-q200-mixed-300rps-db.txt), [`20261027-optimized-q200-mixed-300rps-metrics.csv`](results/20261027-optimized-q200-mixed-300rps-metrics.csv)

### API-only isolation

To separate ingestion from delivery, the `api` phase keeps the worker disabled and targets 100 events/sec for 30 seconds:

- Actual ingest: 97.9/sec
- API p50: 6.6ms
- API p95: 1.04s
- API p99: 1.62s
- Accepted events: 2,972
- Scheduled iterations dropped by k6: 29

The worker was not running, so the tail is in the event write path, not in delivery. The median is single-digit milliseconds; a small fraction of writes are much slower. The mixed 100/sec run had a lower API p95 of 618ms, so the API-only number is also affected by host variance. It is a useful lower bound, not a stable baseline.

Raw data: [`20261027-optimized-api-only-100rps-ingest.json`](results/20261027-optimized-api-only-100rps-ingest.json), [`20261027-optimized-api-only-100rps-db.txt`](results/20261027-optimized-api-only-100rps-db.txt), [`20261027-optimized-api-only-100rps-metrics.csv`](results/20261027-optimized-api-only-100rps-metrics.csv).

## Bottleneck findings

The first benchmark found two worker limits that were worth fixing.

1. The poller claimed exactly one batch per poll. With the old defaults, that capped the worker at 4 deliveries/sec. `DeliveryWorker.runOnce()` now keeps claiming batches while the executor has at least one batch of queue room, up to `max-batches-per-poll`. The default was also raised to 100ms, 20 per batch and 16 threads. The 100-delivery default drain went from 4.03/sec to 120.53/sec. The tuned capacity went from 405/sec in the repeat baseline to 463/sec.

2. Completion used a read-modify-write path: it locked the delivery with `findByIdForUpdate()`, loaded the attempt, changed both entities and flushed them. `DeliveryCompletionService` now uses a conditional `UPDATE` fenced by `status = 'DELIVERING'` and `lock_token`, followed by one attempt update. This removes a pessimistic lock and two reads from every delivery. The capacity and mixed runs above include this change, together with the claim-loop change. The two changes were benchmarked together, so the exact split is not claimed.

A third result was about queue size, not code. The same tuned worker with a 1,000-slot executor queue at 200/sec averaged 30.1 active threads, reached a queue depth of 989 and produced a delivery p95 of 18.8s. With a 200-slot queue it averaged 8.9 active threads, reached 179 queued deliveries and produced a delivery p95 of 3.66s, while actual ingest was 192.3/sec instead of 188.0/sec. The deep queue did not improve throughput; it hid backpressure and added latency. The final tuned profile uses 200 slots.

The API tail is still the first bottleneck. The worker-disabled run had a 1.04s API p95 and a 6.6ms median. That is not a worker queue problem. The next step is server-side timing for the event write, JVM pauses and Tomcat/Hikari waits, not a larger worker pool.

Deep-queue comparison data: [`20261027-optimized-mixed-200rps-ingest.json`](results/20261027-optimized-mixed-200rps-ingest.json), [`20261027-optimized-mixed-200rps-db.txt`](results/20261027-optimized-mixed-200rps-db.txt), [`20261027-optimized-mixed-200rps-metrics.csv`](results/20261027-optimized-mixed-200rps-metrics.csv).

## What the numbers mean

- The default worker drains a 100-delivery backlog at about 120 deliveries/sec on this host. It is no longer limited to one batch per poll.
- The tuned worker can drain about 460 deliveries/sec in isolation. The receiver saw 493 requests/sec on the same run.
- Under mixed load, 100 and 200/sec are comfortable. At 250/sec the worker is almost fully occupied and delivery latency grows. At a 300/sec target the API accepts 271/sec and the delivery p95 reaches 29.1s.
- Ingestion is the first bottleneck. At 100/sec the API p95 is 618ms while the worker averages 1.2 active threads. The worker becomes the second bottleneck at 250/sec and above.
- Every accepted event was delivered. The service did not lose events or exhaust retries in any run.

## Reproducing

From the repository root:

```bash
./benchmarks/run.sh
```

Prerequisites: Docker with Compose, k6, Go 1.22 or newer, curl and awk.

The script builds the application image and receiver, resets the `vayu_bench` database, and runs the default worker, delivery capacity, API-only and mixed phases. The default mixed rates are 100, 200, 250 and 300/sec. Results are written to `benchmarks/results/`. The script leaves the `db` service running; stop it with `docker compose down` when finished.

Useful environment variables:

```bash
RUN_ID=my-run MIXED_RATES="100 200 250 300" ./benchmarks/run.sh
PHASES=mixed MIXED_RATES="200" ./benchmarks/run.sh
PHASES=api API_RATE=100 API_DURATION=30s ./benchmarks/run.sh
```

The script uses Linux host networking for the application container so it can reach PostgreSQL and the receiver on `localhost`. On macOS or Windows, run the application with port mappings and set `RECEIVER_URL=http://host.docker.internal:9090/webhook` so the app can reach the receiver.

## Raw results

The final numbers are in:

- `20261027-optimized-default-worker-*`
- `20261027-optimized-api-only-100rps-*`
- `20261027-optimized-q200-delivery-capacity-*`
- `20261027-optimized-q200-mixed-100rps-*`
- `20261027-optimized-q200-mixed-200rps-*`
- `20261027-optimized-q200-mixed-250rps-*`
- `20261027-optimized-q200-mixed-300rps-*`
- `20261027-optimized-mixed-200rps-*` (the 1,000-slot queue comparison)

Each set contains the k6 summary, the Prometheus sampler CSV, and the database counts and latency output. Console logs from a run are local artifacts and are not committed.

## Caveats

- Single run per rate. The host is shared with the OS, Docker, k6 and the receiver. Expect variance.
- The final default and API-only numbers come from the first optimized run. The capacity and mixed numbers come from the q200 follow-up. The tuned queue is the only configuration difference and it does not apply to the default or API-only phases.
- The 250 and 300/sec targets are not fully reached. k6 dropped 993 and 1,328 scheduled iterations, and the worker received 233 and 223 requests/sec. The 300/sec API p95 is lower than the 250/sec API p95 because of host variance.
- PostgreSQL uses default durability settings. `synchronous_commit` and `fsync` were not disabled.
- Application logging was reduced to WARN and ECS console output was disabled. This isolates the delivery pipeline but does not measure log formatting or I/O.
- The receiver always returns `200`, so retry, backoff and failure paths are not exercised by this benchmark.
- The tuned worker profile is not the default.
- The benchmark uses one endpoint and one subscription, so each event creates one delivery. Fan-out to many endpoints will change the ingestion cost.
