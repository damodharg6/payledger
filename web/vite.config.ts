/// <reference types="node" />
/**
 * Vite config for the PayLedger website: two real pages (/ and /developers/), Tailwind v4,
 * React. Pages are pre-rendered to static HTML after the build (scripts/prerender.mjs).
 *
 * Author: Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
import { resolve } from 'node:path';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5180 },
  build: {
    rollupOptions: {
      input: {
        home: resolve(import.meta.dirname, 'index.html'),
        developers: resolve(import.meta.dirname, 'developers/index.html'),
      },
    },
  },
});
