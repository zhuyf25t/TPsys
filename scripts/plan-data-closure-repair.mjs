import { readFileSync } from "node:fs";
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

const ROOT_DIR = fileURLToPath(new URL("..", import.meta.url));
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

const criticalAudit = auditCriticalFindings({
  battleResults,
  mails,
  replayRecords,
  identityAccounts,
});

printHeader();
printInputSummary();
printCriticalAudit(criticalAudit);

if (criticalAudit.blockedByCriticalAuditFindings) {
  printCriticalBlock();
  process.exit(0);
}

const resultIndex = indexBattleResults(battleResults);
const mailIndex = indexBattleMails(mails);
const ratingContinuityPlan = planRatingContinuityBreaks(battleResults);
const replayWithoutResultPlan = planReplayWithoutResult(replayRecords, resultIndex);
const missingBattleMailPlan = planMissingBattleMails(battleResults, mails, mailIndex);

printRepairSummary({
  ratingContinuityPlan,
  replayWithoutResultPlan,
  missingBattleMailPlan,
});
printRatingContinuityPlan(ratingContinuityPlan);
printReplayWithoutResultPlan(replayWithoutResultPlan);
printMissingBattleMailPlan(missingBattleMailPlan);
printFooter();

function readJsonArray(fileName, arrayKey) {
  const path = join(DATA_DIR, fileName);
  const parsed = JSON.parse(readFileSync(path, "utf8"));
  const rows = parsed[arrayKey];

  if (!Array.isArray(rows)) {
    throw new Error(`${fileName} does not contain an array at "${arrayKey}".`);
  }

  return rows;
}

function auditCriticalFindings(input) {
  const visitorAudits = [
    auditVisitorLikeRows("battle results", input.battleResults, "handle", (row) => ({
      resultId: row?.resultId,
      battleId: row?.battleId,
      handle: row?.handle,
    })),
    auditVisitorLikeRows("mails", input.mails, "ownerHandle", (row) => ({
      id: row?.id,
      ownerHandle: row?.ownerHandle,
      kind: row?.kind,
    })),
    auditVisitorLikeRows("replay records", input.replayRecords, "handle", (row) => ({
      replayId: row?.replayId,
      battleId: row?.battleId,
      handle: row?.handle,
    })),
    auditVisitorLikeRows("identity accounts", input.identityAccounts, "handle", (row) => ({
      userId: row?.userId,
      handle: row?.handle,
      active: row?.active,
    })),
  ];
  const duplicateBattleResultAudit = auditBattleResultDuplicates(input.battleResults);
  const visitorLikeRows = visitorAudits.reduce((sum, audit) => sum + audit.matches, 0);

  return {
    blockedByCriticalAuditFindings:
      visitorLikeRows > 0 || duplicateBattleResultAudit.duplicateGroups > 0,
    visitorLikeRows,
    visitorAudits,
    duplicateBattleResultAudit,
  };
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
      resultIds: group.rows.map((row) => row?.resultId ?? "(missing)").slice(0, 5),
      finishedAt: group.rows.map((row) => row?.finishedAt ?? null).slice(0, 5),
    })),
  };
}

function planRatingContinuityBreaks(rows) {
  const groups = new Map();
  const samples = [];
  let continuityBreaks = 0;

  for (const row of rows) {
    const handleKey = normalizeHandleKey(row?.handle);
    if (!groups.has(handleKey)) {
      groups.set(handleKey, []);
    }
    groups.get(handleKey).push(row);
  }

  for (const [handleKey, groupRows] of groups) {
    const sortedRows = [...groupRows].sort(compareByFinishedAtAsc);
    let previous = null;

    for (const row of sortedRows) {
      if (!previous) {
        previous = row;
        continue;
      }

      if (isFiniteNumber(previous?.ratingAfter) && isFiniteNumber(row?.ratingBefore)) {
        if (row.ratingBefore !== previous.ratingAfter) {
          continuityBreaks += 1;
          pushSample(samples, {
            issue: "rating_continuity_break",
            repairClass: "unsafe_auto_repair",
            reason:
              "Historical rating continuity can cascade through later rows; do not patch without a manual rating policy decision.",
            handle: row?.handle ?? handleKey,
            handleKey: handleKey || "(empty)",
            expectedNextRatingBefore: previous.ratingAfter,
            actualNextRatingBefore: row.ratingBefore,
            previous: toResultReference(previous),
            next: toResultReference(row),
          });
        }
      }

      previous = row;
    }
  }

  return {
    totalRows: rows.length,
    handles: groups.size,
    continuityBreaks,
    repairClass: "unsafe_auto_repair",
    autoPatchCount: 0,
    samples,
  };
}

