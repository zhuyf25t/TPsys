export function normalizeApiBase(base: string | null | undefined, fallback = "/api"): string {
  const value = (base ?? "").trim();
  if (!value) {
    return fallback;
  }

  return value.replace(/\/+$/, "");
}

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
