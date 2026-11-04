/// <reference types="vitest/config" />
import { seedDesignPlugin } from "@seed-design/vite-plugin";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), seedDesignPlugin({ colorMode: "light-only" })],
  resolve: {
    tsconfigPaths: true, // tsconfig.app.json의 "seed-design/*" 경로를 Vite도 알아듣게 한다
  },
  server: {
    proxy: { "/api": "http://localhost:8080" },
  },
  test: {
    environment: "jsdom",
    setupFiles: "./src/test/setup.ts",
    // SEED 패키지는 내부에서 .css를 import한다. Node는 .css를 읽지 못하므로 Vitest가 직접 변환하게 한다
    server: { deps: { inline: [/@seed-design\//] } },
  },
});
