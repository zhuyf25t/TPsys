import { copyFileSync, existsSync, readFileSync, renameSync, unlinkSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const VISITOR_LIKE_HANDLE_KEYS = new Set([
  "visitor",
  "guest",
  "anonymous",
  "anon",
  "\u8bbf\u5ba2",
  "\u6e38\u5ba2",
  "\u672a\u767b\u5f55",
]);

const ROOT_DIR = join(fileURLToPath(new URL("..", import.meta.url)));
const DATA_DIR = join(ROOT_DIR, "backend", "data");

const args = process.argv.slice(2);
const apply = args.includes("--apply");
const help = args.includes("--help") || args.includes("-h");
const unknownArgs = args.filter((arg) => !["--apply", "--dry-run", "--help", "-h"].includes(arg));

if (help) {
  printUsage();
  process.exit(0);
}

if (unknownArgs.length > 0) {
  fail(`Unknown argument(s): ${unknownArgs.join(", ")}`);
}

const documents = {
  battleResults: readJsonDocument("battle-results.json", ["results"]),
  mails: readJsonDocument("mails.json", ["mails"]),
  replayRecords: readJsonDocument("replay-records.json", ["records"], ["comments"]),
  identityAccounts: readJsonDocument("identity-accounts.json", ["accounts"]),
};

const plans = [
  planBattleResultsCleanup(documents.battleResults),
  planHandleArrayCleanup(documents.mails, "mails", "ownerHandle"),
  planReplayRecordsCleanup(documents.replayRecords),
  planHandleArrayCleanup(documents.identityAccounts, "accounts", "handle"),
];

const changedPlans = plans.filter((plan) => plan.changed);
const backupTimestamp = formatBackupTimestamp(new Date());

if (apply) {
  ensureBackupTargetsAvailable(changedPlans, backupTimestamp);
  prepareTempWrites(changedPlans, backupTimestamp);

  for (const plan of changedPlans) {
    plan.backupPath = `${plan.path}.bak-${backupTimestamp}`;
    copyFileSync(plan.path, plan.backupPath);
  }

  for (const plan of changedPlans) {
    renameSync(plan.tempPath, plan.path);
  }
}

printSummary(plans, { apply, backupTimestamp });

function readJsonDocument(fileName, requiredArrayKeys, optionalArrayKeys = []) {
  const path = join(DATA_DIR, fileName);

  if (!existsSync(path)) {
    fail(`${fileName} is missing.`);
  }

  const raw = readFileSync(path, "utf8");
  let parsed;

  try {
    parsed = JSON.parse(raw);
  } catch (error) {
    fail(`${fileName} is not valid JSON: ${error.message}`);
  }

  if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
    fail(`${fileName} must contain a top-level JSON object.`);
  }

  for (const key of requiredArrayKeys) {
    if (!Array.isArray(parsed[key])) {
      fail(`${fileName} does not contain an array at "${key}".`);
    }
  }

  for (const key of optionalArrayKeys) {
    if (Object.hasOwn(parsed, key) && !Array.isArray(parsed[key])) {
      fail(`${fileName} contains "${key}", but it is not an array.`);
    }
  }

  return { fileName, path, parsed };
}

function planBattleResultsCleanup(document) {
  const originalRows = document.parsed.results.map((row, index) => ({ row, index }));
  const playableRows = originalRows.filter(({ row }) => !isNonPlayableHandle(row?.handle));
  const visitorRemoved = originalRows.length - playableRows.length;
  const dedupe = dedupeBattleResults(playableRows);
  const nextResults = dedupe.rows.map(({ row }) => row);
  const nextDocument = { ...document.parsed, results: nextResults };
  const totalRemoved = visitorRemoved + dedupe.removedRows;

  return createPlan(document, nextDocument, {
    removedRows: totalRemoved,
    visitorRemoved,
    replayRecordRemoved: 0,
    replayCommentRemoved: 0,
    duplicateGroups: dedupe.duplicateGroups,
    duplicateRowsRemoved: dedupe.removedRows,
  });
}

