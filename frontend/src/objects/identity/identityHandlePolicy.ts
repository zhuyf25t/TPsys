const VISITOR_LIKE_HANDLE_KEYS = new Set([
  "visitor",
  "guest",
  "anonymous",
  "anon",
  "\u8bbf\u5ba2",
  "\u6e38\u5ba2",
  "\u672a\u767b\u5f55"
]);

/** 中文名：规范化玩家玩家名key（normalizePlayerHandleKey）。游戏职责：在前端身份域中组织玩家名、登录态和资料展示，统一玩家身份入口。 */
export function normalizePlayerHandleKey(handle: string | null | undefined): string {
  return (handle ?? "").trim().toLowerCase();
}

/** 中文名：判断是否visitorlike玩家名（isVisitorLikeHandle）。游戏职责：在前端身份域中组织玩家名、登录态和资料展示，统一玩家身份入口。 */
export function isVisitorLikeHandle(handle: string | null | undefined): boolean {
  return VISITOR_LIKE_HANDLE_KEYS.has(normalizePlayerHandleKey(handle));
}

/** 中文名：判断是否playableidentity玩家名（isPlayableIdentityHandle）。游戏职责：在前端身份域中组织玩家名、登录态和资料展示，统一玩家身份入口。 */
export function isPlayableIdentityHandle(handle: string | null | undefined): boolean {
  const key = normalizePlayerHandleKey(handle);
  return Boolean(key) && !VISITOR_LIKE_HANDLE_KEYS.has(key);
}

/** 中文名：规范化playableidentity玩家名（normalizePlayableIdentityHandle）。游戏职责：在前端身份域中组织玩家名、登录态和资料展示，统一玩家身份入口。 */
export function normalizePlayableIdentityHandle(handle: string | null | undefined): string | null {
  const trimmed = (handle ?? "").trim();
  return isPlayableIdentityHandle(trimmed) ? trimmed : null;
}
