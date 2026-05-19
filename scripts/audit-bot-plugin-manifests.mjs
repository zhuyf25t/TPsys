import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT_DIR = join(fileURLToPath(new URL("..", import.meta.url)));
const MANIFEST_PATH = join(
  ROOT_DIR,
  "frontend",
  "src",
  "domains",
  "bots",
  "runtime",
  "registry",
  "botPluginManifest.ts"
);
const EXPECTED_API_VERSION = "bot-sdk/v1";

const source = readFileSync(MANIFEST_PATH, "utf8");
const manifestArray = extractConstArray(source, "BOT_PLUGIN_MANIFESTS");
const manifestBlocks = extractTopLevelObjectBlocks(manifestArray);
const failures = [];

if (!source.includes(`BOT_PLUGIN_API_VERSION = "${EXPECTED_API_VERSION}"`)) {
  failures.push(`BOT_PLUGIN_API_VERSION must be "${EXPECTED_API_VERSION}".`);
}

if (manifestBlocks.length === 0) {
  failures.push("BOT_PLUGIN_MANIFESTS must contain at least one manifest.");
}

const pluginIds = [];
const strategyIds = [];
const botIds = [];

for (const [index, block] of manifestBlocks.entries()) {
  const label = `manifest[${index}]`;
  const pluginId = readStringField(block, "pluginId");
  const apiVersion = readApiVersionField(block);
  const strategyIdValues = readStringArrayField(block, "strategyIds");
  const botIdValues = readStringArrayField(block, "botIds");

  if (!pluginId) {
    failures.push(`${label} is missing pluginId.`);
  } else {
    pluginIds.push(pluginId);
  }

  if (!apiVersion) {
    failures.push(`${label} is missing apiVersion.`);
  } else if (apiVersion !== EXPECTED_API_VERSION && apiVersion !== "BOT_PLUGIN_API_VERSION") {
    failures.push(`${label} apiVersion must be ${EXPECTED_API_VERSION} or BOT_PLUGIN_API_VERSION.`);
  }

  if (strategyIdValues.length === 0) {
    failures.push(`${label} must declare at least one strategyId.`);
  }

  if (botIdValues.length === 0) {
    failures.push(`${label} must declare at least one botId.`);
  }

  strategyIds.push(...strategyIdValues);
  botIds.push(...botIdValues);
}

pushDuplicateFailures(failures, "pluginId", pluginIds);
pushDuplicateFailures(failures, "strategyId", strategyIds);
pushDuplicateFailures(failures, "botId", botIds);

if (failures.length > 0) {
  console.error("Bot plugin manifest audit failed:");
  for (const failure of failures) {
    console.error(`  - ${failure}`);
  }
  process.exitCode = 1;
} else {
  console.log("Bot plugin manifest audit passed.");
  console.log(`Manifest source: ${MANIFEST_PATH}`);
  console.log(`Manifests: ${manifestBlocks.length}`);
  console.log(`Strategy IDs: ${strategyIds.length}`);
  console.log(`Bot IDs: ${botIds.length}`);
}

function pushDuplicateFailures(target, label, values) {
  const duplicates = findDuplicates(values);
  if (duplicates.length > 0) {
    target.push(`Duplicate ${label} values: ${duplicates.join(", ")}.`);
  }
}

function findDuplicates(values) {
  const seen = new Set();
  const duplicates = new Set();

  for (const value of values) {
    const normalized = normalizeId(value);
    if (!normalized) {
      continue;
    }

    if (seen.has(normalized)) {
      duplicates.add(normalized);
      continue;
    }

    seen.add(normalized);
  }

  return [...duplicates].sort();
}

function readStringField(block, fieldName) {
  const match = new RegExp(`\\b${fieldName}\\s*:\\s*"([^"]+)"`).exec(block);
  return match?.[1]?.trim() ?? null;
}

function readApiVersionField(block) {
  const literal = readStringField(block, "apiVersion");
  if (literal) {
    return literal;
  }

  const match = /\bapiVersion\s*:\s*([A-Z0-9_]+)/.exec(block);
  return match?.[1]?.trim() ?? null;
}

function readStringArrayField(block, fieldName) {
  const fieldIndex = block.search(new RegExp(`\\b${fieldName}\\s*:`));
  if (fieldIndex < 0) {
    return [];
  }

  const arrayStart = block.indexOf("[", fieldIndex);
  if (arrayStart < 0) {
    return [];
  }

  const arrayEnd = findMatchingBracket(block, arrayStart, "[", "]");
  if (arrayEnd < 0) {
    return [];
  }

  const arraySource = block.slice(arrayStart, arrayEnd + 1);
  return [...arraySource.matchAll(/"([^"]+)"/g)].map((match) => match[1].trim()).filter(Boolean);
}

function extractConstArray(fullSource, constName) {
  const declarationIndex = fullSource.indexOf(`const ${constName}`);
  if (declarationIndex < 0) {
    throw new Error(`Could not find const ${constName}.`);
  }

  const arrayStart = fullSource.indexOf("[", declarationIndex);
  if (arrayStart < 0) {
    throw new Error(`Could not find array initializer for ${constName}.`);
  }

  const arrayEnd = findMatchingBracket(fullSource, arrayStart, "[", "]");
  if (arrayEnd < 0) {
    throw new Error(`Could not find closing array bracket for ${constName}.`);
  }

  return fullSource.slice(arrayStart, arrayEnd + 1);
}

function extractTopLevelObjectBlocks(arraySource) {
  const blocks = [];
  let objectStart = -1;
  let objectDepth = 0;
  let arrayDepth = 0;
  let quote = null;
  let escaped = false;

  for (let index = 0; index < arraySource.length; index += 1) {
    const char = arraySource[index];

    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === quote) {
        quote = null;
      }
      continue;
    }

    if (char === "\"" || char === "'" || char === "`") {
      quote = char;
      continue;
    }

    if (char === "[") {
      arrayDepth += 1;
      continue;
    }

    if (char === "]") {
      arrayDepth -= 1;
      continue;
    }

    if (char === "{" && arrayDepth === 1) {
      if (objectDepth === 0) {
        objectStart = index;
      }
      objectDepth += 1;
      continue;
    }

    if (char === "}" && objectDepth > 0) {
      objectDepth -= 1;
      if (objectDepth === 0 && objectStart >= 0) {
        blocks.push(arraySource.slice(objectStart, index + 1));
        objectStart = -1;
      }
    }
  }

  return blocks;
}

function findMatchingBracket(value, startIndex, openChar, closeChar) {
  let depth = 0;
  let quote = null;
  let escaped = false;

  for (let index = startIndex; index < value.length; index += 1) {
    const char = value[index];

    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === quote) {
        quote = null;
      }
      continue;
    }

    if (char === "\"" || char === "'" || char === "`") {
      quote = char;
      continue;
    }

    if (char === openChar) {
      depth += 1;
    } else if (char === closeChar) {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }

  return -1;
}

function normalizeId(value) {
  return String(value ?? "").trim().toLowerCase();
}