function planHandleArrayCleanup(document, arrayKey, handleField) {
  const rows = document.parsed[arrayKey];
  const nextRows = rows.filter((row) => !isNonPlayableHandle(row?.[handleField]));
  const removedRows = rows.length - nextRows.length;
  const nextDocument = { ...document.parsed, [arrayKey]: nextRows };

  return createPlan(document, nextDocument, {
    removedRows,
    visitorRemoved: removedRows,
    replayRecordRemoved: 0,
    replayCommentRemoved: 0,
    duplicateGroups: 0,
    duplicateRowsRemoved: 0,
  });
}

function planReplayRecordsCleanup(document) {
  const records = document.parsed.records;
  const comments = Object.hasOwn(document.parsed, "comments") ? document.parsed.comments : undefined;
  const nextRecords = records.filter((row) => !isNonPlayableHandle(row?.handle));
  const nextDocument = { ...document.parsed, records: nextRecords };
  let replayCommentRemoved = 0;

  if (comments) {
    const nextComments = comments.filter((row) => !isNonPlayableHandle(row?.authorHandle));
    replayCommentRemoved = comments.length - nextComments.length;
    nextDocument.comments = nextComments;
  }

  const replayRecordRemoved = records.length - nextRecords.length;

  return createPlan(document, nextDocument, {
    removedRows: replayRecordRemoved + replayCommentRemoved,
    visitorRemoved: replayRecordRemoved + replayCommentRemoved,
    replayRecordRemoved,
    replayCommentRemoved,
    duplicateGroups: 0,
    duplicateRowsRemoved: 0,
  });
}

function createPlan(document, nextDocument, stats) {
  const nextJson = `${JSON.stringify(nextDocument, null, 2)}\n`;
  const currentJson = `${JSON.stringify(document.parsed, null, 2)}\n`;

  return {
    fileName: document.fileName,
    path: document.path,
    nextJson,
    changed: stats.removedRows > 0 && nextJson !== currentJson,
    backupPath: null,
    ...stats,
  };
}

function dedupeBattleResults(rows) {
  const groups = new Map();

  for (const item of rows) {
    const key = `${normalizeHandleKey(item.row?.battleId)}\u0000${normalizeHandleKey(item.row?.handle)}`;

    if (!groups.has(key)) {
      groups.set(key, []);
    }

    groups.get(key).push(item);
  }

  const keepIndexes = new Set();
  let duplicateGroups = 0;
  let removedRows = 0;

  for (const group of groups.values()) {
    if (group.length === 1) {
      keepIndexes.add(group[0].index);
      continue;
    }

    duplicateGroups += 1;
    removedRows += group.length - 1;
    keepIndexes.add(selectBattleResultToKeep(group).index);
  }

  return {
    rows: rows.filter((item) => keepIndexes.has(item.index)),
    duplicateGroups,
    removedRows,
  };
}

function selectBattleResultToKeep(rows) {
  return rows.reduce((best, candidate) => {
    const bestFinishedAt = getFinishedAtRank(best.row?.finishedAt);
    const candidateFinishedAt = getFinishedAtRank(candidate.row?.finishedAt);

    if (candidateFinishedAt !== bestFinishedAt) {
      return candidateFinishedAt > bestFinishedAt ? candidate : best;
    }

    const bestHasResultId = hasNonEmptyValue(best.row?.resultId);
    const candidateHasResultId = hasNonEmptyValue(candidate.row?.resultId);

    if (candidateHasResultId !== bestHasResultId) {
      return candidateHasResultId ? candidate : best;
    }

    return candidate.index < best.index ? candidate : best;
  });
}

function getFinishedAtRank(value) {
  if (typeof value === "number" && Number.isFinite(value)) {
    return value;
  }

  if (typeof value === "string") {
    const trimmed = value.trim();
    const numericValue = Number(trimmed);

    if (Number.isFinite(numericValue)) {
      return numericValue;
    }

    const parsedDate = Date.parse(trimmed);

    if (Number.isFinite(parsedDate)) {
      return parsedDate;
    }
  }

  return Number.NEGATIVE_INFINITY;
}

