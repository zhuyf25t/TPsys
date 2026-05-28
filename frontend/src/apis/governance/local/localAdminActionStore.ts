import { isBuiltinAdminHandle } from "../../identity/authGateway";
import { appendMailNotification } from "../../mail/local/localMailNotificationStore";
import type { ContributionAdjustmentRecord as GovernanceContributionAdjustmentRecord } from "../../../objects/governance/governanceTypes";

const STORAGE_KEY = "slay-demo.governance.admin-actions.v1";

export type ContributionAdjustmentRecord = GovernanceContributionAdjustmentRecord;

interface ContributionAdjustmentState {
  version: 1;
  records: ContributionAdjustmentRecord[];
}

export interface ContributionAdjustmentResult {
  ok: boolean;
  record: ContributionAdjustmentRecord | null;
}

/** 中文名：记录贡献adjustment（recordContributionAdjustment）。游戏职责：在前端治理域中组织积分、贡献和反馈数据，支撑玩家成长与运营展示。 */
export function recordContributionAdjustment(input: {
  actorHandle?: string;
  targetHandle: string;
  delta: number;
  reason?: string;
  sourceLabel?: string;
  sourcePath?: string;
}): ContributionAdjustmentResult {
  const actorHandle = normalizeHandle(input.actorHandle ?? "");
  const targetHandle = normalizeHandle(input.targetHandle);
  const delta = Math.trunc(input.delta);
  const reason = (input.reason ?? "").trim();
  const sourceLabel = (input.sourceLabel ?? "").trim();
  const sourcePath = (input.sourcePath ?? "").trim();

  if (!isBuiltinAdminHandle(actorHandle) || !targetHandle || delta === 0 || !Number.isFinite(delta)) {
    return { ok: false, record: null };
  }

  const record: ContributionAdjustmentRecord = {
    id: `governance-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    actorHandle,
    targetHandle,
    delta,
    reason,
    createdAt: Date.now(),
    sourceLabel,
    sourcePath
  };

  writeState({
    version: 1,
    records: [record, ...readState().records].slice(0, 200)
  });

  appendMailNotification({
    ownerHandle: targetHandle,
    kind: "governance",
    subject: `贡献裁决 ${formatDelta(delta)}`,
    excerpt: buildNotificationExcerpt(actorHandle, delta, reason, sourceLabel, sourcePath),
    senderLabel: `管理员 @${actorHandle}`,
    important: true
  });

  return { ok: true, record };
}

/** 中文名：获取贡献adjustmenttotals（getContributionAdjustmentTotals）。游戏职责：在前端治理域中组织积分、贡献和反馈数据，支撑玩家成长与运营展示。 */
export function getContributionAdjustmentTotals(): Record<string, number> {
  return readState().records.reduce<Record<string, number>>((totals, record) => {
    const key = normalizeHandle(record.targetHandle);
    totals[key] = (totals[key] ?? 0) + record.delta;
    return totals;
  }, {});
}

function buildNotificationExcerpt(
  actorHandle: string,
  delta: number,
  reason: string,
  sourceLabel: string,
  sourcePath: string
): string {
  const parts = [`@${actorHandle} 对你的贡献值进行了 ${formatDelta(delta)} 调整。`];
  if (reason) {
    parts.push(`原因：${reason}`);
  }
  if (sourceLabel || sourcePath) {
    parts.push(`来源：${[sourceLabel, sourcePath].filter(Boolean).join(" ")}`);
  }
  return parts.join(" ");
}

function formatDelta(delta: number): string {
  return delta > 0 ? `+${delta}` : `${delta}`;
}

function readState(): ContributionAdjustmentState {
  if (typeof window === "undefined") {
    return { version: 1, records: [] };
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return { version: 1, records: [] };
    }

    const parsed = JSON.parse(raw) as Partial<ContributionAdjustmentState>;
    return {
      version: 1,
      records: Array.isArray(parsed.records) ? parsed.records.filter(isContributionAdjustmentRecord) : []
    };
  } catch {
    return { version: 1, records: [] };
  }
}

function writeState(state: ContributionAdjustmentState): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function isContributionAdjustmentRecord(
  value: Partial<ContributionAdjustmentRecord>
): value is ContributionAdjustmentRecord {
  return (
    typeof value.id === "string" &&
    typeof value.actorHandle === "string" &&
    typeof value.targetHandle === "string" &&
    typeof value.delta === "number" &&
    typeof value.reason === "string" &&
    typeof value.createdAt === "number"
  );
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
