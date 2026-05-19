/** 中文名：规范化接口base（normalizeApiBase）。游戏职责：在前端共享工程模块中统一公共逻辑，避免业务页面散落重复实现。 */
export function normalizeApiBase(base: string | null | undefined, fallback = "/api"): string {
  const value = (base ?? "").trim();
  if (!value) {
    return fallback;
  }

  return value.replace(/\/+$/, "");
}

/** 中文名：构建接口地址（buildApiUrl）。游戏职责：在前端共享工程模块中统一公共逻辑，避免业务页面散落重复实现。 */
export function buildApiUrl(
  base: string,
  path: string,
  query?: Record<string, string | number | boolean | null | undefined>
): string {
  const normalizedBase = base.replace(/\/+$/, "");
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  let url = `${normalizedBase}${normalizedPath}`;

  if (query) {
    const params = new URLSearchParams();

    for (const [key, value] of Object.entries(query)) {
      if (value === null || typeof value === "undefined") {
        continue;
      }

      params.set(key, String(value));
    }

    const search = params.toString();
    if (search) {
      url += `?${search}`;
    }
  }

  return url;
}
