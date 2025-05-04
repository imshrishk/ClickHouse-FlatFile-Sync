import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import path from "path";
import runtimeErrorOverlay from "@replit/vite-plugin-runtime-error-modal";

// 👇 convert to async function
export default defineConfig(async ({ mode }) => {
  // ✅ Manually load env file
  const env = loadEnv(mode, process.cwd(), ""); // "" loads all VITE_ variables
  const isReplit = process.env.REPL_ID !== undefined;

  console.log("Ensure SpringBoot is running on: ", env.VITE_SPRING_BOOT_URL);

  return {    
    plugins: [
      react(),
      runtimeErrorOverlay(),
      ...(process.env.NODE_ENV !== "production" && isReplit
        ? [
            (await import("@replit/vite-plugin-cartographer")).cartographer(),
          ]
        : []),
    ],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "client", "src"),
        "@shared": path.resolve(__dirname, "shared"),
        "@assets": path.resolve(__dirname, "attached_assets"),
      },
    },
    root: path.resolve(__dirname, "client"),
    build: {
      outDir: path.resolve(__dirname, "dist/public"),
      emptyOutDir: true,
    },
    define: {
      // make VITE_ env vars available
      "import.meta.env": env,
      "import.meta.env.VITE_SPRING_BOOT_URL": JSON.stringify(env.VITE_SPRING_BOOT_URL),
    },
  };
});
