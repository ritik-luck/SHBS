import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import exec from 'k6/execution';

// ── Custom Metrics ──
const errorRate = new Rate('errors');
const orderLatency = new Trend('order_latency', true);

// ── Test Configuration ──
export const options = {
  stages: [
    { duration: '30s', target: 20 },   // Ramp up
    { duration: '1m',  target: 50 },   // Sustained load
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    errors:           ['rate<0.1'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  // ── Health Endpoint ──
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  check(healthRes, {
    'health status is 200': (r) => r.status === 200,
  });

  // ── Order Creation Endpoint (Protected by Circuit Breaker) ──
  const payload = JSON.stringify({
    product: `PROD-${Math.floor(Math.random() * 1000)}`,
    quantity: Math.floor(Math.random() * 5) + 1,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-test-${exec.vu.idInTest}-${exec.scenario.iterationInInstance}`,
    },
  };

  const orderRes = http.post(`${BASE_URL}/api/orders`, payload, params);
  orderLatency.add(orderRes.timings.duration);
  
  const isAcceptable = orderRes.status === 200 || orderRes.status === 503 || orderRes.status === 429;
  check(orderRes, {
    'order returns acceptable status (200/503/429)': () => isAcceptable,
  });
  errorRate.add(!isAcceptable);

  sleep(0.5);
}
