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

const resultIndex = indexBattleResults(battleResults);
const mailIndex = indexBattleMails(mails);
const duplicateBattleResultAudit = auditBattleResultDuplicates(battleResults);
const ratingArithmeticAudit = auditRatingArithmetic(battleResults);
const ratingContinuityAudit = auditRatingContinuity(battleResults);
const replayResultAudit = auditReplayResultLinks(replayRecords, resultIndex);
const battleMailAudit = auditBattleMailCoverage(battleResults, mailIndex);

printReport({
  visitorAudits,
  duplicateBattleResultAudit,
  ratingArithmeticAudit,
  ratingContinuityAudit,
  replayResultAudit,
  battleMailAudit,
});

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

function normalizeIdKey(value) {
  return String(value ?? "").trim();
}

function resultLinkKey(battleId, handle) {
  return `${normalizeIdKey(battleId)}\u0000${normalizeHandleKey(handle)}`;
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

function auditRatingArithmetic(rows) {
  const samples = [];
  let invalidFieldRows = 0;
  let arithmeticMismatches = 0;

  for (const row of rows) {
    const fields = {
      ratingBefore: row?.ratingBefore,
      ratingDelta: row?.ratingDelta,
      ratingAfter: row?.ratingAfter,
    };
    const invalidFields = Object.entries(fields)
      .filter(([, value]) => !isFiniteNumber(value))
      .map(([field]) => field);

    if (invalidFields.length > 0) {
      invalidFieldRows += 1;
      pushSample(samples, {
        issue: "non_finite_rating_field",
        resultId: row?.resultId,
        battleId: row?.battleId,
        handle: row?.handle,
        invalidFields,
        ratingBefore: row?.ratingBefore,
        ratingDelta: row?.ratingDelta,
        ratingAfter: row?.ratingAfter,
      });
      continue;
    }

    const expectedAfter = row.ratingBefore + row.ratingDelta;
    if (expectedAfter !== row.ratingAfter) {
      arithmeticMismatches += 1;
      pushSample(samples, {
        issue: "rating_arithmetic_mismatch",
        resultId: row.resultId,
        battleId: row.battleId,
        handle: row.handle,
        ratingBefore: row.ratingBefore,
        ratingDelta: row.ratingDelta,
        ratingAfter: row.ratingAfter,
        expectedAfter,
      });
    }
  }

  return {
    totalRows: rows.length,
    invalidFieldRows,
    arithmeticMismatches,
    samples,
  };
}

function auditRatingContinuity(rows) {
  const groups = new Map();
  const samples = [];
  let continuityBreaks = 0;
  let invalidTimelineRows = 0;

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
      if (!isFiniteNumber(row?.finishedAt)) {
        invalidTimelineRows += 1;
        pushSample(samples, {
          issue: "non_finite_finished_at",
          resultId: row?.resultId,
          battleId: row?.battleId,
          handle: row?.handle,
          finishedAt: row?.finishedAt,
        });
      }

      if (!previous) {
        previous = row;
        continue;
      }

      if (isFiniteNumber(previous.ratingAfter) && isFiniteNumber(row?.ratingBefore)) {
        if (row.ratingBefore !== previous.ratingAfter) {
          continuityBreaks += 1;
          pushSample(samples, {
            issue: "rating_continuity_break",
            handleKey: handleKey || "(empty)",
            previousResultId: previous.resultId,
            previousBattleId: previous.battleId,
            previousFinishedAt: previous.finishedAt,
            previousRatingAfter: previous.ratingAfter,
            nextResultId: row.resultId,
            nextBattleId: row.battleId,
            nextFinishedAt: row.finishedAt,
            nextRatingBefore: row.ratingBefore,
          });
        }
      }

      previous = row;
    }
  }

  return {
    handles: groups.size,
    continuityBreaks,
    invalidTimelineRows,
    samples,
  };
}

