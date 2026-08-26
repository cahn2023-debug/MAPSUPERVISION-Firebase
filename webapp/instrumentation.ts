export async function register() {
  if (process.env.NEXT_RUNTIME === "nodejs" && process.env.ENABLE_STARTUP_MIGRATION === "true") {
    const { runMigrationIfNeeded } = await import("./lib/migration-runner");
    void runMigrationIfNeeded();
  }
}
