import { buildApiUrl, normalizeApiBase } from "../../../shared/api/apiUrl";
import { type ContributionAdjustmentRecord, type ContributionAdjustmentResult } from "../local/localAdminActionStore";

const GOVERNANCE_API_BASE = normalizeApiBase(
  import.meta.env.VITE_GOVERNANCE_API_BASE ?? import.meta.env.VITE_AUTH_API_BASE ?? "",
  "/api"
);
const REQUEST_TIMEOUT_MS = 5_000;

export const CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT = "slay-demo:contribution-adjustments-changed";

let remoteAdjustmentRecordsCache: ContributionAdjustmentRecord[] | null = null;

export type ContributionAdjustmentDelivery = "remote" | "local" | "failed";
export type GovernanceReviewKind = "replay_proposal" | "replay_report" | "discussion_report" | "bot_suggestion";
export type GovernanceReviewNotificationDelivery = "remote" | "local" | "failed";

export interface ContributionAdjustmentSubmissionResult extends ContributionAdjustmentResult {
  delivery: ContributionAdjustmentDelivery;
  fallbackReason?: string;
}

export interface GovernanceReviewNotificationResult {
  ok: boolean;
  delivery: GovernanceReviewNotificationDelivery;
  fallbackReason?: string;
}

export interface GovernanceReviewNotificationRecord {
  id: string;
  actorHandle: string;
  kind: GovernanceReviewKind;
  targetType: "replay" | "discussion" | "bot";
  targetId: string;
  targetTitle: string;
  targetPath: string;
  body: string;
  createdAt: number;
  mailId: string;
}

export interface GovernanceReviewNotificationQuery {
  kind?: GovernanceReviewKind;
  targetType?: "replay" | "discussion" | "bot";
  limit?: number;
}

interface RemoteContributionAdjustmentResponse {
  adjustment?: unknown;
  adjustments?: unknown;
}

interface RemoteGovernanceReviewNotificationResponse {
  notifications?: unknown;
}

export function getContributionAdjustmentTotals(): Record<string, number> {
  return remoteAdjustmentRecordsCache ? buildContributionAdjustmentTotals(remoteAdjustmentRecordsCache) : {};
}

export async function loadContributionAdjustmentTotals(): Promise<Record<string, number>> {
  const records = await loadContributionAdjustments();
  return records ? buildContributionAdjustmentTotals(records) : getContributionAdjustmentTotals();
}

export async function loadContributionAdjustments(): Promise<ContributionAdjustmentRecord[] | null> {
  if (typeof window === "undefined" || !GOVERNANCE_API_BASE) {
    return null;
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(GOVERNANCE_API_BASE, "/governance/contribution-adjustments"), {
        method: "GET",
        cache: "no-store",
        signal: controller.signal
      });

      if (!response.ok) {
        return null;
      }

      const payload = (await response.json().catch(() => null)) as RemoteContributionAdjustmentResponse | null;
      const rawRecords = Array.isArray(payload?.adjustments) ? payload.adjustments : [];
      const records = rawRecords
        .map((record) => normalizeContributionAdjustmentRecord(record))
        .filter((record): record is ContributionAdjustmentRecord => record !== null);

      remoteAdjustmentRecordsCache = records;
      return [...records];
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return null;
  }
}

export async function recordContributionAdjustment(input: {
  actorHandle?: string;
  targetHandle: string;
  delta: number;
  reason?: string;
  sourceLabel?: string;
  sourcePath?: string;
}): Promise<ContributionAdjustmentSubmissionResult> {
  if (typeof window === "undefined" || !GOVERNANCE_API_BASE) {
    return failedContributionAdjustment("network_unavailable");
  }

  const payload = {
    actorHandle: input.actorHandle ?? "",
    targetHandle: input.targetHandle,
    delta: Math.trunc(input.delta),
    reason: input.reason ?? "",
    sourceLabel: input.sourceLabel ?? "",
    sourcePath: input.sourcePath ?? ""
  };

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(GOVERNANCE_API_BASE, "/governance/contribution-adjustments"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: controller.signal,
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        return failedContributionAdjustment(`http_${response.status}`);
      }

      const responsePayload = (await response.json().catch(() => null)) as RemoteContributionAdjustmentResponse | null;
      const record = normalizeContributionAdjustmentRecord(responsePayload?.adjustment);
      if (!record) {
        return failedContributionAdjustment("invalid_response");
      }

      remoteAdjustmentRecordsCache = [record, ...(remoteAdjustmentRecordsCache ?? []).filter((entry) => entry.id !== record.id)];
      const result: ContributionAdjustmentSubmissionResult = {
        ok: true,
        record,
        delivery: "remote"
      };
      emitContributionAdjustmentsChanged(result);
      return result;
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return failedContributionAdjustment("network_unavailable");
  }
}

export async function submitGovernanceReviewNotification(input: {
  actorHandle?: string;
  kind: GovernanceReviewKind;
  targetType: "replay" | "discussion" | "bot";
  targetId: string;
  targetTitle?: string;
  targetPath?: string;
  body: string;
}): Promise<GovernanceReviewNotificationResult> {
  if (typeof window === "undefined" || !GOVERNANCE_API_BASE) {
    return failedGovernanceReviewNotification("network_unavailable");
  }

  const payload = {
    actorHandle: input.actorHandle ?? "",
    kind: input.kind,
    targetType: input.targetType,
    targetId: input.targetId,
    targetTitle: input.targetTitle ?? "",
    targetPath: input.targetPath ?? "",
    body: input.body
  };

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(buildApiUrl(GOVERNANCE_API_BASE, "/governance/admin-notifications"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        signal: controller.signal,
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        return failedGovernanceReviewNotification(`http_${response.status}`);
      }

      return {
        ok: true,
        delivery: "remote"
      };
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return failedGovernanceReviewNotification("network_unavailable");
  }
}

