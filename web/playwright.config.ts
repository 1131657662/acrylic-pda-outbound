import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  testIgnore: ['pda-download*.spec.ts', '**/datetime/**'],
  workers: 1,
  retries: 0,
  timeout: 30000,
  use: {
    baseURL: process.env.OUTBOUND_WEB_URL ?? 'http://127.0.0.1:5173',
    channel: 'chrome',
    viewport: { width: 1440, height: 1050 },
    screenshot: 'only-on-failure',
  },
});
