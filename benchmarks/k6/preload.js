import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const RECEIVER_URL = __ENV.RECEIVER_URL || 'http://localhost:9090/webhook';
const EVENT_TYPE = __ENV.EVENT_TYPE || 'bench.event';
const ITERATIONS = Number(__ENV.ITERATIONS || 10000);
const VUS = Number(__ENV.VUS || 50);
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

export const options = {
  scenarios: {
    preload: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: ITERATIONS,
      maxDuration: '10m',
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  const response = http.post(
    `${BASE_URL}/endpoints`,
    JSON.stringify({
      url: RECEIVER_URL,
      eventTypes: [EVENT_TYPE],
    }),
    {
      headers: { 'Content-Type': 'application/json' },
      tags: { name: 'create-endpoint' },
    },
  );

  const created = check(response, {
    'endpoint created': (r) => r.status === 201,
  });

  if (!created) {
    throw new Error(`could not create endpoint: ${response.status} ${response.body}`);
  }

  return { endpointId: response.json('id') };
}

export default function () {
  const key = `${RUN_ID}-${__VU}-${__ITER}-${Math.random().toString(36).slice(2)}`;
  const payload = JSON.stringify({
    id: key,
    amount: 100,
    currency: 'USD',
  });

  const response = http.post(
    `${BASE_URL}/events`,
    JSON.stringify({
      type: EVENT_TYPE,
      payload,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': key,
      },
      tags: { name: 'create-event' },
    },
  );

  check(response, {
    'event created': (r) => r.status === 201,
  });
}
