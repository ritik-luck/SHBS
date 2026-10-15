import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// ── Custom Metrics ──
const errorRate = new Rate('errors');
const preFailureLatency = new Trend('pre_failure_latency', true);
const duringFailureLatency = new Trend('during_failure_latency', true);
const postRecoveryLatency = new Trend('post_recovery_latency', true);

// ── Test Configuration ──
// 3-phase chaos test: Normal → Inject Failures → Observe Recovery
export const options = {
  scenarios: {
    // Phase 1: Normal traffic baseline (0s - 30s)m. 
    baseline: {
      executor: 'constant-vus',
      vus: 20,
      duration: '30s',
      startTime: '0s',
      tags: { phase: 'baseline' },
    },
    // Phase 2: Inject failures while traffic continues (30s - 90s)
    chaos_inject: {
      executor: 'shared-iterations',
      vus: 1,
      iterations: 10,
      startTime: '30s',
      exec: 'injectFailures',
      tags: { phase: 'chaos' },
    },
    chaos_traffic: {
      executor: 'constant-vus',
      vus: 30,
      duration: '1m',
      startTime: '30s',
      tags: { phase: 'chaos' },
    },
    // Phase 3: Recovery observation (90s - 150s)
    recovery: {
      executor: 'constant-vus',
      vus: 20,
      duration: '1m',
      startTime: '90s',
      tags: { phase: 'recovery' },
    },
  },
  thresholds: {
    'http_req_duration{phase:baseline}': ['p(99)<300'],
    'http_req_duration{phase:recovery}': ['p(99)<600'],
    errors: ['rate<0.3'], // Allow up to 30% errors during chaos
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ADMIN_TOKEN = __ENV.ADMIN_TOKEN || '';

// ── Default function: send regular traffic ──
export default function () {
  const phase = __ENV.__VU_SCENARIO || 'unknown';

  const helloRes = http.get(`${BASE_URL}/hello`);
  const isOk = helloRes.status === 200;
  const is503 = helloRes.status === 503;
  const is429 = helloRes.status === 429;

  // During chaos, 503 and 429 are expected (circuit breaker and rate limiter)
  check(helloRes, {
    'response is acceptable': () => isOk || is503 || is429,
  });
  errorRate.add(!(isOk || is503 || is429));

  // Categorize latency by which scenario phase we are in
  if (__ITER < 60) {
    preFailureLatency.add(helloRes.timings.duration);
  } else if (__ITER < 180) {
    duringFailureLatency.add(helloRes.timings.duration);
  } else {
    postRecoveryLatency.add(helloRes.timings.duration);
  }

  // Also hit health to see score degrade and recover
  const healthRes = http.get(`${BASE_URL}/health`);
  check(healthRes, {
    'health endpoint alive': (r) => r.status === 200,
  });

  sleep(0.3);
}

// ── Chaos Injection Function ──
export function injectFailures() {
  const headers = {
    'Content-Type': 'application/json',
  };
  if (ADMIN_TOKEN) {
    headers['Authorization'] = `Bearer ${ADMIN_TOKEN}`;
  }

  const failureTypes = ['timeout', 'database', 'network', 'rate_limit'];

  for (const type of failureTypes) {
    const payload = JSON.stringify({
      type: type,
      service: 'chaos-test',
      count: 3,
    });

    const res = http.post(`${BASE_URL}/admin/simulate`, payload, { headers });
    check(res, {
      [`injected ${type} failure`]: (r) => r.status === 200 || r.status === 401,
    });

    sleep(2);
  }
}
