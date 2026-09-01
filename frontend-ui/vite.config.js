import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

// Tauri 期望固定端口,且使用 IP 时 devtools 才能正常工作
const host = "127.0.0.1";
const port = 1420;

export default defineConfig({
  plugins: [vue()],
  clearScreen: false,
  server: {
    host,
    port,
    strictPort: true,
  },
  envPrefix: ["VITE_", "TAURI_"],
  build: {
    target: "es2021",
    minify: "esbuild",
    sourcemap: false,
  },
});
