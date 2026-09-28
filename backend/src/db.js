import pg from "pg";
import { loadConfig } from "./config.js";

const { Pool } = pg;
const config = loadConfig();

export const pool = new Pool({
  connectionString: config.databaseUrl,
  ssl: config.databaseSsl ? { rejectUnauthorized: false } : false,
  max: 10,
  idleTimeoutMillis: 30_000,
});

// PostgreSQL can briefly drop idle clients while Docker Desktop restarts it.
// Handling the pool event keeps the API process alive; the pool reconnects on
// the next query instead of terminating Node.js with an unhandled error.
pool.on("error", (error) => {
  console.error("PostgreSQL idle connection was interrupted", error.message);
});

export async function inTransaction(work) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const result = await work(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}
