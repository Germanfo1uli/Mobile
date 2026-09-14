import { createApp } from "./app.js";
import { loadConfig } from "./config.js";
import { inTransaction, pool } from "./db.js";
import { migrate } from "./migrate.js";

const config = loadConfig();
await migrate();

const app = createApp({ pool, inTransaction, corsOrigin: config.corsOrigin });
const server = app.listen(config.port, "0.0.0.0", () => {
  console.log(`Bugs API is listening on port ${config.port}`);
});

async function shutdown(signal) {
  console.log(`${signal}: shutting down`);
  server.close(async () => {
    await pool.end();
    process.exit(0);
  });
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
