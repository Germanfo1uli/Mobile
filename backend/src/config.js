function booleanFromEnv(value, fallback = false) {
  if (value === undefined) return fallback;
  return value.toLowerCase() === "true";
}

export function loadConfig(env = process.env) {
  return {
    port: Number(env.PORT ?? 8080),
    databaseUrl: env.DATABASE_URL ?? "postgres://bugs:bugs@localhost:5432/bugs",
    databaseSsl: booleanFromEnv(env.DATABASE_SSL),
    corsOrigin: env.CORS_ORIGIN ?? "*",
  };
}
