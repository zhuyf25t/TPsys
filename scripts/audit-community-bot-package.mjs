import { existsSync, readFileSync } from "node:fs";
import { readdir } from "node:fs/promises";
import { dirname, extname, join, relative, resolve, sep } from "node:path";
import { pathToFileURL, fileURLToPath } from "node:url";

const ROOT_DIR = fileURLToPath(new URL("..", import.meta.url));
const PACKAGE_ROOT = join(ROOT_DIR, "examples", "bots");
const EXPECTED_API_VERSION = "bot-sdk/v1";
const ALLOWED_PERMISSIONS = new Set(["bot:read-context", "bot:issue-command"]);
const REQUIRED_FIELDS = [
  "pluginId",
  "displayName",
  "version",
  "apiVersion",
  "entry",
  "exportName",
  "strategyIds",
  "botIds",
  "permissions"
];
const OPTIONAL_FIELDS = new Set(["author", "description"]);
const failures = [];

const manifestPaths = await findManifestPaths(PACKAGE_ROOT);

if (manifestPaths.length === 0) {
  failures.push(`No *.plugin.json community bot package manifests found under ${PACKAGE_ROOT}.`);
}

for (const manifestPath of manifestPaths) {
  await auditManifest(manifestPath);
}

if (failures.length > 0) {
  console.error("Community bot package audit failed:");
  for (const failure of failures) {
    console.error(`  - ${failure}`);
  }
  process.exitCode = 1;
} else {
  console.log("Community bot package audit passed.");
  console.log(`Package root: ${PACKAGE_ROOT}`);
  console.log(`Manifests: ${manifestPaths.length}`);
}

async function auditManifest(manifestPath) {
  const label = relative(ROOT_DIR, manifestPath);
  let manifest;

  try {
    manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  } catch (error) {
    failures.push(`${label}: invalid JSON (${error.message}).`);
    return;
  }

  if (!isPlainObject(manifest)) {
    failures.push(`${label}: manifest must be a JSON object.`);
    return;
  }

  auditAllowedFields(manifest, label);
  auditRequiredString(manifest, "pluginId", label, { id: true });
  auditRequiredString(manifest, "displayName", label);
  auditRequiredString(manifest, "version", label);
  auditRequiredString(manifest, "apiVersion", label);
  auditRequiredString(manifest, "entry", label);
  auditRequiredString(manifest, "exportName", label);
  auditRequiredStringArray(manifest, "strategyIds", label, { ids: true });
  auditRequiredStringArray(manifest, "botIds", label, { ids: true });
  auditPermissions(manifest, label);

  if (manifest.apiVersion !== EXPECTED_API_VERSION) {
    failures.push(`${label}: apiVersion must be "${EXPECTED_API_VERSION}".`);
  }

  const entryPath = resolveEntryPath(manifestPath, manifest.entry, label);
  if (!entryPath) {
    return;
  }

  const strategy = await importStrategy(entryPath, manifest.exportName, label);
  if (!strategy) {
    return;
  }

  if (!manifest.strategyIds.includes(strategy.strategyId)) {
    failures.push(`${label}: exported strategyId "${strategy.strategyId}" is not declared in strategyIds.`);
  }
}

function auditAllowedFields(manifest, label) {
  const allowedFields = new Set([...REQUIRED_FIELDS, ...OPTIONAL_FIELDS]);
  for (const fieldName of Object.keys(manifest)) {
    if (!allowedFields.has(fieldName)) {
      failures.push(`${label}: unexpected field "${fieldName}".`);
    }
  }
}

function auditRequiredString(manifest, fieldName, label, options = {}) {
  const value = manifest[fieldName];
  if (typeof value !== "string") {
    failures.push(`${label}: ${fieldName} must be a string.`);
    return;
  }

  if (value.trim() !== value || value.length === 0) {
    failures.push(`${label}: ${fieldName} must be non-empty and must not have surrounding whitespace.`);
  }

  if (options.id && !/^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$/.test(value)) {
    failures.push(`${label}: ${fieldName} must be a lowercase dash-separated id.`);
  }
}

