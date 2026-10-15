# k6 Load & Chaos Testing

This directory contains load test and chaos engineering scripts for the SHBS project, written in [k6](https://k6.io/).

## Prerequisites

Install k6:
```bash
# macOS
brew install k6

# Linux (Debian/Ubuntu)
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D68
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
```

## Running the Tests

### 1. Baseline Load Test
Measures p50/p95/p99 latency and throughput under normal ramped-up load.

```bash
k6 run load-test.js
```

### 2. Chaos Test (Failure Injection Under Load)
Runs a 3-phase test:
- **Phase 1 (0-30s):** Baseline traffic at 20 VUs.
- **Phase 2 (30-90s):** Injects `timeout`, `database`, `network`, and `rate_limit` failures while 30 VUs maintain traffic. Observes circuit breaker opening and latency spike.
- **Phase 3 (90-150s):** Stops injecting failures and observes the system self-healing back to normal.

```bash
k6 run chaos-test.js
```

### 3. Export to Prometheus (for Grafana dashboards)
If your Prometheus + Grafana stack is running, you can push k6 metrics into it:
```bash
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
  k6 run --out experimental-prometheus-rw load-test.js
```

## Interpreting Results

After each run, k6 prints a summary table. Key metrics to evaluate:

| Metric                  | Meaning                                     | Target        |
| ----------------------- | ------------------------------------------- | ------------- |
| `http_req_duration p95` | 95th percentile response time               | < 500ms       |
| `http_req_duration p99` | 99th percentile response time               | < 1000ms      |
| `errors`                | Percentage of non-acceptable responses      | < 10%         |
| `http_reqs`             | Total requests completed (throughput)       | Maximize      |
| `pre_failure_latency`   | Latency before chaos injection (chaos test) | Baseline      |
| `during_failure_latency`| Latency during failure injection            | May spike     |
| `post_recovery_latency` | Latency after self-healing completes        | ≈ Baseline    |

The key proof is that `post_recovery_latency` should converge back to `pre_failure_latency`, demonstrating that the self-healing pipeline genuinely restores system performance.