export async function loadGovernanceReviewNotifications(
  query: GovernanceReviewNotificationQuery = {}
): Promise<GovernanceReviewNotificationRecord[] | null> {
  if (typeof window === "undefined" || !GOVERNANCE_API_BASE) {
    return null;
  }

  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
      const response = await fetch(
        buildApiUrl(GOVERNANCE_API_BASE, "/governance/admin-notifications", {
          kind: query.kind,
          targetType: query.targetType,
          limit: typeof query.limit === "number" ? Math.trunc(query.limit) : undefined
        }),
        {
          method: "GET",
          cache: "no-store",
          signal: controller.signal
        }
      );

      if (!response.ok) {
        return null;
      }

      const payload = (await response.json().catch(() => null)) as RemoteGovernanceReviewNotificationResponse | null;
      const rawRecords = Array.isArray(payload?.notifications) ? payload.notifications : [];
      return rawRecords
        .map((record) => normalizeGovernanceReviewNotificationRecord(record))
        .filter((record): record is GovernanceReviewNotificationRecord => record !== null);
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    return null;
  }
}

function buildContributionAdjustmentTotals(records: ContributionAdjustmentRecord[]): Record<string, number> {
  return records.reduce<Record<string, number>>((totals, record) => {
    const key = normalizeHandle(record.targetHandle);
    totals[key] = (totals[key] ?? 0) + record.delta;
    return totals;
  }, {});
}

function normalizeContributionAdjustmentRecord(value: unknown): ContributionAdjustmentRecord | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const record = value as Partial<Record<keyof ContributionAdjustmentRecord, unknown>>;
  if (!hasFields(record, CONTRIBUTION_ADJUSTMENT_FIELDS)) {
    return null;
  }

  const id = readString(record.id);
  const actorHandle = readString(record.actorHandle);
  const targetHandle = readString(record.targetHandle);
  const delta = readNumber(record.delta);
  const reason = readString(record.reason);
  const createdAt = readNumber(record.createdAt);
  const sourceLabel = readString(record.sourceLabel);
  const sourcePath = readString(record.sourcePath);

  if (!id || !actorHandle || !targetHandle || delta === null || reason === null || createdAt === null || sourceLabel === null || sourcePath === null) {
    return null;
  }

  return {
    id,
    actorHandle,
    targetHandle,
    delta: Math.trunc(delta),
    reason,
    createdAt,
    sourceLabel,
    sourcePath
  };
}

function normalizeGovernanceReviewNotificationRecord(value: unknown): GovernanceReviewNotificationRecord | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const record = value as Partial<Record<keyof GovernanceReviewNotificationRecord, unknown>>;
  if (!hasFields(record, GOVERNANCE_NOTIFICATION_FIELDS)) {
    return null;
  }

  const id = readString(record.id);
  const actorHandle = readString(record.actorHandle);
  const kind = readString(record.kind);
  const targetType = readString(record.targetType);
  const targetId = readString(record.targetId);
  const targetTitle = readString(record.targetTitle);
  const targetPath = readString(record.targetPath);
  const body = readString(record.body);
  const createdAt = readNumber(record.createdAt);
  const mailId = readString(record.mailId);

  if (
    !id ||
    !actorHandle ||
    !kind ||
    !targetType ||
    !isGovernanceReviewKind(kind) ||
    !isGovernanceReviewTargetType(targetType) ||
    !targetId ||
    targetTitle === null ||
    targetPath === null ||
    body === null ||
    createdAt === null ||
    !mailId
  ) {
    return null;
  }

  return {
    id,
    actorHandle,
    kind,
    targetType,
    targetId,
    targetTitle,
    targetPath,
    body,
    createdAt,
    mailId
  };
}

function failedContributionAdjustment(fallbackReason?: string): ContributionAdjustmentSubmissionResult {
  return {
    ok: false,
    record: null,
    delivery: "failed",
    fallbackReason
  };
}

function failedGovernanceReviewNotification(fallbackReason?: string): GovernanceReviewNotificationResult {
  return {
    ok: false,
    delivery: "failed",
    fallbackReason
  };
}

function emitContributionAdjustmentsChanged(result: ContributionAdjustmentSubmissionResult): void {
  if (!result.ok || typeof window === "undefined") {
    return;
  }

  window.dispatchEvent(new CustomEvent(CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT));
}

function isGovernanceReviewKind(value: string): value is GovernanceReviewKind {
  return (
    value === "replay_proposal" ||
    value === "replay_report" ||
    value === "discussion_report" ||
    value === "bot_suggestion"
  );
}

function isGovernanceReviewTargetType(value: string): value is "replay" | "discussion" | "bot" {
  return value === "replay" || value === "discussion" || value === "bot";
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}

function hasFields<T extends string>(value: Record<string, unknown>, fields: readonly T[]): boolean {
  return fields.every((field) => Object.prototype.hasOwnProperty.call(value, field));
}

function readString(value: unknown): string | null {
  return typeof value === "string" ? value.trim() : null;
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

const CONTRIBUTION_ADJUSTMENT_FIELDS = [
  "id",
  "actorHandle",
  "targetHandle",
  "delta",
  "reason",
  "createdAt",
  "sourceLabel",
  "sourcePath"
] satisfies (keyof ContributionAdjustmentRecord)[];

const GOVERNANCE_NOTIFICATION_FIELDS = [
  "id",
  "actorHandle",
  "kind",
  "targetType",
  "targetId",
  "targetTitle",
  "targetPath",
  "body",
  "createdAt",
  "mailId"
] satisfies (keyof GovernanceReviewNotificationRecord)[];