function planReplayWithoutResult(replayRows, resultsByLinkKey) {
  const samplesByClass = {
    likely_system_or_bot: [],
    needs_result_decision: [],
  };
  const countsByClass = {
    likely_system_or_bot: 0,
    needs_result_decision: 0,
  };
  let missingResult = 0;
  let linked = 0;

  for (const replay of replayRows) {
    const key = resultLinkKey(replay?.battleId, replay?.handle);
    const resultRows = resultsByLinkKey.get(key) ?? [];

    if (resultRows.length > 0) {
      linked += 1;
      continue;
    }

    missingResult += 1;
    const repairClass = classifyReplayWithoutResult(replay);
    countsByClass[repairClass] += 1;
    pushSample(samplesByClass[repairClass], {
      issue: "replay_without_result",
      repairClass,
      resultPlanGenerated: false,
      reason:
        repairClass === "likely_system_or_bot"
          ? "Replay appears to be a bot/system or contract-smoke artifact; do not synthesize a user result."
          : "Replay has no matching result and needs an explicit product/data decision before a result can exist.",
      replayId: replay?.replayId,
      battleId: replay?.battleId,
      handle: replay?.handle,
      finishedAt: replay?.finishedAt ?? null,
    });
  }

  return {
    totalReplayRows: replayRows.length,
    linked,
    missingResult,
    autoResultPlanCount: 0,
    countsByClass,
    samplesByClass,
  };
}

function planMissingBattleMails(resultRows, mailRows, mailIdsByOwner) {
  const globalMailIds = new Set(mailRows.map((mail) => String(mail?.id ?? "")));
  const samples = [];
  let coveredByNewId = 0;
  let coveredByLegacyId = 0;
  let missing = 0;
  let suggestedMailPlans = 0;

  for (const result of resultRows) {
    const ownerKey = normalizeHandleKey(result?.handle);
    const ownerMailIds = mailIdsByOwner.get(ownerKey) ?? new Set();
    const newId = `mail-battle-${result?.resultId ?? ""}`;
    const legacyId = `mail-battle-${result?.battleId ?? ""}`;

    if (ownerMailIds.has(newId)) {
      coveredByNewId += 1;
      continue;
    }

    if (ownerMailIds.has(legacyId)) {
      coveredByLegacyId += 1;
      continue;
    }

    missing += 1;
    suggestedMailPlans += 1;
    pushSample(samples, {
      issue: "battle_result_without_battle_mail",
      repairClass: "suggested_mail",
      result: toResultReference(result),
      expectedNewId: newId,
      acceptedLegacyId: legacyId,
      mailIdAlreadyExistsGlobally: globalMailIds.has(newId),
      suggestedMail: buildSuggestedBattleMail(result, newId),
    });
  }

  return {
    totalResultRows: resultRows.length,
    coveredByNewId,
    coveredByLegacyId,
    missing,
    suggestedMailPlans,
    filesWritten: 0,
    samples,
  };
}

function buildSuggestedBattleMail(result, id) {
  const title = "\u6218\u6597\u7ed3\u7b97\u4e0e\u8bc4\u5206\u66f4\u65b0";
  const body = buildBattleMailBody(result);
  const excerpt = buildBattleMailExcerpt(result);

  return {
    id,
    ownerHandle: result?.handle ?? "",
    kind: "battle",
    title,
    body,
    subject: title,
    excerpt,
    senderLabel: "\u6218\u6597\u8bb0\u5f55",
    createdAt: isFiniteNumber(result?.finishedAt) ? result.finishedAt : null,
    read: false,
    unread: true,
    important: true,
    sourceBattleId: result?.battleId ?? null,
    sourceResultId: result?.resultId ?? null,
  };
}

