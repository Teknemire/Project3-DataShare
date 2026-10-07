import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import http from 'node:http';
import https from 'node:https';
import { writeFile } from 'node:fs/promises';

if (!process.argv.includes('--confirm-local-storage')) {
  throw new Error('Confirmez que la cible utilise le stockage local avec --confirm-local-storage.');
}

const base = (process.env.BASE_URL ?? 'http://localhost:4200').replace(/\/$/, '');
const email = `slow-download-${Date.now()}@datashare.test`;
const password = 'SlowDownloadTest123!';
const bytes = 64 * 1024 * 1024;
const pauseMs = 40_000;

async function json(path, method, body, token) {
  const headers = token ? { Authorization: `Bearer ${token}` } : {};
  if (!(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(base + path, {
    method, headers,
    body: body instanceof FormData ? body : JSON.stringify(body),
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}`);
  return response.status === 204 ? undefined : response.json();
}

await json('/api/auth/register', 'POST', { email, password });
const { accessToken } = await json('/api/auth/login', 'POST', { email, password });
try {
  const content = Buffer.alloc(bytes, 65);
  const expectedSha256 = createHash('sha256').update(content).digest('hex');
  const body = new FormData();
  body.append('file', new Blob([content], { type: 'text/plain' }), 'slow-download.txt');
  const uploaded = await json('/api/files', 'POST', body, accessToken);
  const token = new URL(uploaded.shareUrl).pathname.split('/').at(-1);
  const access = await json(`/api/shares/${token}/download`, 'POST', {});
  const started = Date.now();
  const transfer = await new Promise((resolve) => {
    const hash = createHash('sha256');
    let received = 0;
    let done = false;
    let resumeTimer;
    const finish = (status) => {
      if (done) return;
      done = true;
      clearTimeout(deadline);
      clearTimeout(resumeTimer);
      resolve({ status, receivedBytes: received, receivedSha256: hash.digest('hex'), elapsedMs: Date.now() - started });
    };
    const transport = base.startsWith('https:') ? https : http;
    const request = transport.get(base + access.downloadUrl, (response) => {
      if (response.statusCode !== 200) {
        finish(`http-${response.statusCode}`);
        response.destroy();
        return;
      }
      response.pause();
      console.log('Téléchargement ouvert ; lecture suspendue pendant 40 secondes.');
      resumeTimer = setTimeout(() => response.resume(), pauseMs);
      response.on('data', chunk => { received += chunk.length; hash.update(chunk); });
      response.on('end', () => finish('complete'));
      response.on('error', () => finish('stream-error'));
      response.on('aborted', () => finish('aborted'));
    });
    request.on('error', () => finish('request-error'));
    const deadline = setTimeout(() => { finish('test-timeout'); request.destroy(); }, 70_000);
  });
  const result = { testedAt: new Date().toISOString(), storage: 'local', expectedBytes: bytes, pauseMs, expectedSha256, ...transfer };
  await writeFile(new URL('./slow-download-result.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify(result, null, 2));
  assert.equal(transfer.status, 'complete');
  assert.equal(transfer.receivedBytes, bytes);
  assert.equal(transfer.receivedSha256, expectedSha256);
} finally {
  await json('/api/users/me', 'DELETE', { password }, accessToken);
}
