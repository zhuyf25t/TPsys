const BOT_HANDLE_PREFIXES = ["cpu-", "机器人-", "bot-"] as const;
const BOT_DISPLAY_NAME_PREFIXES = ["机器人", "CPU", "Bot"] as const;

export function isBotLikeHandle(handle: string): boolean {
  const normalized = normalizeText(handle);
  return BOT_HANDLE_PREFIXES.some((prefix) => normalized.startsWith(prefix));
}

export function isBotLikeDisplayName(displayName: string): boolean {
  const normalized = normalizeText(displayName);
  return BOT_DISPLAY_NAME_PREFIXES.some((prefix) => normalized.startsWith(prefix));
}

export function buildBotProfilePath(handle: string): string {
  const normalized = handle.trim();
  return normalized ? `/profile/${encodeURIComponent(normalized)}` : "/profile";
}

function normalizeText(value: string): string {
  return value.trim().toLowerCase();
}