function auditRequiredStringArray(manifest, fieldName, label, options = {}) {
  const values = manifest[fieldName];
  if (!Array.isArray(values)) {
    failures.push(`${label}: ${fieldName} must be an array.`);
    return;
  }

  if (values.length === 0) {
    failures.push(`${label}: ${fieldName} must not be empty.`);
    return;
  }

  const seen = new Set();
  for (const [index, value] of values.entries()) {
    if (typeof value !== "string" || value.trim() !== value || value.length === 0) {
      failures.push(`${label}: ${fieldName}[${index}] must be a non-empty string without surrounding whitespace.`);
      continue;
    }

    if (options.ids && !/^[a-z0-9][a-z0-9-]*[a-z0-9]$|^[a-z0-9]$/.test(value)) {
      failures.push(`${label}: ${fieldName}[${index}] must be a lowercase dash-separated id.`);
    }

    if (seen.has(value)) {
      failures.push(`${label}: ${fieldName} contains duplicate value "${value}".`);
    }
    seen.add(value);
  }
}

function auditPermissions(manifest, label) {
  const permissions = manifest.permissions;
  auditRequiredStringArray(manifest, "permissions", label);

  if (!Array.isArray(permissions)) {
    return;
  }

  for (const permission of permissions) {
    if (!ALLOWED_PERMISSIONS.has(permission)) {
      failures.push(
        `${label}: permission "${permission}" is not allowed; use only ${[...ALLOWED_PERMISSIONS].join(", ")}.`
      );
    }
  }
}

function resolveEntryPath(manifestPath, entry, label) {
  if (typeof entry !== "string") {
    return null;
  }

  if (/^[a-z][a-z0-9+.-]*:/i.test(entry) || entry.startsWith("//")) {
    failures.push(`${label}: entry must be a local relative path, not a remote URL or absolute specifier.`);
    return null;
  }

  if (!entry.startsWith("./")) {
    failures.push(`${label}: entry must start with "./".`);
    return null;
  }

  const manifestDir = dirname(manifestPath);
  const resolvedEntry = resolve(manifestDir, entry);
  const relativeToManifest = relative(manifestDir, resolvedEntry);
  if (relativeToManifest === ".." || relativeToManifest.startsWith(`..${sep}`) || relativeToManifest === "") {
    failures.push(`${label}: entry must resolve inside the manifest package directory.`);
    return null;
  }

  if (extname(resolvedEntry) !== ".mjs") {
    failures.push(`${label}: entry must point to a .mjs ESM module.`);
    return null;
  }

  if (!existsSync(resolvedEntry)) {
    failures.push(`${label}: entry file does not exist at ${relative(ROOT_DIR, resolvedEntry)}.`);
    return null;
  }

  return resolvedEntry;
}

async function importStrategy(entryPath, exportName, label) {
  let moduleNamespace;
  try {
    moduleNamespace = await import(pathToFileURL(entryPath).href);
  } catch (error) {
    failures.push(`${label}: failed to import entry (${error.message}).`);
    return null;
  }

  if (typeof exportName !== "string") {
    return null;
  }

  if (!Object.hasOwn(moduleNamespace, exportName)) {
    failures.push(`${label}: entry does not export "${exportName}".`);
    return null;
  }

  const strategy = moduleNamespace[exportName];
  if (!isPlainObject(strategy)) {
    failures.push(`${label}: export "${exportName}" must be a strategy object.`);
    return null;
  }

  if (typeof strategy.strategyId !== "string" || strategy.strategyId.trim() !== strategy.strategyId || !strategy.strategyId) {
    failures.push(`${label}: export "${exportName}".strategyId must be a non-empty string without surrounding whitespace.`);
  }

  if (typeof strategy.decide !== "function") {
    failures.push(`${label}: export "${exportName}".decide must be a function.`);
  }

  return strategy;
}

async function findManifestPaths(rootDir) {
  const results = [];
  const entries = await readdir(rootDir, { withFileTypes: true });

  for (const entry of entries) {
    const fullPath = join(rootDir, entry.name);
    if (entry.isDirectory()) {
      results.push(...(await findManifestPaths(fullPath)));
    } else if (entry.isFile() && entry.name.endsWith(".plugin.json")) {
      results.push(fullPath);
    }
  }

  return results.sort();
}

function isPlainObject(value) {
  return Boolean(value) && typeof value === "object" && !Array.isArray(value);
}
