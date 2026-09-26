import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests/datetime', workers: 1, retries: 0, timeout: 30000,
  outputDir: 'test-results/datetime-display',
  reporter: [['list'], ['json', { outputFile: 'test-results/datetime-display/results.json' }]],
  use: { baseURL: 'https://127.0.0.1:5197', ignoreHTTPSErrors: true, channel: 'chrome',
    viewport: { width: 1440, height: 1050 }, screenshot: 'only-on-failure' },
  projects: [
    { name: 'UTC-English', use: { timezoneId: 'UTC', locale: 'en-US' } },
    { name: 'Los-Angeles-Arabic', use: { timezoneId: 'America/Los_Angeles', locale: 'ar-EG' } },
    { name: 'Shanghai-Chinese', use: { timezoneId: 'Asia/Shanghai', locale: 'zh-CN' } },
  ],
  webServer: { command: 'python tests/datetime/serve.py', url: 'https://127.0.0.1:5197/login',
    ignoreHTTPSErrors: true, reuseExistingServer: false, timeout: 15000 },
});
