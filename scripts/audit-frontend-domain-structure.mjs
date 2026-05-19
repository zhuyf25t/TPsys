import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = process.cwd();
const frontendSrc = path.join(repoRoot, "frontend", "src");
const failures = [];

const expectedDomains = ["battle", "bots", "forum", "governance", "identity", "mail", "replay", "social"];
const requiredDomainDirs = ["api", "objects", "pages", "components", "hooks", "lib"];
const allowedDomainExtras = {
  battle: ["contracts", "game", "runtime"],
  bots: ["runtime"],
  forum: [],
  governance: [],
  identity: [],
  mail: [],
  replay: [],
  social: []
};

const expectedPages = {
  battle: ["battle", "loadout"],
  bots: [],
  forum: ["discussion-detail", "discussion-list"],
  governance: ["admin-notifications", "contribution", "rating"],
  identity: ["profile", "session"],
  mail: ["inbox"],
  replay: ["replay-detail", "replay-list"],
  social: []
};

function fail(message) {
  failures.push(message);
}

function exists(target) {
  return fs.existsSync(path.join(repoRoot, target));
}

function isDirectory(target) {
  try {
    return fs.statSync(path.join(repoRoot, target)).isDirectory();
  } catch {
    return false;
  }
}

function listDirectoryNames(target) {
  const absolute = path.join(repoRoot, target);
  if (!fs.existsSync(absolute)) {
    return [];
  }

  return fs
    .readdirSync(absolute, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
}

function listCodeFiles(target) {
  const absolute = path.join(repoRoot, target);
  if (!fs.existsSync(absolute)) {
    return [];
  }

  const results = [];
  const stack = [absolute];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (/\.tsx?$/.test(entry.name)) {
        results.push(fullPath);
      }
    }
  }
  return results;
}

function assertPath(target) {
  if (!exists(target)) {
    fail(`Missing required path: ${target}`);
  }
}

function assertDir(target) {
  if (!isDirectory(target)) {
    fail(`Missing required directory: ${target}`);
  }
}

function assertNoPath(target) {
  if (exists(target)) {
    fail(`Forbidden legacy path still exists: ${target}`);
  }
}

function assertCodeFiles(target) {
  const files = listCodeFiles(target);
  if (files.length === 0) {
    fail(`Expected code files under: ${target}`);
  }
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

function assertNoLegacyImports() {
  const codeFiles = listCodeFiles("frontend/src");
  const legacyPatterns = [
    /(?:^|["'])\.\.?\/.*features\//,
    /features\//,
    /domain\/types/,
    /contracts\/battle/,
    /frontend\/src\/(?:features|pages|game|domain|contracts|scenes|ui)\//
  ];

  for (const file of codeFiles) {
    const content = fs.readFileSync(file, "utf8");
    for (const pattern of legacyPatterns) {
      if (pattern.test(content)) {
        fail(`Legacy import/path pattern ${pattern} found in ${path.relative(repoRoot, file)}`);
        break;
      }
    }
  }
}

assertDir("frontend/src/app");
assertDir("frontend/src/assets");
assertDir("frontend/src/domains");
assertDir("frontend/src/shared");
assertPath("frontend/src/main.tsx");
assertPath("frontend/src/vite-env.d.ts");

for (const legacyRoot of ["features", "pages", "game", "domain", "contracts", "scenes", "ui"]) {
  assertNoPath(`frontend/src/${legacyRoot}`);
}

assertPath("frontend/src/app/App.tsx");
assertPath("frontend/src/app/routes.tsx");
assertDir("frontend/src/app/providers");
assertDir("frontend/src/app/storage");
assertDir("frontend/src/app/styles");
for (const styleFile of ["base.css", "app-shell.css", "domain-overrides.css"]) {
  assertPath(`frontend/src/app/styles/${styleFile}`);
}

for (const sharedDir of ["api", "objects", "ui", "hooks", "lib", "storage", "types"]) {
  assertDir(`frontend/src/shared/${sharedDir}`);
}
assertPath("frontend/src/shared/api/apiUrl.ts");
assertPath("frontend/src/shared/api/httpClient.ts");

compareSets("frontend domain", expectedDomains, listDirectoryNames("frontend/src/domains"));

for (const domain of expectedDomains) {
  const domainRoot = `frontend/src/domains/${domain}`;
  assertPath(`${domainRoot}/index.ts`);

  const allowedChildren = [...requiredDomainDirs, ...allowedDomainExtras[domain]].sort();
  compareSets(`${domain} top-level directory`, allowedChildren, listDirectoryNames(domainRoot));

  for (const requiredDir of requiredDomainDirs) {
    assertDir(`${domainRoot}/${requiredDir}`);
  }

  for (const page of expectedPages[domain]) {
    assertDir(`${domainRoot}/pages/${page}`);
  }
}

for (const runtimeDir of ["authoritative", "local", "matchmaking"]) {
  assertDir(`frontend/src/domains/battle/runtime/${runtimeDir}`);
  assertCodeFiles(`frontend/src/domains/battle/runtime/${runtimeDir}`);
}
for (const gameDir of ["assets", "maps", "weapons", "skills", "renderer"]) {
  assertDir(`frontend/src/domains/battle/game/${gameDir}`);
  assertCodeFiles(`frontend/src/domains/battle/game/${gameDir}`);
}
for (const runtimeDir of ["controller", "registry", "strategies"]) {
  assertDir(`frontend/src/domains/bots/runtime/${runtimeDir}`);
  assertCodeFiles(`frontend/src/domains/bots/runtime/${runtimeDir}`);
}

assertNoLegacyImports();

if (failures.length > 0) {
  console.error("Frontend domain structure audit failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Frontend domain structure audit passed.");
