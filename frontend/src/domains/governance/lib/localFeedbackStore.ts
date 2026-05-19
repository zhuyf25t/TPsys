export type LocalFeedbackKind = "proposal" | "report" | "comment";

export interface LocalFeedbackEntry {
  id: string;
  replayId: string;
  kind: LocalFeedbackKind;
  author: string;
  body: string;
  createdAt: number;
}

const STORAGE_KEY = "slay-demo.local-replay-feedback.v1";

/** 中文名：获取本地feedback（getLocalFeedback）。游戏职责：在前端治理域中组织积分、贡献和反馈数据，支撑玩家成长与运营展示。 */
export function getLocalFeedback(replayId: string, kind?: LocalFeedbackKind): LocalFeedbackEntry[] {
  const normalizedReplayId = replayId.trim();
  if (!normalizedReplayId) {
    return [];
  }

  return readEntries()
    .filter((entry) => entry.replayId === normalizedReplayId && (!kind || entry.kind === kind))
    .sort((left, right) => left.createdAt - right.createdAt);
}

/** 中文名：保存本地feedback（saveLocalFeedback）。游戏职责：在前端治理域中组织积分、贡献和反馈数据，支撑玩家成长与运营展示。 */
export function saveLocalFeedback(input: {
  replayId: string;
  kind: LocalFeedbackKind;
  author: string;
  body: string;
}): LocalFeedbackEntry | null {
  const replayId = input.replayId.trim();
  const body = input.body.trim();
  if (!replayId || !body) {
    return null;
  }

  const entry: LocalFeedbackEntry = {
    id: `${input.kind}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    replayId,
    kind: input.kind,
    author: input.author.trim() || "匿名",
    body,
    createdAt: Date.now()
  };

  writeEntries([...readEntries(), entry].slice(-120));
  return entry;
}

function readEntries(): LocalFeedbackEntry[] {
  if (typeof window === "undefined") {
    return [];
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }

    const parsed = JSON.parse(raw) as { entries?: LocalFeedbackEntry[] };
    return Array.isArray(parsed.entries) ? parsed.entries.filter(isFeedbackEntry) : [];
  } catch {
    return [];
  }
}

function writeEntries(entries: LocalFeedbackEntry[]): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ entries }));
  } catch {
    // Local feedback is non-critical and intentionally does not affect replay playback.
  }
}

function isFeedbackEntry(value: Partial<LocalFeedbackEntry>): value is LocalFeedbackEntry {
  return (
    typeof value.id === "string" &&
    typeof value.replayId === "string" &&
    (value.kind === "proposal" || value.kind === "report" || value.kind === "comment") &&
    typeof value.author === "string" &&
    typeof value.body === "string" &&
    typeof value.createdAt === "number"
  );
}
