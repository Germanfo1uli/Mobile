// Vercel detects Express from the entrypoint import and deploys this app as one Function.
import express from "express";
import { createApp } from "./app.js";
import { inTransaction, pool } from "./db.js";
import { migrate } from "./migrate.js";

void express;

await migrate();

export default createApp({
  pool,
  inTransaction,
  corsOrigin: process.env.CORS_ORIGIN ?? "*",
});
