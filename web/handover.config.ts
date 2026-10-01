import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests', testMatch: 'handover-list.spec.ts', workers: 1, retries: 0, timeout: 45000,
  outputDir: process.env.HANDOVER_BROWSER_OUTPUT ?? './test-results/handover',
  reporter: [['list'], ['json', { outputFile: `${process.env.HANDOVER_BROWSER_OUTPUT ?? './test-results/handover'}/results.json` }]],
  use: { baseURL: 'http://127.0.0.1:5173', channel: 'chrome', viewport: { width: 1440, height: 900 }, screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  webServer: { command: 'npm.cmd run dev -- --host 127.0.0.1', url: 'http://127.0.0.1:5173', reuseExistingServer: false, timeout: 120000 },
});
