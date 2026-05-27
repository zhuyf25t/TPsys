import { fileURLToPath } from "node:url";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";

export default defineConfig({
  root: fileURLToPath(new URL(".", import.meta.url)),
  plugins: [tailwindcss()],
  publicDir: "public",
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
        secure: false
      }
    }
  },
  build: {
    outDir: "../dist",
    emptyOutDir: true,
    rollupOptions: {
      onwarn(warning, defaultHandler) {
        const warningId = typeof warning.id === "string" ? warning.id.replace(/\\/g, "/") : "";
        if (warning.code === "MODULE_LEVEL_DIRECTIVE" && warning.message.includes("\"use client\"") && warningId.includes("/node_modules/react-router/")) {
          return;
        }

        defaultHandler(warning);
      },
      output: {
        manualChunks(id) {
          const normalizedId = id.replace(/\\/g, "/");

          if (normalizedId.includes("/node_modules/phaser/")) {
            return "vendor-phaser";
          }

          if (
            normalizedId.includes("/node_modules/react/") ||
            normalizedId.includes("/node_modules/react-dom/") ||
            normalizedId.includes("/node_modules/react-router/")
          ) {
            return "vendor-react";
          }

          if (normalizedId.includes("/src/runtime/battle/")) {
            return "runtime-battle";
          }

          return undefined;
        }
      }
    }
  }
});