function auditReplayResultLinks(replayRows, resultsByLinkKey) {
  const samples = [];
  let linked = 0;
  let missingResult = 0;
  let duplicateResultLinks = 0;
  let mismatchedReplays = 0;
  let fieldMismatches = 0;

  for (const replay of replayRows) {
    const key = resultLinkKey(replay?.battleId, replay?.handle);
    const resultRows = resultsByLinkKey.get(key) ?? [];

    if (resultRows.length === 0) {
      missingResult += 1;
      pushSample(samples, {
        issue: "missing_result_for_replay",
        replayId: replay?.replayId,
        battleId: replay?.battleId,
        handle: replay?.handle,
      });
      continue;
    }

    linked += 1;
    if (resultRows.length > 1) {
      duplicateResultLinks += 1;
    }

    const result = chooseBestResultForReplay(replay, resultRows);
    const mismatches = compareProjectionFields(replay, result, [
      "ratingBefore",
      "ratingDelta",
      "ratingAfter",
      "score",
      "placement",
    ]);

    if (mismatches.length > 0) {
      mismatchedReplays += 1;
      fieldMismatches += mismatches.length;
      pushSample(samples, {
        issue: "replay_result_field_mismatch",
        replayId: replay?.replayId,
        resultId: result?.resultId,
        battleId: replay?.battleId,
        handle: replay?.handle,
        mismatches,
      });
    }
  }

  return {
    totalReplayRows: replayRows.length,
    linked,
    missingResult,
    duplicateResultLinks,
    mismatchedReplays,
    fieldMismatches,
    samples,
  };
}

function auditBattleMailCoverage(resultRows, mailIdsByOwner) {
  const samples = [];
  let coveredByNewId = 0;
  let coveredByLegacyId = 0;
  let missing = 0;

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
    pushSample(samples, {
      issue: "missing_battle_mail",
      resultId: result?.resultId,
      battleId: result?.battleId,
      handle: result?.handle,
      expectedNewId: newId,
      acceptedLegacyId: legacyId,
    });
  }

  return {
    totalResultRows: resultRows.length,
    coveredByNewId,
    coveredByLegacyId,
    missing,
    samples,
  };
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

function chooseBestResultForReplay(replay, resultRows) {
  const replayFinishedAt = replay?.finishedAt;
  if (!isFiniteNumber(replayFinishedAt)) {
    return resultRows[0];
  }

  return [...resultRows].sort((left, right) => {
    const leftDelta = Math.abs((isFiniteNumber(left?.finishedAt) ? left.finishedAt : 0) - replayFinishedAt);
    const rightDelta = Math.abs((isFiniteNumber(right?.finishedAt) ? right.finishedAt : 0) - replayFinishedAt);
    return leftDelta - rightDelta;
  })[0];
}

function compareProjectionFields(replay, result, fields) {
  const mismatches = [];

  for (const field of fields) {
    if (!Object.hasOwn(replay ?? {}, field)) {
      continue;
    }

    const replayValue = replay?.[field] ?? null;
    const resultValue = result?.[field] ?? null;
    if (replayValue !== resultValue) {
      mismatches.push({
        field,
        replay: replayValue,
        result: resultValue,
      });
    }
  }

  return mismatches;
}

