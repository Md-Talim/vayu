import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const RECEIVER_URL = __ENV.RECEIVER_URL || 'http://localhost:9090/webhook';
const EVENT_TYPE = __ENV.EVENT_TYPE || 'bench.event';
const RATE = Number(__ENV.RATE || 100);
const DURATION = __ENV.DURATION || '60s';
const PRE_VUS = Number(__ENV.PRE_VUS || 50);
const MAX_VUS = Number(__ENV.MAX_VUS || 500);
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;
const SEND_REQUEST_ID = __ENV.SEND_REQUEST_ID === '1';

export const options = {
  scenarios: {
    ingest: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_VUS,
      maxVUs: MAX_VUS,
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
        ...(SEND_REQUEST_ID ? { 'X-Request-Id': key } : {}),
      },
      tags: { name: 'create-event' },
    },
  );

  check(response, {
    'event created': (r) => r.status === 201,
  });
}
