import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  // BO is served under the /bo context-path, so built asset URLs must be
  // absolute from there — a relative base breaks as soon as the router
  // puts the browser on a nested path like /bo/board.
  base: '/bo/',
  build: {
    // Straight into the jar's static resources: same origin as the API,
    // which is the whole reason BO needs no CORS.
    outDir: '../src/main/resources/static',
    emptyOutDir: true
  },
  server: {
    // `npm run dev` talks to a BO running on the host, so the dev server
    // proxies the API under the same path the built app will use.
    proxy: {
      '/bo/api': 'http://localhost:8080',
    }
  }
})
