import { mkdir } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { chromium } from '@playwright/test';

const workspace = resolve(process.cwd(), '..');
const evidenceDirectory = resolve(workspace, 'docs', 'evidence', 'coverage');
const label = process.argv.find(argument => argument.startsWith('--label='))?.slice(8);
if (label && !/^[a-zA-Z0-9-]+$/.test(label)) throw new Error('Label de capture invalide.');
const suffix = label ? `-${label}` : '';
const reports = [
  {
    source: resolve(workspace, 'backend', 'target', 'site', 'jacoco', 'index.html'),
    destination: resolve(evidenceDirectory, `backend-jacoco${suffix}.png`),
  },
  {
    source: resolve(workspace, 'frontend', 'coverage', 'frontend', 'index.html'),
    destination: resolve(evidenceDirectory, `frontend-karma${suffix}.png`),
  },
];

await mkdir(evidenceDirectory, { recursive: true });
const browser = await chromium.launch(process.platform === 'win32' ? { channel: 'msedge' } : {});

try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  for (const report of reports) {
    await page.goto(pathToFileURL(report.source).href);
    await page.screenshot({ path: report.destination, fullPage: true });
  }
} finally {
  await browser.close();
}
