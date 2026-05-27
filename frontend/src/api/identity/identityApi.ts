import { normalizeApiBase } from "../../shared/api/apiUrl";

export interface IdentityAccountSummary {
  handle: string;
  displayName: string;
  skinId: string;
}

interface IdentityAccountsResponse {
  accounts?: unknown;
}

type IdentityAccountSummaryDto = {
  [Field in keyof IdentityAccountSummary]?: unknown;
};

const IDENTITY_API_BASE = normalizeApiBase(import.meta.env.VITE_AUTH_API_BASE ?? "", "/api");
const IDENTITY_API_TIMEOUT_MS = 5_000;

export async function loadIdentityAccounts(): Promise<IdentityAccountSummary[] | null> {
  if (typeof window === "undefined" || !IDENTITY_API_BASE) {
    return null;
  }

  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), IDENTITY_API_TIMEOUT_MS);

  try {
    const response = await fetch(`${IDENTITY_API_BASE}/identity/accounts`, {
      method: "GET",
      cache: "no-store",
      signal: controller.signal
    });

    if (!response.ok) {
      return null;
    }

    const payload = (await response.json().catch(() => null)) as IdentityAccountsResponse | null;
    const rawAccounts = Array.isArray(payload?.accounts) ? payload.accounts : [];

    const accounts = rawAccounts
      .map((account) => normalizeIdentityAccount(account))
      .filter((account): account is IdentityAccountSummary => account !== null);

    return accounts;
  } catch {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

function normalizeIdentityAccount(value: unknown): IdentityAccountSummary | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const account = value as IdentityAccountSummaryDto;
  const handle = readString(account.handle);
  const displayName = readString(account.displayName);
  const skinId = readString(account.skinId);
  if (!handle || !displayName || !skinId) {
    return null;
  }

  return {
    handle,
    displayName,
    skinId
  };
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value : null;
}