function buildBattleMailBody(result) {
  const displayName = nonEmptyString(result?.displayName) ?? nonEmptyString(result?.handle) ?? "\u73a9\u5bb6";
  const placement = isFiniteNumber(result?.placement) ? `\u7b2c ${result.placement} \u540d` : "\u672a\u77e5\u6392\u540d";
  const score = isFiniteNumber(result?.score) ? String(result.score) : "\u672a\u77e5";
  const ratingLine =
    isFiniteNumber(result?.ratingBefore) && isFiniteNumber(result?.ratingDelta) && isFiniteNumber(result?.ratingAfter)
      ? `\u8bc4\u5206 ${result.ratingBefore} -> ${result.ratingAfter}\uff08${formatSigned(result.ratingDelta)}\uff09\u3002`
      : "\u8bc4\u5206\u4fe1\u606f\u9700\u8981\u4eba\u5de5\u786e\u8ba4\u3002";

  return `${displayName} \u5b8c\u6210\u4e00\u573a\u6218\u6597\uff0c\u6392\u540d${placement}\uff0c\u5f97\u5206 ${score}\u3002${ratingLine}`;
}

function buildBattleMailExcerpt(result) {
  if (isFiniteNumber(result?.ratingDelta) && isFiniteNumber(result?.ratingAfter)) {
    return `\u6218\u62a5\u548c\u56de\u653e\u5df2\u751f\u6210\uff0c\u672c\u5c40\u8bc4\u5206\u53d8\u52a8 ${formatSigned(
      result.ratingDelta
    )}\uff0c\u5f53\u524d\u8bc4\u5206 ${result.ratingAfter}\u3002`;
  }

  return "\u6218\u62a5\u548c\u56de\u653e\u5df2\u751f\u6210\uff0c\u8bc4\u5206\u4fe1\u606f\u9700\u8981\u4eba\u5de5\u786e\u8ba4\u3002";
}

function classifyReplayWithoutResult(replay) {
  const handleKey = normalizeHandleKey(replay?.handle);
  const battleIdKey = normalizeIdKey(replay?.battleId).toLowerCase();
  const replayIdKey = normalizeIdKey(replay?.replayId).toLowerCase();

  if (
    handleKey.startsWith("cpu-") ||
    battleIdKey.startsWith("contract-smoke-") ||
    replayIdKey.startsWith("contract-smoke-")
  ) {
    return "likely_system_or_bot";
  }

  return "needs_result_decision";
}

function indexBattleResults(rows) {
  const index = new Map();

  for (const row of rows) {
    const key = resultLinkKey(row?.battleId, row?.handle);
    if (!index.has(key)) {
      index.set(key, []);
    }
    index.get(key).push(row);
  }

  return index;
}

function indexBattleMails(rows) {
  const index = new Map();

  for (const row of rows) {
    if (row?.kind !== "battle") {
      continue;
    }

    const ownerKey = normalizeHandleKey(row?.ownerHandle);
    if (!index.has(ownerKey)) {
      index.set(ownerKey, new Set());
    }
    index.get(ownerKey).add(String(row?.id ?? ""));
  }

  return index;
}

function resultLinkKey(battleId, handle) {
  return `${normalizeIdKey(battleId)}\u0000${normalizeHandleKey(handle)}`;
}

function toResultReference(row) {
  return {
    resultId: row?.resultId,
    battleId: row?.battleId,
    handle: row?.handle,
    finishedAt: row?.finishedAt ?? null,
    ratingBefore: row?.ratingBefore ?? null,
    ratingDelta: row?.ratingDelta ?? null,
    ratingAfter: row?.ratingAfter ?? null,
    score: row?.score ?? null,
    placement: row?.placement ?? null,
  };
}

