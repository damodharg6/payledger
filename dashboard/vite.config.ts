/// <reference types="vitest/config" />
/**
 * Vite config for the PayLedger dashboard.
 * In development, /api is proxied to the Spring Boot app, so the browser sees one origin
 * (no CORS needed). In Docker, nginx does the same job.
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': process.env.VITE_API_TARGET ?? 'http://localhost:8080',
    },
  },
  test: {
    environment: 'node',
  },
});
