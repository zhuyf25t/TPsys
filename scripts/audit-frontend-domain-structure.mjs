import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = process.cwd();
const failures = [];

const expectedServices = ["battle", "bots", "forum", "governance", "identity", "mail", "replay", "social"];
const expectedRootDirs = ["api", "app", "assets", "components", "hooks", "lib", "objects", "pages", "runtime", "shared"];
const expectedPages = [
  "battle",
  "contribution",
  "discussion",
  "discussion-detail",
  "home",
  "loadout",
  "mails",
  "profile",
  "rating",
  "replay",
  "replay-detail"
];

function fail(message) {
  failures.push(message);
}

function absolute(target) {
  return path.join(repoRoot, target);
}

function exists(target) {
  return fs.existsSync(absolute(target));
}

function isDirectory(target) {
  try {
    return fs.statSync(absolute(target)).isDirectory();
  } catch {
    return false;
  }
}

function listDirectoryNames(target) {
  const fullPath = absolute(target);
  if (!fs.existsSync(fullPath)) {
    return [];
  }

  return fs
    .readdirSync(fullPath, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
}

function listCodeFiles(target) {
  const fullPath = absolute(target);
  if (!fs.existsSync(fullPath)) {
    return [];
  }

  const results = [];
  const stack = [fullPath];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const nestedPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(nestedPath);
      } else if (/\.tsx?$/.test(entry.name)) {
        results.push(nestedPath);
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
    fail(`Forbidden path still exists: ${target}`);
  }
}

function assertCodeFiles(target) {
  if (listCodeFiles(target).length === 0) {
    fail(`Expected code files under: ${target}`);
  }
}

