import { mkdir } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { chromium } from '@playwright/test';

const workspace = resolve(process.cwd(), '..');
const evidenceDirectory = resolve(workspace, 'docs', 'evidence', 'coverage');
const reports = [
  {
    source: resolve(workspace, 'backend', 'target', 'site', 'jacoco', 'index.html'),
    destination: resolve(evidenceDirectory, 'backend-jacoco.png'),
  },
  {
    source: resolve(workspace, 'frontend', 'coverage', 'frontend', 'index.html'),
    destination: resolve(evidenceDirectory, 'frontend-karma.png'),
  },
];

await mkdir(evidenceDirectory, { recursive: true });
const browser = await chromium.launch({ channel: 'msedge' });

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  for (const report of reports) {
    await page.goto(pathToFileURL(report.source).href);
    await page.screenshot({ path: report.destination, fullPage: true });
  }
} finally {
  await browser.close();
}
