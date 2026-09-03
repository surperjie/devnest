import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Tauri 期望固定端口,且使用 IP 时 devtools 才能正常工作
const host = "127.0.0.1";
const port = 1420;

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  clearScreen: false,
  server: {
    host,
    port,
    strictPort: true,
    proxy: {
      // REST API → 后端
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
      // WebSocket (/ws/console/{id}) → 后端(双保险).
      // 前端默认直连 8080(和昨天的版本行为一致);如果将来改为同源连接,也能自动代理.
      "/ws": {
        target: "ws://127.0.0.1:8080",
        changeOrigin: true,
        ws: true,
      },
    },
  },
  envPrefix: ["VITE_", "TAURI_"],
  build: {
    target: "es2021",
    minify: "esbuild",
    sourcemap: false,
  },
});
