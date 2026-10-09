import { defineConfig } from '@playwright/test';

const output = process.env.HANDOVER_FAULT_BOUNDARY_OUTPUT;
export default defineConfig({
  testDir: './tests', testMatch: ['handover-formal-fault.spec.ts'], workers: 1, retries: 0, timeout: 30000,
  outputDir: `${output ?? './test-results/handover-formal-fault'}/artifacts`,
  reporter: [['list'], ['json', { outputFile: `${output ?? './test-results/handover-formal-fault'}/results.json` }]],
  use: { baseURL: 'http://127.0.0.1:5173', channel: 'chrome', actionTimeout: 10000,
    screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  webServer: { command: 'npm.cmd run dev -- --host 127.0.0.1', url: 'http://127.0.0.1:5173', reuseExistingServer: false, timeout: 120000 },
});
