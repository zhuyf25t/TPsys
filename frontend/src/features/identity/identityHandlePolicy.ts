const VISITOR_LIKE_HANDLE_KEYS = new Set([
  "visitor",
  "guest",
  "anonymous",
  "anon",
  "\u8bbf\u5ba2",
  "\u6e38\u5ba2",
  "\u672a\u767b\u5f55"
]);

export function normalizePlayerHandleKey(handle: string | null | undefined): string {
  return (handle ?? "").trim().toLowerCase();
}

export function isVisitorLikeHandle(handle: string | null | undefined): boolean {
  return VISITOR_LIKE_HANDLE_KEYS.has(normalizePlayerHandleKey(handle));
}

export function isPlayableIdentityHandle(handle: string | null | undefined): boolean {
  const key = normalizePlayerHandleKey(handle);
  return Boolean(key) && !VISITOR_LIKE_HANDLE_KEYS.has(key);
}

export function normalizePlayableIdentityHandle(handle: string | null | undefined): string | null {
  const trimmed = (handle ?? "").trim();
  return isPlayableIdentityHandle(trimmed) ? trimmed : null;
}
