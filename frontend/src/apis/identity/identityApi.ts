import { normalizeApiBase } from "../../system/api/apiUrl";
import type {
  IdentityAccountsResponseDto,
  IdentityAccountSummaryDto,
  SkinIdDto
} from "../../objects/identity/identityTypes";

export type IdentityAccountSummary = IdentityAccountSummaryDto;

type IdentityAccountsResponse = Partial<Record<keyof IdentityAccountsResponseDto, unknown>>;

type IdentityAccountSummaryPayload = {
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

  const account = value as IdentityAccountSummaryPayload;
  const handle = readString(account.handle);
  const displayName = readString(account.displayName);
  const skinId = normalizeSkinId(readString(account.skinId));
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

function normalizeSkinId(value: string | null): SkinIdDto | null {
  return value === "blue" || value === "survivor" || value === "soldier" || value === "old" ? value : null;
}
