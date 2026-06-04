import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = process.cwd();
const backendServicesRoot = path.join(repoRoot, "backend", "src", "main", "scala", "services");
const frontendApiRoot = path.join(repoRoot, "frontend", "src", "apis");
const frontendObjectRoot = path.join(repoRoot, "frontend", "src", "objects");
const frontendRoot = path.join(repoRoot, "frontend", "src");
const failures = [];

const expectedBusinessDomains = ["battle", "bots", "forum", "governance", "identity", "mail", "replay", "social"];
const expectedBattleApiClients = ["queue", "results", "session"];
const forbiddenFrontendBusinessAliases = [
  "auth",
  "contribution",
  "domains",
  "features",
  "home",
  "hooks",
  "lib",
  "loadout",
  "mails",
  "profile",
  "rating",
  "shared"
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

function assertDirectory(target, label) {
  if (!isDirectory(target)) {
    fail(`Missing ${label}: ${path.relative(repoRoot, target)}`);
  }
}

function assertHasFiles(target, extensions, label) {
  const count = extensions.reduce((total, extension) => total + listFilesByExtension(target, extension).length, 0);
  if (count === 0) {
    fail(`${label} has no source files: ${path.relative(repoRoot, target)}`);
  }
}

assertDirectory(backendServicesRoot, "backend services root");
assertDirectory(frontendApiRoot, "frontend API root");
assertDirectory(frontendObjectRoot, "frontend object root");

const backendServices = listDirectoryNames(backendServicesRoot);
const frontendApiServices = listDirectoryNames(frontendApiRoot);
const frontendObjectServices = listDirectoryNames(frontendObjectRoot);

compareSets("backend service", expectedBusinessDomains, backendServices);
compareSets("frontend API service", expectedBusinessDomains, frontendApiServices);
compareSets("frontend object service", expectedBusinessDomains, frontendObjectServices);

for (const domain of expectedBusinessDomains) {
  const backendDomainPath = path.join(backendServicesRoot, domain);
  const frontendApiPath = path.join(frontendApiRoot, domain);
  const frontendObjectPath = path.join(frontendObjectRoot, domain);

  assertHasFiles(backendDomainPath, [".scala"], `Backend service ${domain}`);
  assertHasFiles(frontendApiPath, [".ts", ".tsx"], `Frontend API service ${domain}`);
  assertHasFiles(frontendObjectPath, [".ts", ".tsx"], `Frontend object service ${domain}`);
}

const frontendTopLevelDirs = listDirectoryNames(frontendRoot);
for (const alias of forbiddenFrontendBusinessAliases) {
  if (frontendTopLevelDirs.includes(alias)) {
    fail(`Forbidden frontend pseudo-domain still exists: ${alias}`);
  }
}

const backendBattleMicroservicesRoot = path.join(backendServicesRoot, "battle", "microservices");
const frontendBattleObjectMicroservicesRoot = path.join(frontendObjectRoot, "battle", "microservices");
const frontendBattleApiMicroservicesRoot = path.join(frontendApiRoot, "battle", "microservices");

const backendBattleMicroservices = listDirectoryNames(backendBattleMicroservicesRoot);
compareSets(
  "battle object microservice",
  backendBattleMicroservices,
  listDirectoryNames(frontendBattleObjectMicroservicesRoot)
);
for (const microservice of backendBattleMicroservices) {
  assertHasFiles(
    path.join(frontendBattleObjectMicroservicesRoot, microservice),
    [".ts", ".tsx"],
    `Frontend battle object microservice ${microservice}`
  );
}

compareSets(
  "battle API client microservice",
  expectedBattleApiClients,
  listDirectoryNames(frontendBattleApiMicroservicesRoot)
);
for (const microservice of expectedBattleApiClients) {
  assertHasFiles(
    path.join(frontendBattleApiMicroservicesRoot, microservice),
    [".ts", ".tsx"],
    `Frontend battle API client microservice ${microservice}`
  );
}

if (failures.length > 0) {
  console.error("Frontend/backend domain alignment audit failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Frontend/backend domain alignment audit passed.");
