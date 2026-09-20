import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests', testMatch: 'pda-download*.spec.ts', workers: 1, retries: 0,
  timeout: 30000, outputDir: 'test-results/pda-download',
  reporter: [['list'], ['json', { outputFile: '../.local/pda-download-browser-results.json' }]],
  use: { baseURL: 'https://127.0.0.1:5189', ignoreHTTPSErrors: true, channel: 'chrome',
    viewport: { width: 1440, height: 900 }, screenshot: 'only-on-failure' },
  webServer: { command: 'python tests/serve-download.py', url: 'https://127.0.0.1:5189/pda-download',
    ignoreHTTPSErrors: true, reuseExistingServer: false, timeout: 15000 },
});
