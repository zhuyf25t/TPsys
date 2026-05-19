const BOT_HANDLE_PREFIXES = ["cpu-", "机器人-", "bot-"] as const;
const BOT_DISPLAY_NAME_PREFIXES = ["机器人", "CPU", "Bot"] as const;

/** 中文名：判断是否机器人like玩家名（isBotLikeHandle）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function isBotLikeHandle(handle: string): boolean {
  const normalized = normalizeText(handle);
  return BOT_HANDLE_PREFIXES.some((prefix) => normalized.startsWith(prefix));
}

/** 中文名：判断是否机器人like展示name（isBotLikeDisplayName）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function isBotLikeDisplayName(displayName: string): boolean {
  const normalized = normalizeText(displayName);
  return BOT_DISPLAY_NAME_PREFIXES.some((prefix) => normalized.startsWith(prefix));
}

/** 中文名：构建机器人profilepath（buildBotProfilePath）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function buildBotProfilePath(handle: string): string {
  const normalized = handle.trim();
  return normalized ? `/profile/${encodeURIComponent(normalized)}` : "/profile";
}

function normalizeText(value: string): string {
  return value.trim().toLowerCase();
}
