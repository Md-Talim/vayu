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

| Setting                      | Default | Tuned   |
| ---------------------------- | ------- | ------- |
| `vayu.worker.poll-interval`  | `1s`    | `10ms`  |
| `vayu.worker.batch-size`     | `4`     | `50`    |
| `vayu.worker.concurrency`    | `4`     | `32`    |
| `vayu.worker.queue-capacity` | `4`     | `1000`  |
| `vayu.worker.lease`          | `60s`   | `300s`  |
| `vayu.worker.reaper.enabled` | `true`  | `false` |
| Hikari maximum pool size     | default | `64`    |

The tuned profile is a benchmark configuration, not the default. It raises the effective one-batch-per-poll ceiling to about 5,000 deliveries/sec and gives the executor enough queue and database connections to keep 32 delivery threads busy. The reaper is disabled because the test measures a finite backlog and does not need lease recovery.

For both profiles, application logging was set to WARN and the ECS console format was disabled. This keeps log I/O out of the measured path. API docs were disabled.

## Results

### Default worker

100 deliveries, default worker:

- Drain time: 24.817s
- Throughput: 4.03 deliveries/sec
- Delivered: 100/100
- Failed: 0
- Retries: 0

This matches the default cap: one batch of 4 per 1s poll.

Raw data: [`20261027-core-default-worker-db.txt`](results/20261027-core-default-worker-db.txt), [`20261027-core-default-worker-preload.json`](results/20261027-core-default-worker-preload.json), [`20261027-core-default-worker-metrics.csv`](results/20261027-core-default-worker-metrics.csv).

### Delivery capacity

10,000 deliveries preloaded into PostgreSQL, then the tuned worker started:

- Drain time: 27.527s
- Throughput: 363.29 deliveries/sec from database timestamps
- Receiver rate: 371.13 requests/sec
- Delivered: 10,000/10,000
- Failed: 0
- Retries: 0
- Average active threads: 26.3/32 (82%)
- Max executor queue depth: 117

The delivery p50/p95/p99 for this test is not shown because the preload ran before the worker started, so the end-to-end latency mostly measures backlog wait. Use the mixed runs for latency.

Raw data: [`20261027-core-delivery-capacity-db.txt`](results/20261027-core-delivery-capacity-db.txt), [`20261027-core-delivery-capacity-preload.json`](results/20261027-core-delivery-capacity-preload.json), [`20261027-core-delivery-capacity-metrics.csv`](results/20261027-core-delivery-capacity-metrics.csv).

### Mixed ingest and delivery

Each run targets a fixed event rate for 60 seconds, with the tuned worker running. `Actual ingest` is the k6 iteration rate. `Delivered` is the number of accepted events that reached `DELIVERED`.

| Target  | Actual ingest | API p50 | API p95 | API p99 | Delivery p50 | Delivery p95 | Delivery p99 | Avg active threads | Max executor queue | Delivered     |
| ------- | ------------- | ------- | ------- | ------- | ------------ | ------------ | ------------ | ------------------ | ------------------ | ------------- |
| 100/sec | 99.3/sec      | 5.7ms   | 863ms   | 1.60s   | 17ms         | 1.26s        | 1.61s        | 2.4/32 (7%)        | 27                 | 5,976/5,976   |
| 200/sec | 190.6/sec     | 42.8ms  | 1.19s   | 1.65s   | 1.35s        | 5.45s        | 5.89s        | 14.6/32 (46%)      | 255                | 11,477/11,477 |
| 300/sec | 278.6/sec     | 136ms   | 1.46s   | 2.30s   | 25.2s        | 31.3s        | 31.7s        | 27.0/32 (84%)      | 470                | 16,780/16,780 |

- `API` is k6 `http_req_duration`.
- `Delivery` is `completed_at - created_at` in PostgreSQL. It includes the ingest write and commit, queueing and worker time.
- `Avg active threads` is the mean number of delivery threads running over all samples in the run, including idle samples. The maximum was 32/32 in every tuned run.
- `Max executor queue` is the maximum executor queue depth observed by the one-second Prometheus sampler. It is not the full database backlog.
- `Delivered` counts accepted events. All accepted events were delivered, with no failed deliveries and no retries.

k6 dropped 25, 524 and 1,221 scheduled iterations at the three targets. A dropped iteration is a request k6 did not start because its VU pool or arrival schedule was saturated. Those requests were never sent to vayu and are not events lost by the service. The 300/sec target is also limited by the 600-VU ceiling in the current runner, so 278.6/sec is a lower bound on vayu's ceiling.