function compareByFinishedAtAsc(left, right) {
  const leftTime = isFiniteNumber(left?.finishedAt) ? left.finishedAt : Number.NEGATIVE_INFINITY;
  const rightTime = isFiniteNumber(right?.finishedAt) ? right.finishedAt : Number.NEGATIVE_INFINITY;
  if (leftTime !== rightTime) {
    return leftTime - rightTime;
  }

  return String(left?.resultId ?? "").localeCompare(String(right?.resultId ?? ""));
}

function normalizeHandleKey(value) {
  return String(value ?? "").trim().toLowerCase();
}

function normalizeIdKey(value) {
  return String(value ?? "").trim();
}

function isNonPlayableHandle(value) {
  const key = normalizeHandleKey(value);
  return key.length === 0 || VISITOR_LIKE_HANDLE_KEYS.has(key);
}

function bucketHandle(value) {
  const key = normalizeHandleKey(value);
  return key.length === 0 ? "(empty)" : key;
}

function isFiniteNumber(value) {
  return typeof value === "number" && Number.isFinite(value);
}

function nonEmptyString(value) {
  const text = String(value ?? "").trim();
  return text.length > 0 ? text : null;
}

function formatSigned(value) {
  if (!isFiniteNumber(value)) {
    return String(value ?? "");
  }

  return value > 0 ? `+${value}` : String(value);
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

function pushSample(samples, sample) {
  if (samples.length < SAMPLE_LIMIT) {
    samples.push(sample);
  }
}

function printHeader() {
  console.log("Data closure repair plan (dry-run, read-only)");
  console.log(`Data directory: ${DATA_DIR}`);
  console.log(`Sample limit per repair class: ${SAMPLE_LIMIT}`);
  console.log("Writes: 0");
  console.log("Network: disabled/not used");
  console.log("");
}

function printInputSummary() {
  console.log("input row counts:");
  console.log(`  battle-results.json results: ${battleResults.length}`);
  console.log(`  replay-records.json records: ${replayRecords.length}`);
  console.log(`  mails.json mails: ${mails.length}`);
  console.log(`  identity-accounts.json accounts: ${identityAccounts.length}`);
  console.log("");
}

function printCriticalAudit(audit) {
  console.log("critical preflight:");
  console.log(`  blocked_by_critical_audit_findings: ${audit.blockedByCriticalAuditFindings}`);
  console.log(`  visitor-like/non-playable rows: ${audit.visitorLikeRows}`);

  for (const visitorAudit of audit.visitorAudits) {
    console.log(`  ${visitorAudit.label}: ${visitorAudit.matches} of ${visitorAudit.total}`);
    printBuckets(visitorAudit.buckets, "    ");
    printSamples(visitorAudit.samples, "    ");
  }

  console.log("  duplicate battle result logical groups:");
  console.log(`    total rows: ${audit.duplicateBattleResultAudit.totalRows}`);
  console.log(`    duplicate groups: ${audit.duplicateBattleResultAudit.duplicateGroups}`);
  console.log(`    rows in duplicate groups: ${audit.duplicateBattleResultAudit.duplicateRows}`);
  console.log(`    excess duplicate rows: ${audit.duplicateBattleResultAudit.excessRows}`);
  printSamples(audit.duplicateBattleResultAudit.samples, "    ");
  console.log("");
}

function printCriticalBlock() {
  console.log("repair planning skipped:");
  console.log("  reason: critical audit findings must be cleaned before planning dependent history repairs.");
  console.log("  next action: run the existing data closure cleanup first, then re-run this repair plan.");
  console.log("  suggested command: npm run data:closure-cleanup -- --apply");
  console.log("  files written by this script: 0");
}

function printRepairSummary({ ratingContinuityPlan, replayWithoutResultPlan, missingBattleMailPlan }) {
  console.log("aggregate repair summary:");
  console.log(`  rating continuity breaks: ${ratingContinuityPlan.continuityBreaks}`);
  console.log(`  rating unsafe_auto_repair plans: ${ratingContinuityPlan.continuityBreaks}`);
  console.log(`  replay without result rows: ${replayWithoutResultPlan.missingResult}`);
  console.log(`  replay likely_system_or_bot: ${replayWithoutResultPlan.countsByClass.likely_system_or_bot}`);
  console.log(`  replay needs_result_decision: ${replayWithoutResultPlan.countsByClass.needs_result_decision}`);
  console.log(`  battle result without battle mail rows: ${missingBattleMailPlan.missing}`);
  console.log(`  suggested_mail plans: ${missingBattleMailPlan.suggestedMailPlans}`);
  console.log("  automatic data patches generated: 0");
  console.log("  files written: 0");
  console.log("");
}

function printRatingContinuityPlan(plan) {
  console.log("rating continuity breaks:");
  console.log(`  repair_class: ${plan.repairClass}`);
  console.log(`  handles scanned: ${plan.handles}`);
  console.log(`  breaks: ${plan.continuityBreaks}`);
  console.log(`  automatic patches: ${plan.autoPatchCount}`);
  printSamples(plan.samples, "  ");
  console.log("");
}

function printReplayWithoutResultPlan(plan) {
  console.log("replay without result:");
  console.log(`  replay rows: ${plan.totalReplayRows}`);
  console.log(`  linked rows: ${plan.linked}`);
  console.log(`  missing result rows: ${plan.missingResult}`);
  console.log(`  automatic result plans: ${plan.autoResultPlanCount}`);
  console.log(`  likely_system_or_bot: ${plan.countsByClass.likely_system_or_bot}`);
  printSamples(plan.samplesByClass.likely_system_or_bot, "  ");
  console.log(`  needs_result_decision: ${plan.countsByClass.needs_result_decision}`);
  printSamples(plan.samplesByClass.needs_result_decision, "  ");
  console.log("");
}

function printMissingBattleMailPlan(plan) {
  console.log("battle result without battle mail:");
  console.log(`  result rows: ${plan.totalResultRows}`);
  console.log(`  covered by mail-battle-resultId: ${plan.coveredByNewId}`);
  console.log(`  covered by legacy mail-battle-battleId: ${plan.coveredByLegacyId}`);
  console.log(`  missing battle mail coverage: ${plan.missing}`);
  console.log(`  repair_class: suggested_mail`);
  console.log(`  suggested_mail plans: ${plan.suggestedMailPlans}`);
  console.log("  files written: 0");
  console.log(
    "  schema note: current stored mail rows use subject/excerpt/senderLabel/unread; suggested samples also include title/body/read/source* review fields."
  );
  printSamples(plan.samples, "  ");
  console.log("");
}

function printFooter() {
  console.log("dry-run complete: no backend/data files were written.");
}

function printBuckets(buckets, indent) {
  if (buckets.length === 0) {
    console.log(`${indent}buckets: none`);
    return;
  }

  console.log(`${indent}buckets:`);
  for (const bucket of buckets) {
    console.log(`${indent}  ${escapeNonAscii(bucket.handle)}: ${bucket.count}`);
  }
}

function printSamples(samples, indent) {
  if (samples.length === 0) {
    console.log(`${indent}samples: none`);
    return;
  }

  console.log(`${indent}samples (first ${samples.length}, capped at ${SAMPLE_LIMIT}):`);
  for (const sample of samples) {
    console.log(`${indent}  ${safeJson(sample)}`);
  }
}

function safeJson(value) {
  return escapeNonAscii(JSON.stringify(value));
}

function escapeNonAscii(value) {
  return String(value).replace(/[^\x20-\x7e]/gu, (char) => {
    const codePoint = char.codePointAt(0);
    if (codePoint <= 0xffff) {
      return `\\u${codePoint.toString(16).padStart(4, "0")}`;
    }

    const offset = codePoint - 0x10000;
    const high = 0xd800 + (offset >> 10);
    const low = 0xdc00 + (offset & 0x3ff);
    return `\\u${high.toString(16).padStart(4, "0")}\\u${low.toString(16).padStart(4, "0")}`;
  });
}
