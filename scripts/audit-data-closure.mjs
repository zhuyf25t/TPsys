import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const VISITOR_LIKE_HANDLE_KEYS = new Set([
  "visitor",
  "guest",
  "anonymous",
  "anon",
  "访客",
  "游客",
  "未登录",
]);

const ROOT_DIR = join(fileURLToPath(new URL("..", import.meta.url)));
const DATA_DIR = join(ROOT_DIR, "backend", "data");
const SAMPLE_LIMIT = 20;

const files = {
  battleResults: "battle-results.json",
  mails: "mails.json",
  replayRecords: "replay-records.json",
  identityAccounts: "identity-accounts.json",
};

const [battleResults, mails, replayRecords, identityAccounts] = [
  readJsonArray(files.battleResults, "results"),
  readJsonArray(files.mails, "mails"),
  readJsonArray(files.replayRecords, "records"),
  readJsonArray(files.identityAccounts, "accounts"),
];

const visitorAudits = [
  auditVisitorLikeRows("battle results", battleResults, "handle", (row) => ({
    resultId: row.resultId,
    battleId: row.battleId,
    handle: row.handle,
  })),
  auditVisitorLikeRows("mails", mails, "ownerHandle", (row) => ({
    id: row.id,
    ownerHandle: row.ownerHandle,
    kind: row.kind,
  })),
  auditVisitorLikeRows("replay records", replayRecords, "handle", (row) => ({
    replayId: row.replayId,
    battleId: row.battleId,
    handle: row.handle,
  })),
  auditVisitorLikeRows("identity accounts", identityAccounts, "handle", (row) => ({
    userId: row.userId,
    handle: row.handle,
    active: row.active,
  })),
];

const duplicateBattleResultAudit = auditBattleResultDuplicates(battleResults);

printReport(visitorAudits, duplicateBattleResultAudit);

function readJsonArray(fileName, arrayKey) {
  const path = join(DATA_DIR, fileName);
  const parsed = JSON.parse(readFileSync(path, "utf8"));
  const rows = parsed[arrayKey];

  if (!Array.isArray(rows)) {
    throw new Error(`${fileName} does not contain an array at "${arrayKey}".`);
  }

  return rows;
}

function normalizeHandleKey(value) {
  return String(value ?? "").trim().toLowerCase();
}

function isNonPlayableHandle(value) {
  const key = normalizeHandleKey(value);
  return key.length === 0 || VISITOR_LIKE_HANDLE_KEYS.has(key);
}

function bucketHandle(value) {
  const key = normalizeHandleKey(value);
  return key.length === 0 ? "(empty)" : key;
}

function auditVisitorLikeRows(label, rows, handleField, toSample) {
  const matches = [];
  const buckets = new Map();

  for (const row of rows) {
    const handle = row?.[handleField];
    if (!isNonPlayableHandle(handle)) {
      continue;
    }

    matches.push(row);
    const bucket = bucketHandle(handle);
    buckets.set(bucket, (buckets.get(bucket) ?? 0) + 1);
  }

  return {
    label,
    total: rows.length,
    matches: matches.length,
    buckets: sortedBuckets(buckets),
    samples: matches.slice(0, SAMPLE_LIMIT).map(toSample),
  };
}

function auditBattleResultDuplicates(rows) {
  const groups = new Map();

  for (const row of rows) {
    const battleIdKey = normalizeHandleKey(row?.battleId);
    const handleKey = normalizeHandleKey(row?.handle);
    const logicalKey = `${battleIdKey}\u0000${handleKey}`;

    if (!groups.has(logicalKey)) {
      groups.set(logicalKey, {
        battleIdKey,
        handleKey,
        rows: [],
      });
    }

    groups.get(logicalKey).rows.push(row);
  }

  const duplicateGroups = [...groups.values()]
    .filter((group) => group.rows.length > 1)
    .sort((left, right) => {
      const rowDelta = right.rows.length - left.rows.length;
      if (rowDelta !== 0) {
        return rowDelta;
      }
      return `${left.battleIdKey}:${left.handleKey}`.localeCompare(`${right.battleIdKey}:${right.handleKey}`);
    });

  return {
    totalRows: rows.length,
    duplicateGroups: duplicateGroups.length,
    duplicateRows: duplicateGroups.reduce((sum, group) => sum + group.rows.length, 0),
    excessRows: duplicateGroups.reduce((sum, group) => sum + group.rows.length - 1, 0),
    samples: duplicateGroups.slice(0, SAMPLE_LIMIT).map((group) => ({
      battleIdKey: group.battleIdKey || "(empty)",
      handleKey: group.handleKey || "(empty)",
      count: group.rows.length,
      resultIds: group.rows.map((row) => row.resultId ?? "(missing)").slice(0, 5),
      finishedAt: group.rows.map((row) => row.finishedAt ?? null).slice(0, 5),
    })),
  };
}

function sortedBuckets(buckets) {
  return [...buckets.entries()]
    .map(([handle, count]) => ({ handle, count }))
    .sort((left, right) => {
      const countDelta = right.count - left.count;
      if (countDelta !== 0) {
        return countDelta;
      }
      return left.handle.localeCompare(right.handle);
    });
}

function printReport(visitorAudits, duplicateAudit) {
  console.log("Data closure audit (read-only)");
  console.log(`Data directory: ${DATA_DIR}`);
  console.log("Visitor-like aliases: Visitor, guest, anonymous, anon, 访客, 游客, 未登录; empty handles are non-playable.");
  console.log("");

  for (const audit of visitorAudits) {
    console.log(`${audit.label}: ${audit.matches} visitor-like/non-playable rows out of ${audit.total}`);
    printBuckets(audit.buckets);
    printSamples(audit.samples);
    console.log("");
  }

  console.log("battle result duplicate logical groups:");
  console.log(`  total rows: ${duplicateAudit.totalRows}`);
  console.log(`  duplicate groups: ${duplicateAudit.duplicateGroups}`);
  console.log(`  rows in duplicate groups: ${duplicateAudit.duplicateRows}`);
  console.log(`  excess duplicate rows beyond one per logical key: ${duplicateAudit.excessRows}`);
  printSamples(duplicateAudit.samples);
}

function printBuckets(buckets) {
  if (buckets.length === 0) {
    console.log("  buckets: none");
    return;
  }

  console.log("  buckets:");
  for (const bucket of buckets) {
    console.log(`    ${bucket.handle}: ${bucket.count}`);
  }
}

function printSamples(samples) {
  if (samples.length === 0) {
    console.log("  samples: none");
    return;
  }

  console.log(`  samples (first ${samples.length}):`);
  for (const sample of samples) {
    console.log(`    ${JSON.stringify(sample)}`);
  }
}
