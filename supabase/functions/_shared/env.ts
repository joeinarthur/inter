export function getEnv(name: string): string | undefined {
  const value = Deno.env.get(name)?.trim();
  return value && value.length > 0 ? value : undefined;
}

export function requireEnv(name: string): string {
  const value = getEnv(name);
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}
