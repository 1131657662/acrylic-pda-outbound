import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests/r08', outputDir: '../.local/r08/integration/browser-results',
  workers: 1, retries: 0, timeout: 90000,
  use: { baseURL: 'http://127.0.0.1:5178', channel: 'chrome', viewport: { width: 1440, height: 1000 },
    screenshot: 'only-on-failure', trace: 'retain-on-failure' },
});