function compareByFinishedAtAsc(left, right) {
  const leftTime = isFiniteNumber(left?.finishedAt) ? left.finishedAt : Number.NEGATIVE_INFINITY;
  const rightTime = isFiniteNumber(right?.finishedAt) ? right.finishedAt : Number.NEGATIVE_INFINITY;
  if (leftTime !== rightTime) {
    return leftTime - rightTime;
  }

  return String(left?.resultId ?? "").localeCompare(String(right?.resultId ?? ""));
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

function printReport({
  visitorAudits,
  duplicateBattleResultAudit,
  ratingArithmeticAudit,
  ratingContinuityAudit,
  replayResultAudit,
  battleMailAudit,
}) {
  console.log("Data closure audit (read-only)");
  console.log(`Data directory: ${DATA_DIR}`);
  console.log("Visitor-like aliases: visitor, guest, anonymous, anon, \\u8bbf\\u5ba2, \\u6e38\\u5ba2, \\u672a\\u767b\\u5f55; empty handles are non-playable.");
  console.log(`Sample limit per section: ${SAMPLE_LIMIT}`);
  console.log("");

  for (const audit of visitorAudits) {
    console.log(`${audit.label}: ${audit.matches} visitor-like/non-playable rows out of ${audit.total}`);
    printBuckets(audit.buckets);
    printSamples(audit.samples);
    console.log("");
  }

  console.log("battle result duplicate logical groups:");
  console.log(`  total rows: ${duplicateBattleResultAudit.totalRows}`);
  console.log(`  duplicate groups: ${duplicateBattleResultAudit.duplicateGroups}`);
  console.log(`  rows in duplicate groups: ${duplicateBattleResultAudit.duplicateRows}`);
  console.log(`  excess duplicate rows beyond one per logical key: ${duplicateBattleResultAudit.excessRows}`);
  printSamples(duplicateBattleResultAudit.samples);
  console.log("");

  console.log("battle result rating arithmetic:");
  console.log(`  total rows: ${ratingArithmeticAudit.totalRows}`);
  console.log(`  rows with non-finite rating fields: ${ratingArithmeticAudit.invalidFieldRows}`);
  console.log(`  ratingBefore + ratingDelta != ratingAfter: ${ratingArithmeticAudit.arithmeticMismatches}`);
  printSamples(ratingArithmeticAudit.samples);
  console.log("");

  console.log("per-handle rating continuity:");
  console.log(`  handles: ${ratingContinuityAudit.handles}`);
  console.log(`  continuity breaks: ${ratingContinuityAudit.continuityBreaks}`);
  console.log(`  rows with non-finite finishedAt: ${ratingContinuityAudit.invalidTimelineRows}`);
  printSamples(ratingContinuityAudit.samples);
  console.log("");

  console.log("replay/result association:");
  console.log(`  replay rows: ${replayResultAudit.totalReplayRows}`);
  console.log(`  linked to at least one result: ${replayResultAudit.linked}`);
  console.log(`  missing result rows: ${replayResultAudit.missingResult}`);
  console.log(`  replay links with duplicate candidate results: ${replayResultAudit.duplicateResultLinks}`);
  console.log(`  replay rows with field mismatches: ${replayResultAudit.mismatchedReplays}`);
  console.log(`  mismatched fields: ${replayResultAudit.fieldMismatches}`);
  printSamples(replayResultAudit.samples);
  console.log("");

  console.log("battle mail coverage:");
  console.log(`  result rows: ${battleMailAudit.totalResultRows}`);
  console.log(`  covered by mail-battle-resultId: ${battleMailAudit.coveredByNewId}`);
  console.log(`  covered by legacy mail-battle-battleId: ${battleMailAudit.coveredByLegacyId}`);
  console.log(`  missing battle mail coverage: ${battleMailAudit.missing}`);
  printSamples(battleMailAudit.samples);
}

function printBuckets(buckets) {
  if (buckets.length === 0) {
    console.log("  buckets: none");
    return;
  }

  console.log("  buckets:");
  for (const bucket of buckets) {
    console.log(`    ${escapeNonAscii(bucket.handle)}: ${bucket.count}`);
  }
}

function printSamples(samples) {
  if (samples.length === 0) {
    console.log("  samples: none");
    return;
  }

  console.log(`  samples (first ${samples.length}):`);
  for (const sample of samples) {
    console.log(`    ${safeJson(sample)}`);
  }
}

function safeJson(value) {
  return escapeNonAscii(JSON.stringify(value));
}

function escapeNonAscii(value) {
  return String(value).replace(/[^\x20-\x7e]/g, (char) => {
    const codePoint = char.codePointAt(0);
    if (codePoint <= 0xffff) {
      return `\\u${codePoint.toString(16).padStart(4, "0")}`;
    }
    return `\\u{${codePoint.toString(16)}}`;
  });
}