Raw data:

- [`20261027-core-mixed-100rps-ingest.json`](results/20261027-core-mixed-100rps-ingest.json), [`20261027-core-mixed-100rps-db.txt`](results/20261027-core-mixed-100rps-db.txt), [`20261027-core-mixed-100rps-metrics.csv`](results/20261027-core-mixed-100rps-metrics.csv)
- [`20261027-core-mixed-200rps-ingest.json`](results/20261027-core-mixed-200rps-ingest.json), [`20261027-core-mixed-200rps-db.txt`](results/20261027-core-mixed-200rps-db.txt), [`20261027-core-mixed-200rps-metrics.csv`](results/20261027-core-mixed-200rps-metrics.csv)
- [`20261027-core-mixed-300rps-ingest.json`](results/20261027-core-mixed-300rps-ingest.json), [`20261027-core-mixed-300rps-db.txt`](results/20261027-core-mixed-300rps-db.txt), [`20261027-core-mixed-300rps-metrics.csv`](results/20261027-core-mixed-300rps-metrics.csv)

### API-only isolation

To separate ingestion from delivery, a separate run kept the worker disabled and targeted 100 events/sec for 30 seconds:

- Actual ingest: 98.3/sec
- API p50: 6.1ms
- API p95: 436ms
- API p99: 842ms
- Accepted events: 2,961
- Scheduled iterations dropped by k6: 39

The worker was not running, so the tail is in the event write path, not in delivery. The median is single-digit milliseconds; a small fraction of writes are much slower. The mixed runs add worker database contention on top of that baseline.

Raw data: [`20261027-api-only-100rps-ingest.json`](results/20261027-api-only-100rps-ingest.json).

## What the numbers mean

- The default worker is throughput-limited by design: one batch per poll gives about `batch-size / poll-interval` deliveries per second, or 4/sec with the defaults.
- The tuned worker can drain about 360 to 450 deliveries/sec in isolation on this host.
- Under mixed load, ingestion is the first bottleneck. At 100/sec the API p95 is already 863ms while the worker averages only 7% active threads. At 300/sec the API accepts 278.6/sec and the worker falls behind, so delivery latency grows to 31.3s p95.
- Every accepted event was delivered. The service did not lose events or exhaust retries in any run.

## Reproducing

From the repository root:

```bash
./benchmarks/run.sh
```

Prerequisites: Docker with Compose, k6, Go 1.22 or newer, curl and awk.

The script builds the application image and receiver, resets the `vayu_bench` database, and runs the default worker, delivery capacity and mixed phases. Results are written to `benchmarks/results/`. The script leaves the `db` service running; stop it with `docker compose down` when finished.

Useful environment variables:

```bash
RUN_ID=my-run MIXED_RATES="100 200 300" ./benchmarks/run.sh
PHASES=mixed MIXED_RATES="200" ./benchmarks/run.sh
```

The script uses Linux host networking for the application container so it can reach PostgreSQL and the receiver on `localhost`. On macOS or Windows, run the application with port mappings and set `RECEIVER_URL=http://host.docker.internal:9090/webhook` so the app can reach the receiver.

## Raw results

The recorded run is `20261027-core`:

- `20261027-core-default-worker-*`
- `20261027-core-delivery-capacity-*`
- `20261027-core-mixed-100rps-*`
- `20261027-core-mixed-200rps-*`
- `20261027-core-mixed-300rps-*`
- `20261027-api-only-100rps-ingest.json`

Each set contains the k6 summary, the Prometheus sampler CSV, and the database counts and latency output. Console logs from a run are local artifacts and are not committed.

## Caveats

- Single run per rate. The host is shared with the OS, Docker, k6 and the receiver. Expect variance.
- The 300/sec target is not fully reached because the 600-VU ceiling is marginal at that latency. Increase `MIXED_MAX_VUS` before treating 278.6/sec as vayu's ceiling.
- PostgreSQL uses default durability settings. `synchronous_commit` and `fsync` were not disabled.
- Application logging was reduced to WARN and ECS console output was disabled. This isolates the delivery pipeline but does not measure log formatting or I/O.
- The receiver always returns `200`, so retry, backoff and failure paths are not exercised by this benchmark.
- The tuned worker profile is not the default. The default profile is intentionally conservative.
- The benchmark uses one endpoint and one subscription, so each event creates one delivery. Fan-out to many endpoints will change the ingestion cost.
