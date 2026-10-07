import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://host.docker.internal:4200';
const accountPassword = 'Performance123!';
const uploadDuration = new Trend('upload_duration', true);
const fileContent = 'DataShare performance test.\n'.repeat(4000);

export const options = {
  scenarios: {
    authenticated_uploads: {
      executor: 'constant-vus',
      vus: 5,
      duration: '15s',
      gracefulStop: '5s',
    },
  },
  thresholds: {
    checks: ['rate==1'],
    http_req_failed: ['rate<0.01'],
    upload_duration: ['p(95)<1000'],
  },
};

export function setup() {
  const email = `performance-${Date.now()}@datashare.test`;
  const registration = http.post(
    `${baseUrl}/api/auth/register`,
    JSON.stringify({ email, password: accountPassword }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(registration, { 'performance account created': (response) => response.status === 201 });

  const login = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ email, password: accountPassword }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  check(login, { 'performance account authenticated': (response) => response.status === 200 });

  return {
    accessToken: login.json('accessToken'),
    email,
  };
}

export default function (data) {
  const upload = http.post(
    `${baseUrl}/api/files`,
    {
      file: http.file(fileContent, `performance-${__VU}-${__ITER}.txt`, 'text/plain'),
      expirationDays: '1',
      tags: 'Performance',
    },
    {
      headers: { Authorization: `Bearer ${data.accessToken}` },
      tags: { operation: 'upload' },
    },
  );
  uploadDuration.add(upload.timings.duration);

  const uploaded = check(upload, {
    'upload returns 201': (response) => response.status === 201,
    'upload returns an id': (response) => Boolean(response.json('id')),
  });

  if (uploaded) {
    const deletion = http.del(`${baseUrl}/api/files/${upload.json('id')}`, null, {
      headers: { Authorization: `Bearer ${data.accessToken}` },
      tags: { operation: 'cleanup' },
    });
    check(deletion, { 'uploaded file cleaned': (response) => response.status === 204 });
  }
}

export function teardown(data) {
  const deletion = http.del(
    `${baseUrl}/api/users/me`,
    JSON.stringify({ password: accountPassword }),
    {
      headers: {
        Authorization: `Bearer ${data.accessToken}`,
        'Content-Type': 'application/json',
      },
    },
  );
  check(deletion, { 'performance account cleaned': (response) => response.status === 204 });
}
