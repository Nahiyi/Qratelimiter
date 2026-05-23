import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  base: './',
  plugins: [react(), tailwindcss()],
  build: {
    outDir: '../resources/META-INF/resources/qratelimiter-dashboard',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          antd: ['antd', '@ant-design/icons']
        }
      }
    },
    chunkSizeWarningLimit: 1000
  },
  server: {
    port: 5173
  }
});
