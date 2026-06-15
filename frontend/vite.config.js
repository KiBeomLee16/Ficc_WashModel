import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/run-request": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/calibration-run-request": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/calibration-run-requests": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/calibration-results": {
        target: "http://localhost:8080",
        changeOrigin: true
      },
      "/alert-history": {
        target: "http://localhost:8080",
        changeOrigin: true
      }
    }
  }
});
