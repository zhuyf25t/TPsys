const STORAGE_ITEM_LIMIT_BYTES = 900_000;

const RECOVERABLE_STORAGE_KEYS = [
  "slay-demo-active-battle-session-v1",
  "slay-demo.active-battle-session.v1",
  "slay-demo.truthful-battle-data.v2",
  "slay-demo.local-replay-playback.v1"
] as const;

/** 中文名：sanitizestartup存储（sanitizeStartupStorage）。游戏职责：在前端共享工程模块中统一公共逻辑，避免业务页面散落重复实现。 */
export function sanitizeStartupStorage(): void {
  if (typeof window === "undefined") {
    return;
  }

  for (const key of RECOVERABLE_STORAGE_KEYS) {
    removeOversizedStorageItem(key);
  }

  safeRemoveStorageItem("slay-demo-active-battle-session-v1");
}

/** 中文名：重置recoverablestartup存储（resetRecoverableStartupStorage）。游戏职责：在前端共享工程模块中统一公共逻辑，避免业务页面散落重复实现。 */
export function resetRecoverableStartupStorage(): void {
  if (typeof window === "undefined") {
    return;
  }

  for (const key of RECOVERABLE_STORAGE_KEYS) {
    safeRemoveStorageItem(key);
  }
}

function removeOversizedStorageItem(key: string): void {
  try {
    const value = window.localStorage.getItem(key);
    if (value && value.length > STORAGE_ITEM_LIMIT_BYTES) {
      window.localStorage.removeItem(key);
      console.warn(`[storage] removed oversized recoverable item: ${key}`);
    }
  } catch {
    // Storage access can fail in restricted modes; startup should continue.
  }
}

function safeRemoveStorageItem(key: string): void {
  try {
    window.localStorage.removeItem(key);
  } catch {
    // Recovery storage is optional.
  }
}
