import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests', testMatch: 'temporary-command-recovery.spec.ts', workers: 1, retries: 0, timeout: 30000,
  outputDir: process.env.TEMP_WEB_BROWSER_OUTPUT,
  reporter: [['list'], ['json', { outputFile: process.env.TEMP_WEB_BROWSER_REPORT }]],
  use: { baseURL: 'http://127.0.0.1:5178', channel: 'chrome', viewport: { width: 1100, height: 850 }, screenshot: 'only-on-failure' },
  webServer: { command: 'npm run dev -- --port 5178', url: 'http://127.0.0.1:5178', reuseExistingServer: false, timeout: 60000 },
});
