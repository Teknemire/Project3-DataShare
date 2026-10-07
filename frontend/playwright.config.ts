import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: process.platform === 'win32' ? 'Microsoft Edge' : 'Chromium',
      use: process.platform === 'win32' ? { ...devices['Desktop Edge'], channel: 'msedge' } : { ...devices['Desktop Chrome'] },
    },
  ],
});
