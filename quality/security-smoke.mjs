/** Vérification sans création de données, à lancer sur une instance de test dédiée. */
import { writeFileSync } from 'node:fs';

const baseUrl = process.argv[2];
if (!baseUrl) throw new Error('Usage : node quality/security-smoke.mjs http://localhost:<port-api-test>');
const url = new URL(baseUrl);
if (!['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) {
  throw new Error('Ce contrôle de débit est réservé à une instance locale de test.');
}
const statuses = [];
let retryAfter;
for (let i = 0; i < 11; i++) {
  const response = await fetch(`${baseUrl}/api/auth/login`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: 'rate-smoke@datashare.test', password: 'not-a-real-password' }),
  });
  statuses.push(response.status);
  if (response.status === 429) retryAfter = response.headers.get('Retry-After');
  await response.text();
}
const healthResponse = await fetch(`${baseUrl}/actuator/health`);
const health = await healthResponse.json();
const passed = statuses.slice(0, 10).every(status => status === 401)
  && statuses[10] === 429 && Number(retryAfter) >= 1
  && healthResponse.status === 200 && health.status === 'UP'
  && !('components' in health) && !('details' in health);
const result = { checkedAt: new Date().toISOString(), statuses, retryAfter,
  healthStatus: healthResponse.status, health, passed };
writeFileSync(new URL('./security-smoke-result.json', import.meta.url), JSON.stringify(result, null, 2) + '\n');
console.log(JSON.stringify(result));
if (!passed) throw new Error('Le contrôle de débit ou de santé a échoué. Utiliser une instance fraîche avec les limites par défaut.');
