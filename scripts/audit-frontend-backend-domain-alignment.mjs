import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = process.cwd();
const backendRoot = path.join(repoRoot, "backend", "src", "main", "scala", "slaydemo", "backend");
const frontendDomainRoot = path.join(repoRoot, "frontend", "src", "domains");
const frontendRoot = path.join(repoRoot, "frontend", "src");
const failures = [];

const expectedBusinessDomains = ["battle", "bots", "forum", "governance", "identity", "mail", "replay", "social"];
const allowedBackendInfrastructureDomains = ["shared"];
const forbiddenFrontendBusinessAliases = [
  "api",
  "auth",
  "contribution",
  "home",
  "loadout",
  "mails",
  "profile",
  "rating"
];

function fail(message) {
  failures.push(message);
}

function isDirectory(target) {
  try {
    return fs.statSync(target).isDirectory();
  } catch {
    return false;
  }
}

function listDirectoryNames(target) {
  if (!isDirectory(target)) {
    return [];
  }

  return fs
    .readdirSync(target, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
}

function listFilesByExtension(target, extension) {
  if (!isDirectory(target)) {
    return [];
  }

  const files = [];
  const stack = [target];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (entry.name.endsWith(extension)) {
        files.push(fullPath);
      }
    }
  }
  return files;
}

function compareSets(label, expected, actual) {
  const missing = expected.filter((entry) => !actual.includes(entry));
  const extra = actual.filter((entry) => !expected.includes(entry));

  for (const entry of missing) {
    fail(`${label} missing: ${entry}`);
  }
  for (const entry of extra) {
    fail(`${label} has unexpected entry: ${entry}`);
  }
}

if (!isDirectory(backendRoot)) {
  fail(`Missing backend root: ${path.relative(repoRoot, backendRoot)}`);
}
if (!isDirectory(frontendDomainRoot)) {
  fail(`Missing frontend domain root: ${path.relative(repoRoot, frontendDomainRoot)}`);
}

const backendTopLevelDirs = listDirectoryNames(backendRoot);
const backendBusinessDomains = backendTopLevelDirs.filter((entry) => !allowedBackendInfrastructureDomains.includes(entry));
const frontendDomains = listDirectoryNames(frontendDomainRoot);

compareSets("backend business domain", expectedBusinessDomains, backendBusinessDomains);
compareSets("frontend domain", expectedBusinessDomains, frontendDomains);

for (const infrastructureDomain of allowedBackendInfrastructureDomains) {
  if (!backendTopLevelDirs.includes(infrastructureDomain)) {
    fail(`Backend infrastructure domain missing: ${infrastructureDomain}`);
  }
}

for (const domain of expectedBusinessDomains) {
  const backendDomainPath = path.join(backendRoot, domain);
  const frontendDomainPath = path.join(frontendDomainRoot, domain);

  if (listFilesByExtension(backendDomainPath, ".scala").length === 0) {
    fail(`Backend domain has no Scala source files: ${domain}`);
  }
  if (listFilesByExtension(frontendDomainPath, ".ts").length + listFilesByExtension(frontendDomainPath, ".tsx").length === 0) {
    fail(`Frontend domain has no TypeScript source files: ${domain}`);
  }
}

const frontendTopLevelDirs = listDirectoryNames(frontendRoot);
for (const alias of forbiddenFrontendBusinessAliases) {
  if (frontendTopLevelDirs.includes(alias) || frontendDomains.includes(alias)) {
    fail(`Forbidden frontend pseudo-domain still exists: ${alias}`);
  }
}

if (failures.length > 0) {
  console.error("Frontend/backend domain alignment audit failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Frontend/backend domain alignment audit passed.");
