import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig(({ mode }) => ({
  plugins: [vue()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: { '/api': loadEnv(mode, '.', 'OUTBOUND_').OUTBOUND_API_PROXY ?? 'http://127.0.0.1:8083' },
  },
}));
