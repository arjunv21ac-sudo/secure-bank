import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Forward /api calls to Spring Boot, so the browser sees one origin (no CORS issues in dev)
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
