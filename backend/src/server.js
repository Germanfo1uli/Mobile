import { createApp } from "./app.js";
import { loadConfig } from "./config.js";
import { inTransaction, pool } from "./db.js";
import { migrate } from "./migrate.js";
import { finalizeExpiredRounds } from "./rounds.js";

const config = loadConfig();
await migrate();

const app = createApp({ pool, inTransaction, corsOrigin: config.corsOrigin });
const server = app.listen(config.port, "0.0.0.0", () => {
  console.log(`Bugs API is listening on port ${config.port}`);
});

let sweeping = false;
const expiryTimer = setInterval(async () => {
  if (sweeping) return;
  sweeping = true;
  try {
    await finalizeExpiredRounds(inTransaction);
  } catch (error) {
    console.error("Unable to finalize expired rounds", error);
  } finally {
    sweeping = false;
  }
}, config.roundSweepIntervalMs);
expiryTimer.unref();

async function shutdown(signal) {
  console.log(`${signal}: shutting down`);
  clearInterval(expiryTimer);
  server.close(async () => {
    await pool.end();
    process.exit(0);
  });
}

process.on("SIGINT", () => shutdown("SIGINT"));
process.on("SIGTERM", () => shutdown("SIGTERM"));