function assertFileDoesNotMatch(target, pattern, label) {
  if (!exists(target)) {
    return;
  }

  const content = fs.readFileSync(absolute(target), "utf8");
  if (pattern.test(content)) {
    fail(`${target} must not contain ${label}`);
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
    /domains\//,
    /domains\\/,
    /domain\/types/,
    /contracts\/battle/,
    /frontend\/src\/(?:features|domains|game|domain|contracts|scenes|ui)\//
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

function assertNoPageApiImports() {
  for (const file of listCodeFiles("frontend/src/pages")) {
    const content = fs.readFileSync(file, "utf8");
    if (/from\s+["'][^"']*\/api\//.test(content)) {
      fail(`Page file must not import api modules directly: ${path.relative(repoRoot, file)}`);
    }
  }
}

function assertNoDynamicImportsInFrontend() {
  for (const file of listCodeFiles("frontend/src")) {
    const content = fs.readFileSync(file, "utf8");
    if (/\bimport\s*\(/.test(content)) {
      fail(`Dynamic import is not allowed in frontend source: ${path.relative(repoRoot, file)}`);
    }
  }
}

compareSets("frontend root directory", expectedRootDirs, listDirectoryNames("frontend/src"));
assertPath("frontend/src/main.tsx");
assertPath("frontend/src/vite-env.d.ts");

for (const legacyRoot of ["domains", "features", "game", "domain", "contracts", "scenes", "ui"]) {
  assertNoPath(`frontend/src/${legacyRoot}`);
}

compareSets("frontend api service", expectedServices, listDirectoryNames("frontend/src/api"));
compareSets("frontend object service", expectedServices, listDirectoryNames("frontend/src/objects"));
for (const service of expectedServices) {
  assertCodeFiles(`frontend/src/api/${service}`);
  assertDir(`frontend/src/objects/${service}`);
}
assertDir("frontend/src/objects/battle/contracts");
assertPath("frontend/src/objects/battle/types.ts");
assertPath("frontend/src/objects/battle/battleRules.ts");
assertPath("frontend/src/objects/identity/identityHandlePolicy.ts");
assertPath("frontend/src/objects/replay/replayTypes.ts");

assertPath("frontend/src/app/App.tsx");
assertPath("frontend/src/app/routes.tsx");
assertDir("frontend/src/app/providers");
assertDir("frontend/src/app/storage");
assertPath("frontend/src/app/tailwind.css");

for (const sharedDir of ["api", "objects", "ui", "hooks", "lib", "storage", "types"]) {
  assertDir(`frontend/src/shared/${sharedDir}`);
}
assertPath("frontend/src/shared/api/apiUrl.ts");
assertPath("frontend/src/shared/api/httpClient.ts");

compareSets("frontend page", expectedPages, listDirectoryNames("frontend/src/pages"));
assertPath("frontend/src/pages/home/HomePage.tsx");
assertPath("frontend/src/pages/loadout/LoadoutPage.tsx");
assertPath("frontend/src/pages/battle/BattlePage.tsx");
assertDir("frontend/src/pages/battle/game-screen");
assertDir("frontend/src/pages/battle/non-game");
assertPath("frontend/src/pages/battle/game-screen/BattleGameScreen.tsx");
assertPath("frontend/src/pages/battle/non-game/BattleEntryBlockedOverlay.tsx");
assertPath("frontend/src/pages/battle/non-game/BattleSettlementOverlay.tsx");
assertPath("frontend/src/pages/battle/non-game/MatchingOverlay.tsx");
assertPath("frontend/src/pages/battle/non-game/battleDrawerPresenter.ts");

assertDir("frontend/src/components");
assertPath("frontend/src/components/auth/AuthOverlay.tsx");
assertPath("frontend/src/components/auth/AuthSessionBootstrap.tsx");
assertPath("frontend/src/components/battle/BattleChrome.tsx");
assertPath("frontend/src/components/contribution/ContributionPageView.tsx");
assertPath("frontend/src/components/discussion/DiscussionPageView.tsx");
assertPath("frontend/src/components/discussion-detail/DiscussionDetailPageView.tsx");
assertPath("frontend/src/components/friend-requests/friendRequestPreviewPresenter.ts");
assertPath("frontend/src/components/home/HomePageView.tsx");
assertPath("frontend/src/components/loadout/LoadoutPageView.tsx");
assertPath("frontend/src/components/mails/MailsPageView.tsx");
assertPath("frontend/src/components/profile/ProfilePageView.tsx");
assertPath("frontend/src/components/rating/RatingPageView.tsx");
assertPath("frontend/src/components/replay-detail/ReplayDetailPageView.tsx");
assertPath("frontend/src/components/replay/ReplayPageView.tsx");
assertPath("frontend/src/components/replay/ReplayViewer.tsx");
assertPath("frontend/src/components/user-action-dot/UserActionDot.tsx");

assertDir("frontend/src/hooks/battle-page");
assertPath("frontend/src/hooks/battle-page/useBattlePageRuntime.ts");
assertPath("frontend/src/hooks/battle-page/useBattlePageData.ts");
assertPath("frontend/src/hooks/battle-page/useBattlePageTimers.ts");
assertCodeFiles("frontend/src/hooks/battle-page");
assertPath("frontend/src/hooks/contribution-page/useContributionPage.ts");
assertPath("frontend/src/hooks/discussion-detail-page/useDiscussionDetailPage.ts");
assertPath("frontend/src/hooks/discussion-page/useDiscussionPage.ts");
assertPath("frontend/src/hooks/home-page/useHomePage.ts");
assertPath("frontend/src/hooks/loadout-page/useLoadoutPage.ts");
assertPath("frontend/src/hooks/mails-page/useMailsPage.ts");
assertPath("frontend/src/hooks/profile-page/useProfilePage.ts");
assertPath("frontend/src/hooks/rating-page/useRatingPage.ts");
assertPath("frontend/src/hooks/replay-detail-page/useReplayDetailPage.ts");
assertPath("frontend/src/hooks/replay-page/useReplayPage.ts");
assertFileDoesNotMatch("frontend/src/pages/contribution/ContributionPage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertFileDoesNotMatch("frontend/src/pages/discussion-detail/DiscussionDetailPage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertFileDoesNotMatch("frontend/src/pages/discussion/DiscussionPage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertFileDoesNotMatch("frontend/src/pages/home/HomePage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertFileDoesNotMatch("frontend/src/pages/loadout/LoadoutPage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertFileDoesNotMatch("frontend/src/pages/mails/MailsPage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertFileDoesNotMatch("frontend/src/pages/profile/ProfilePage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertFileDoesNotMatch("frontend/src/pages/rating/RatingPage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertFileDoesNotMatch("frontend/src/pages/replay-detail/ReplayDetailPage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertFileDoesNotMatch("frontend/src/pages/replay/ReplayPage.tsx", /\.\.\/\.\.\/api\//, "direct api imports");
assertNoPageApiImports();
assertNoDynamicImportsInFrontend();

assertDir("frontend/src/lib");
assertPath("frontend/src/lib/localDiscussionStore.ts");
assertPath("frontend/src/lib/localReplayStore.ts");
assertCodeFiles("frontend/src/lib");

for (const runtimeDir of ["authoritative", "local", "matchmaking"]) {
  assertDir(`frontend/src/runtime/battle/${runtimeDir}`);
  assertCodeFiles(`frontend/src/runtime/battle/${runtimeDir}`);
}
assertPath("frontend/src/runtime/battle/game/renderer/createBattleRuntime.ts");
assertPath("frontend/src/runtime/battle/game/scenes/GameScene.ts");
assertPath("frontend/src/runtime/battle/game/ui/Hud.ts");
assertDir("frontend/src/runtime/bots");
assertCodeFiles("frontend/src/runtime/bots");

assertNoLegacyImports();

if (failures.length > 0) {
  console.error("Frontend domain structure audit failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Frontend domain structure audit passed.");
