import { chromium } from '@playwright/test';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';

const baseUrl = process.env.E2E_BASE_URL ?? 'http://localhost:4200';
const outputPath = resolve(
  process.env.PERF_OUTPUT ?? '../quality/browser-performance.json',
);

const browser = await chromium.launch({ channel: 'msedge', headless: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });

await page.goto(baseUrl, { waitUntil: 'networkidle' });

const metrics = await page.evaluate(() => {
  const navigation = performance.getEntriesByType('navigation')[0];
  const resources = performance.getEntriesByType('resource');
  const paints = Object.fromEntries(
    performance.getEntriesByType('paint').map((entry) => [entry.name, entry.startTime]),
  );

  const transferred = (entries) =>
    entries.reduce((total, entry) => total + (entry.transferSize || 0), 0);

  return {
    measuredAt: new Date().toISOString(),
    url: location.href,
    navigation: navigation
      ? {
          timeToFirstByteMs: navigation.responseStart,
          domContentLoadedMs: navigation.domContentLoadedEventEnd,
          loadEventMs: navigation.loadEventEnd,
          documentTransferBytes: navigation.transferSize,
        }
      : null,
    paint: {
      firstPaintMs: paints['first-paint'] ?? null,
      firstContentfulPaintMs: paints['first-contentful-paint'] ?? null,
    },
    resources: {
      count: resources.length,
      transferBytes: transferred(resources),
      javascriptTransferBytes: transferred(
        resources.filter((entry) => entry.name.includes('.js')),
      ),
      stylesheetTransferBytes: transferred(
        resources.filter((entry) => entry.name.includes('.css')),
      ),
    },
  };
});

await browser.close();
await mkdir(dirname(outputPath), { recursive: true });
await writeFile(outputPath, `${JSON.stringify(metrics, null, 2)}\n`, 'utf8');
console.log(JSON.stringify(metrics, null, 2));
