import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests', testMatch: 'temporary-export.spec.ts', workers: 1, retries: 0,
  timeout: 45000, outputDir: process.env.TEMP_EXPORT_OUTPUT ?? 'test-results/temporary-export',
  reporter: [['list']],
  use: { baseURL: 'http://127.0.0.1:19436', channel: 'chrome',
    viewport: { width: 1440, height: 1050 }, screenshot: 'only-on-failure' },
  webServer: { command: 'npm run dev -- --port 19436', url: 'http://127.0.0.1:19436/login',
    reuseExistingServer: false, timeout: 20000 },
});