function hasNonEmptyValue(value) {
  return String(value ?? "").trim().length > 0;
}

function normalizeHandleKey(value) {
  return String(value ?? "").trim().toLowerCase();
}

function isNonPlayableHandle(value) {
  const key = normalizeHandleKey(value);
  return key.length === 0 || VISITOR_LIKE_HANDLE_KEYS.has(key);
}

function ensureBackupTargetsAvailable(plansToBackup, timestamp) {
  for (const plan of plansToBackup) {
    const backupPath = `${plan.path}.bak-${timestamp}`;
    const tempPath = buildTempPath(plan.path, timestamp);

    if (existsSync(backupPath)) {
      fail(`Backup already exists: ${backupPath}`);
    }

    if (existsSync(tempPath)) {
      fail(`Temporary file already exists: ${tempPath}`);
    }
  }
}

function prepareTempWrites(plansToWrite, timestamp) {
  const preparedPlans = [];

  try {
    for (const plan of plansToWrite) {
      const tempPath = buildTempPath(plan.path, timestamp);
      writeFileSync(tempPath, plan.nextJson, "utf8");
      plan.tempPath = tempPath;
      preparedPlans.push(plan);
    }
  } catch (error) {
    for (const plan of preparedPlans) {
      try {
        unlinkSync(plan.tempPath);
      } catch {
        // Best-effort cleanup; the script has not touched source data yet.
      }
    }
    fail(`Could not prepare temporary writes: ${error.message}`);
  }
}

function buildTempPath(path, timestamp) {
  return `${path}.tmp-${process.pid}-${timestamp}`;
}

function formatBackupTimestamp(date) {
  const yyyy = String(date.getFullYear());
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const min = String(date.getMinutes()).padStart(2, "0");
  const ss = String(date.getSeconds()).padStart(2, "0");
  return `${yyyy}${mm}${dd}-${hh}${min}${ss}`;
}

function printSummary(plansToPrint, { apply: isApply, backupTimestamp: timestamp }) {
  console.log(`Data closure cleanup (${isApply ? "apply" : "dry-run"})`);
  console.log(`Data directory: ${DATA_DIR}`);
  console.log(`dry-run: ${isApply ? "false" : "true"}`);
  console.log(`backup timestamp: ${isApply ? timestamp : "(not created in dry-run)"}`);
  console.log("");

  for (const plan of plansToPrint) {
    console.log(plan.fileName);
    console.log(`  removed rows: ${plan.removedRows}`);
    console.log(`  visitor-like/non-playable rows removed: ${plan.visitorRemoved}`);
    console.log(`  replay records removed: ${plan.replayRecordRemoved}`);
    console.log(`  replay comments removed: ${plan.replayCommentRemoved}`);
    console.log(`  duplicate groups cleaned: ${plan.duplicateGroups}`);
    console.log(`  duplicate rows removed: ${plan.duplicateRowsRemoved}`);
    console.log(`  changed: ${plan.changed ? "yes" : "no"}`);
    console.log(`  backup: ${plan.backupPath ?? (plan.changed && !isApply ? "(dry-run)" : "(none)")}`);
    console.log(`  dry-run: ${isApply ? "false" : "true"}`);
    console.log("");
  }

  console.log(`changed files: ${plansToPrint.filter((plan) => plan.changed).length}`);
  console.log(isApply ? "Files were backed up before writing." : "No files were written. Re-run with --apply to clean data.");
}

function printUsage() {
  console.log("Usage: node scripts/cleanup-data-closure.mjs [--apply]");
  console.log("");
  console.log("Default mode is dry-run and does not write files.");
  console.log("--apply creates .bak-YYYYMMDD-HHMMSS backups before writing changed JSON files.");
}

function fail(message) {
  console.error(`Data closure cleanup failed: ${message}`);
  process.exit(1);
}
