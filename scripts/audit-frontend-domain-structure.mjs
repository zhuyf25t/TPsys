import fs from "node:fs";
import path from "node:path";
import process from "node:process";

const repoRoot = process.cwd();
const failures = [];

const expectedServices = ["battle", "bots", "forum", "governance", "identity", "mail", "replay", "social"];
const expectedRootDirs = ["apis", "app", "assets", "components", "objects", "pages", "runtime", "system"];
const expectedPages = [
  "battle",
  "contribution",
  "discussion",
  "discussion-detail",
  "friend-requests",
  "home",
  "loadout",
  "mails",
  "profile",
  "rating",
  "replay",
  "replay-detail",
  "shared"
];
const expectedBattleApiClients = ["queue", "results", "session"];

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

function listBackendBattleMicroserviceNames() {
  return listDirectoryNames("backend/src/main/scala/services/battle/microservices");
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

function assertNoFlatBattleCompatImports() {
  const codeFiles = listCodeFiles("frontend/src");
  const legacyBattlePatterns = [
    /objects\/battle\/types/,
    /objects\/battle\/(?:apiMessages|commands|events|results|snapshots|views)/,
    /apis\/battle\/(?:battleApiMessageClient|battleResultsApi)/
  ];

  for (const file of codeFiles) {
    const content = fs.readFileSync(file, "utf8");
    for (const pattern of legacyBattlePatterns) {
      if (pattern.test(content)) {
        fail(`Legacy flat battle import/path pattern ${pattern} found in ${path.relative(repoRoot, file)}`);
        break;
      }
    }
  }
}

function assertPageHasSampleShape(pageName, hookName, viewName) {
  const pageRoot = `frontend/src/pages/${pageName}`;
  assertPath(`${pageRoot}/index.tsx`);
  assertDir(`${pageRoot}/components`);
  assertDir(`${pageRoot}/hooks`);
  assertPath(`${pageRoot}/hooks/${hookName}.ts`);
  assertPath(`${pageRoot}/components/${viewName}/index.tsx`);
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

for (const legacyRoot of ["api", "domains", "features", "game", "domain", "contracts", "hooks", "lib", "scenes", "shared", "ui"]) {
  assertNoPath(`frontend/src/${legacyRoot}`);
}
for (const legacyBattlePath of [
  "frontend/src/objects/battle/types.ts",
  "frontend/src/objects/battle/battleRules.ts",
  "frontend/src/objects/battle/contracts",
  "frontend/src/objects/battle/apiMessages.ts",
  "frontend/src/objects/battle/commands.ts",
  "frontend/src/objects/battle/events.ts",
  "frontend/src/objects/battle/results.ts",
  "frontend/src/objects/battle/snapshots.ts",
  "frontend/src/objects/battle/views.ts"
]) {
  assertNoPath(legacyBattlePath);
}

compareSets("frontend api service", expectedServices, listDirectoryNames("frontend/src/apis"));
compareSets("frontend object service", expectedServices, listDirectoryNames("frontend/src/objects"));
for (const service of expectedServices) {
  assertCodeFiles(`frontend/src/apis/${service}`);
  assertDir(`frontend/src/objects/${service}`);
}
compareSets(
  "frontend battle object microservice",
  listBackendBattleMicroserviceNames(),
  listDirectoryNames("frontend/src/objects/battle/microservices")
);
for (const microservice of listBackendBattleMicroserviceNames()) {
  assertCodeFiles(`frontend/src/objects/battle/microservices/${microservice}`);
}
compareSets("frontend battle API client microservice", expectedBattleApiClients, listDirectoryNames("frontend/src/apis/battle/microservices"));
for (const microservice of expectedBattleApiClients) {
  assertCodeFiles(`frontend/src/apis/battle/microservices/${microservice}`);
}
assertPath("frontend/src/apis/battle/BattleApiMessageTransport.ts");
assertPath("frontend/src/objects/battle/objects/core/BattleCoreScalars.ts");
assertPath("frontend/src/objects/battle/objects/core/BattleCoreRules.ts");
assertPath("frontend/src/objects/battle/objects/core/BattleModeDisplayLabels.ts");
assertPath("frontend/src/objects/identity/identityHandlePolicy.ts");
assertPath("frontend/src/objects/replay/replayTypes.ts");
assertPath("frontend/src/objects/battle/microservices/session/objects/state/BattleInitialParticipants.ts");

assertPath("frontend/src/app/App.tsx");
assertPath("frontend/src/app/routes.tsx");
assertDir("frontend/src/app/providers");
assertDir("frontend/src/app/storage");
assertPath("frontend/src/app/tailwind.css");

assertPath("frontend/src/system/api/apiUrl.ts");
assertPath("frontend/src/system/api/httpClient.ts");

compareSets("frontend page", expectedPages, listDirectoryNames("frontend/src/pages"));
assertPath("frontend/src/pages/battle/index.tsx");
for (const battlePageDir of ["components", "functions", "hooks", "input", "objects", "stores"]) {
  assertDir(`frontend/src/pages/battle/${battlePageDir}`);
  assertCodeFiles(`frontend/src/pages/battle/${battlePageDir}`);
}
assertPath("frontend/src/pages/battle/components/BattleChrome.tsx");
assertPath("frontend/src/pages/battle/components/BattleDrawerLayer.tsx");
assertPath("frontend/src/pages/battle/components/BattleGameScreen.tsx");
assertPath("frontend/src/pages/battle/components/BattleMatchingLayer/index.tsx");
assertPath("frontend/src/pages/battle/components/BattleMatchingLayer/components/BattleEntryBlockedOverlay.tsx");
assertPath("frontend/src/pages/battle/components/BattleMatchingLayer/components/MatchingOverlay/index.tsx");
assertDir("frontend/src/pages/battle/components/BattleMatchingLayer/components/MatchingOverlay/components");
assertDir("frontend/src/pages/battle/components/BattleMatchingLayer/components/MatchingOverlay/functions");
assertDir("frontend/src/pages/battle/components/BattleMatchingLayer/components/MatchingOverlay/objects");
assertPath("frontend/src/pages/battle/components/BattleSettlementLayer/index.tsx");
assertPath("frontend/src/pages/battle/components/BattleSettlementLayer/components/BattleSettlementOverlay.tsx");
assertPath("frontend/src/pages/battle/hooks/useBattlePageRuntime.ts");
assertPath("frontend/src/pages/battle/hooks/useBattlePageData.ts");
assertPath("frontend/src/pages/battle/hooks/useBattlePageTimers.ts");

assertPageHasSampleShape("contribution", "useContributionPage", "ContributionPageView");
assertPageHasSampleShape("discussion", "useDiscussionPage", "DiscussionPageView");
assertPageHasSampleShape("discussion-detail", "useDiscussionDetailPage", "DiscussionDetailPageView");
assertPageHasSampleShape("home", "useHomePage", "HomePageView");
assertPageHasSampleShape("loadout", "useLoadoutPage", "LoadoutPageView");
assertPageHasSampleShape("mails", "useMailsPage", "MailsPageView");
assertPageHasSampleShape("profile", "useProfilePage", "ProfilePageView");
assertPageHasSampleShape("rating", "useRatingPage", "RatingPageView");
assertPageHasSampleShape("replay", "useReplayPage", "ReplayPageView");
assertDir("frontend/src/pages/replay/components/ReplayViewer");
assertPageHasSampleShape("replay-detail", "useReplayDetailPage", "ReplayDetailPageView");
assertPath("frontend/src/pages/friend-requests/components/friendRequestPreviewPresenter.ts");
assertPath("frontend/src/pages/shared/components/auth/AuthOverlay.tsx");
assertPath("frontend/src/pages/shared/components/user-action-dot/UserActionDot.tsx");
assertPath("frontend/src/pages/shared/hooks/useLobbyData.ts");

assertDir("frontend/src/components");
assertPath("frontend/src/components/ui/AppErrorBoundary.tsx");
assertPath("frontend/src/components/ui/ShellLayout.tsx");
assertNoDynamicImportsInFrontend();

for (const runtimeDir of ["game", "loadout", "local", "matchmaking", "microservices"]) {
  assertDir(`frontend/src/runtime/battle/${runtimeDir}`);
  assertCodeFiles(`frontend/src/runtime/battle/${runtimeDir}`);
}
assertNoPath("frontend/src/runtime/battle/authoritative");
assertNoPath("frontend/src/runtime/battle/battleModeDisplayLabels.ts");
assertPath("frontend/src/runtime/battle/game/renderer/createBattleRuntime.ts");
assertDir("frontend/src/runtime/battle/game/renderer/assets/objects");
assertPath("frontend/src/runtime/battle/game/renderer/assets/objects/BattleProjectileRasterAtlasObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/assets/objects/BattleWeaponRasterAtlasObjects.ts");
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/assets/BattleProjectileRasterAtlas.ts",
  /\bexport\s+interface\s+ProjectileTextureRef\b/,
  "projectile raster atlas object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/assets/BattleWeaponRasterAtlas.ts",
  /\bexport\s+interface\s+WeaponTextureRef\b/,
  "weapon raster atlas object declarations"
);
assertDir("frontend/src/runtime/battle/game/renderer/camera/functions");
assertDir("frontend/src/runtime/battle/game/renderer/camera/objects");
assertPath("frontend/src/runtime/battle/game/renderer/camera/functions/BattleCameraRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/camera/objects/BattleCameraObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/heroWorldViewFactory.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/heroWorldViewRemoteDisplaySync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/heroWorldViewSync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/heroWorldViewVisibilitySync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/heroWorldViewsSync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/remoteHeroDisplayStateSync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/remoteHeroInterpolationBufferSync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/projectileDisplayPositionReader.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/projectileDisplayStateSync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/projectileViewLifecycle.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/projectileViewSync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/preparedSkillIndicatorViewVisualSync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/worldViewDisplayPositionReader.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/worldViewIndicatorSync.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/worldViewStateFactory.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/slowFieldViewLifecycle.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/slowFieldViewSync.ts");
assertDir("frontend/src/runtime/battle/game/renderer/arena/objects");
assertPath("frontend/src/runtime/battle/game/renderer/arena/objects/ArenaBuilderObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/arena/objects/ArenaBackgroundObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/arena/objects/ArenaDecorationObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/arena/objects/OcclusionAlphaObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/arena/objects/ObstacleSkinObjects.ts");
assertDir("frontend/src/runtime/battle/game/renderer/arena/functions");
assertPath("frontend/src/runtime/battle/game/renderer/arena/functions/ArenaBackgroundRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/arena/functions/ArenaBuilderRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/arena/functions/ArenaDecorationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/arena/functions/OcclusionAlphaRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/arena/functions/ObstacleSkinRules.ts");
assertDir("frontend/src/runtime/battle/game/renderer/entities/objects");
assertDir("frontend/src/runtime/battle/game/renderer/entities/functions");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/HeroReadabilityViewRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/BattleGameSceneHeroActorRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/HeroWorldViewFrameLayoutRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/HeroWorldViewVisibilityRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/HeroPresentationScaleRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/HeroWeaponOverlayRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/LocalHeroMotionStreakRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/PickupViewPresentationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/PickupViewSyncRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/PreparedSkillIndicatorRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/ProjectileInterpolationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/ProjectilePresentationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/RemoteHeroInterpolationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/SlowFieldPresentationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/WorldViewFactoryRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/functions/WorldViewStateFactoryRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/BattleGameSceneHeroActorObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/BattleGameSceneHeroDisplacementObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/BattleLocalHeroDisplayObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/HeroWorldViewFactoryObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/HeroWorldViewRemoteDisplayObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/HeroWorldViewSyncObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/HeroWorldViewsSyncObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/HeroReadabilityViewObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/HeroWeaponOverlayObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/LocalHeroMotionStreakObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/PickupViewPresentationObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/PickupViewSyncObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/PreparedSkillIndicatorObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/ProjectileViewObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/RemoteHeroInterpolationObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/SlowFieldViewObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/WorldViewFactoryObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/entities/objects/WorldViewStateFactoryObjects.ts");
assertDir("frontend/src/runtime/battle/game/renderer/runtime");
assertDir("frontend/src/runtime/battle/game/renderer/runtime/functions");
assertDir("frontend/src/runtime/battle/game/renderer/runtime/objects");
assertPath("frontend/src/runtime/battle/game/renderer/runtime/functions/BattlePhaserGameViewportRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/runtime/objects/BattlePhaserGameObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/runtime/objects/BattleRuntimeBootSnapshotObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/runtime/objects/BattleRuntimeDomObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/runtime/objects/BattleRuntimeFactoryObjects.ts");
assertDir("frontend/src/runtime/battle/game/renderer/authoritative");
assertDir("frontend/src/runtime/battle/game/renderer/authoritative/functions");
assertDir("frontend/src/runtime/battle/game/renderer/authoritative/objects");
assertPath("frontend/src/runtime/battle/game/renderer/authoritative/functions/BattleLocalAuthoritativeHeroCorrectionRuntimeRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/authoritative/objects/BattleAuthoritativeFrameSnapshotApplierObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/authoritative/objects/BattleAuthoritativeFrameSceneBridgeObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/authoritative/objects/BattleAuthoritativeLocalHeroMotionObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/authoritative/objects/BattleAuthoritativeLocalHeroReplayObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/authoritative/objects/BattleAuthoritativeRenderPipelineObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/authoritative/objects/BattleLocalAuthoritativeHeroCorrectionObjects.ts");
assertDir("frontend/src/runtime/battle/game/renderer/diagnostics/functions");
assertDir("frontend/src/runtime/battle/game/renderer/diagnostics/objects");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/functions/AuthoritativeLocalHeroReplayDiagnosticsRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/functions/LocalFeedbackDiagnosticsRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/functions/LocalHeroCorrectionDiagnosticsRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/functions/RemoteViewDiagnosticsRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/functions/VisionDiagnosticsRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/objects/AuthoritativeLocalHeroReplayDiagnosticsObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/objects/LocalFeedbackDiagnosticsObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/objects/LocalHeroCorrectionDiagnosticsObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/objects/RemoteViewDiagnosticsObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/diagnostics/objects/VisionDiagnosticsObjects.ts");
assertDir("frontend/src/runtime/battle/game/renderer/effects/functions");
assertDir("frontend/src/runtime/battle/game/renderer/effects/objects");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/CombatProjectileEffectPresentationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/CombatProjectileEffectSceneBridgeRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/FloatingTextVfxRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/HeroAndPickupFeedbackPresentationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/MuzzleAndHitVfxRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/PlayerAbilitySceneBridgeRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/PlayerMotionTweenRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/ProjectileFrameSceneBridgeRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/ProjectileFeedbackEffectPlanPresentationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/ProjectileTerminalDiagnosticsRecorderRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/ProjectileTracerVfxRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/RemoteProjectileBirthFeedbackPresentationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/SceneVfxRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/SkillFeedbackVfxRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/TransientVfxLifecycleRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/WeaponActionSceneBridgeRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/functions/WeaponActionPlanPresentationRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/BattleFeedbackSceneBridgeObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/CombatProjectileEffectPresenterObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/CombatProjectileEffectSceneBridgeObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/FloatingTextVfxObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/GameSceneBattleFeedbackBridgeFactoryObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/GameSceneSharedAuthoritativeLocalFeedbackBridgeFactoryObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/HeroAndPickupFeedbackPresenterObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/MuzzleAndHitVfxObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/PlayerAbilitySceneBridgeObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/PlayerMotionTweenObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/ProjectileFrameSceneBridgeObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/ProjectileFeedbackEffectPlanPresenterObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/ProjectileTerminalDiagnosticsRecorderObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/ProjectileTerminalVfxPresenterObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/ProjectileTracerVfxObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/RemoteProjectileBirthFeedbackPresenterObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/SceneVfxObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/SharedAuthoritativeLocalFeedbackSceneBridgeObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/SkillFeedbackVfxObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/TransientVfxLifecycleObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/WeaponActionSceneBridgeObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/effects/objects/WeaponActionPlanPresenterObjects.ts");
assertDir("frontend/src/runtime/battle/game/renderer/input/functions");
assertDir("frontend/src/runtime/battle/game/renderer/input/objects");
assertPath("frontend/src/runtime/battle/game/renderer/input/functions/BattleGameSceneInputBridgeRules.ts");
assertPath("frontend/src/runtime/battle/game/renderer/input/objects/BattleGameSceneInputBridgeObjects.ts");
assertDir("frontend/src/runtime/battle/game/renderer/presentation/objects");
assertPath("frontend/src/runtime/battle/game/renderer/presentation/objects/BattleGameSceneHudPresentationObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/presentation/objects/BattleGameSceneOcclusionPresentationObjects.ts");
assertPath("frontend/src/runtime/battle/game/renderer/presentation/objects/BattleGameSceneWorldViewPresentationObjects.ts");
assertPath("frontend/src/runtime/battle/game/scenes/GameScene.ts");
assertDir("frontend/src/runtime/battle/game/scenes/functions");
assertDir("frontend/src/runtime/battle/game/scenes/objects");
assertDir("frontend/src/runtime/battle/game/functions");
assertDir("frontend/src/runtime/battle/game/objects");
assertPath("frontend/src/runtime/battle/game/objects/BattleGameConstants.ts");
assertPath("frontend/src/runtime/battle/game/objects/BattleHeroVisualCatalog.ts");
assertNoPath("frontend/src/runtime/battle/game/constants.ts");
assertNoPath("frontend/src/runtime/battle/game/projectileBirth.ts");
assertNoPath("frontend/src/runtime/battle/game/functions/BattleProjectileBirthPosition.ts");
assertNoPath("frontend/src/runtime/battle/game/spawn.ts");
assertNoPath("frontend/src/runtime/battle/game/assets");
assertNoPath("frontend/src/runtime/battle/game/maps");
assertNoPath("frontend/src/runtime/battle/game/weapons");
assertNoPath("frontend/src/runtime/battle/game/skills");
assertNoPath("frontend/src/runtime/battle/local/geometry/displacementResolver.ts");
assertNoPath("frontend/src/runtime/battle/local/skills/BattleAuthoritativeLocalHeroBlinkPrediction.ts");
assertNoPath("frontend/src/runtime/battle/local/skills/BattleAuthoritativeLocalHeroDashPrediction.ts");
assertNoPath("frontend/src/runtime/battle/local/skills/BattleSharedAuthoritativeTargetValidity.ts");
assertNoPath("frontend/src/runtime/battle/local/skills/skillRuntimeProfiles.ts");
assertNoPath("frontend/src/runtime/battle/local/skills/freezeFieldController.ts");
assertNoPath("frontend/src/runtime/battle/local/pickups/pickupController.ts");
assertNoPath("frontend/src/runtime/battle/local/pickups/pickupSpawnResolver.ts");
assertNoPath("frontend/src/runtime/battle/local/projectiles/projectileFactory.ts");
assertNoPath("frontend/src/runtime/battle/local/projectiles/hitResolver.ts");
assertNoPath("frontend/src/runtime/battle/local/projectiles/projectileController.ts");
assertNoPath("frontend/src/runtime/battle/local/projectiles/damageResolver.ts");
assertNoPath("frontend/src/runtime/battle/local/combat/combatFrameController.ts");
assertNoPath("frontend/src/runtime/battle/local/movement/movementController.ts");
assertNoPath("frontend/src/runtime/battle/local/movement/motionController.ts");
assertNoPath("frontend/src/runtime/battle/local/session/respawnSceneBridge.ts");
assertNoPath("frontend/src/runtime/battle/local/session/respawnController.ts");
assertNoPath("frontend/src/runtime/battle/local/session/battleCompletion.ts");
assertNoPath("frontend/src/runtime/battle/local/session/botOnlyBattleClosure.ts");
assertNoPath("frontend/src/runtime/battle/local/session/battleFinalizationReplay.ts");
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/pickups/pickupLifecycle.ts",
  /\bfindNearby(?:Item)?Pickup\b|\bdistanceBetween\b|\brespawnMs\s*=/,
  "pickup search or respawn rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/hud/battleHudSceneBridge.ts",
  /local\/pickups\/pickupLifecycle/,
  "local pickup lifecycle import"
);
assertPath("frontend/src/runtime/battle/game/renderer/hud/objects/BattleHudSceneBridgeObjects.ts");
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/hud/battleHudSceneBridge.ts",
  /\bexport\s+interface\s+BattleHudSceneBridgeContext\b/,
  "HUD scene bridge object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/presentation/BattleGameSceneWorldViewPresentation.ts",
  /\bexport\s+interface\s+SyncGameSceneWorldViewsInput\b/,
  "world-view presentation object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/presentation/BattleGameSceneOcclusionPresentation.ts",
  /\bexport\s+interface\s+UpdateGameSceneOcclusionInput\b/,
  "occlusion presentation object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/presentation/BattleGameSceneHudPresentation.ts",
  /\bexport\s+interface\s+RenderGameSceneHudInput\b/,
  "HUD presentation object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/input/BattleGameSceneInputBridge.ts",
  /\bexport\s+interface\s+ReadGameScenePlayerCommandInput\b|\bfunction\s+(suppressUnreadyAuthoritativePreparedToggle|isAuthoritativeSkillReady)\b/,
  "game scene input bridge object declarations or pure prepared-skill rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/microservices/world/functions/BattlePickupSpawnPointRules.ts",
  /Math\.random/,
  "implicit random source"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/weapons/weaponActionController.ts",
  /export function getCurrentWeapon\b/,
  "current-weapon inventory rule"
);
assertNoPath("frontend/src/runtime/battle/local/weapons/weaponActionController.ts");
assertNoPath("frontend/src/runtime/battle/local/weapons/weaponRuntimeProfiles.ts");
assertNoPath("frontend/src/runtime/battle/local/weapons/weaponController.ts");
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/weapons/weaponController.ts",
  /\b(requestWeaponSwitch|requestWeaponSwitchToIndex|beginWeaponSwitchTransaction|beginWeaponSwitchIndexTransaction|pruneDepletedDisposableWeapon)\b/,
  "weapon switch rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/session/initialBattleSnapshot.ts",
  /\binterface\s+InitialBattle|\bfunction\s+(applyInitialParticipants|normalizeSeatAssignments|resolvePlayerHeroId)\b/,
  "initial battle participant/session rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/weapons/weaponRuntimeProfiles.ts",
  /export function resolveWeaponAmmoMode\b/,
  "weapon ammo mode rule"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/weapons/weaponRuntimeProfiles.ts",
  /\b(projectileSpawnPlan|WeaponProjectileSpawnPlan)\b/,
  "weapon projectile spawn plan"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/timers/heroWeaponSkillTimers.ts",
  /\b(fireCooldownMs|reloadRemainingMs|overheatRemainingMs|coolRatePerSecond|finishReload)\b/,
  "weapon timer mutation rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/timers/heroWeaponSkillTimers.ts",
  /\bskill\.(cooldownMs|activeMs)\s*=/,
  "skill timer mutation rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/movement/playerMotionAbilityHandler.ts",
  /\b\w+\.(cooldownMs|activeMs)\s*=/,
  "skill activation state mutation rules"
);
assertFileDoesNotMatch(
  "frontend/src/objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions.ts",
  /\bpellets\b/,
  "backend-aligned projectileCount field"
);
assertPath("frontend/src/objects/battle/microservices/abilities/objects/abilities/BattleAbilityRuleDefinitions.ts");
assertPath("frontend/src/runtime/battle/microservices/abilities/functions/BattleSkillStateRules.ts");
assertPath("frontend/src/runtime/battle/microservices/abilities/functions/BattleSkillRuntimeProfiles.ts");
assertPath("frontend/src/runtime/battle/microservices/abilities/functions/BattleSkillFeedbackRules.ts");
assertPath("frontend/src/runtime/battle/microservices/abilities/functions/BattleAuthoritativeSkillPredictionRules.ts");
assertPath("frontend/src/runtime/battle/microservices/abilities/functions/BattleAuthoritativeSkillPredictionTrackerRules.ts");
assertPath("frontend/src/runtime/battle/microservices/abilities/functions/BattleSkillTargetValidityRules.ts");
assertPath("frontend/src/runtime/battle/microservices/abilities/functions/BattleSlowFieldRuntimeRules.ts");
assertPath("frontend/src/runtime/battle/microservices/abilities/functions/BattlePickupRules.ts");
assertPath("frontend/src/runtime/battle/microservices/abilities/functions/BattlePickupFeedbackRules.ts");
assertPath("frontend/src/runtime/battle/microservices/actors/functions/BattlePlayerInputRules.ts");
assertPath("frontend/src/runtime/battle/microservices/actors/functions/BattlePlayerMovementRules.ts");
assertPath("frontend/src/runtime/battle/microservices/actors/functions/BattlePlayerRuntimeRules.ts");
assertPath("frontend/src/runtime/battle/microservices/actors/functions/BattlePlayerRespawnRules.ts");
assertPath("frontend/src/runtime/battle/microservices/actors/functions/BattleHeroFeedbackRules.ts");
assertPath("frontend/src/objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleWeaponInventoryRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleWeaponSwitchRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleWeaponFireDecisionRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleWeaponRuntimeProfiles.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleWeaponActionRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleWeaponFeedbackRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleWeaponTimerRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleCombatDisplacementRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleProjectileFactoryRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleProjectileFeedbackDiagnosticRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleProjectileFeedbackRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleProjectileFeedbackQueueRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleProjectileFeedbackPresentationRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleProjectileTargetingRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleProjectileRuntimeRules.ts");
assertPath("frontend/src/runtime/battle/microservices/combat/functions/BattleProjectileImpactRules.ts");
assertPath("frontend/src/runtime/battle/microservices/runtime/functions/BattleRuntimeFinishRules.ts");
assertPath("frontend/src/runtime/battle/microservices/session/functions/BattleInitialSnapshotRules.ts");
assertPath("frontend/src/runtime/battle/microservices/projections/functions/BattleBotOnlyClosureReplayRules.ts");
assertPath("frontend/src/runtime/battle/microservices/projections/functions/BattleFinalizationReplayRules.ts");
assertPath("frontend/src/runtime/battle/microservices/world/functions/BattleArenaCollision.ts");
assertPath("frontend/src/runtime/battle/microservices/world/functions/BattleMotionRules.ts");
assertPath("frontend/src/runtime/battle/microservices/world/functions/BattleWorldInitialLayout.ts");
assertPath("frontend/src/runtime/battle/microservices/world/functions/BattlePickupSpawnPointRules.ts");
assertPath("frontend/src/runtime/battle/game/ui/Hud.ts");
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/arena/arenaBackgroundPresenter.ts",
  /\b(interface\s+NaturalMapPresentationPalette|function\s+(terrainDepth|naturalMapPaletteForTheme|colorFromHex|createBorderShadow|createBoundaryWarningLine|createBoundaryEnergyLine|createHeroCenterLimitCues|createBoundaryWarningTicks|createOutOfBoundsEdgeBand|createOutOfBoundsRailCues|createMetalPanelSeams|createCentralPanelHierarchy|createArenaLightStrips|createIndustrialCornerShadows|createPatternRect)\b|Number\.parseInt|type\s+BattleMapThemeId|BORDER_(SHADOW|WARNING|DANGER|ENERGY)|OUT_OF_BOUNDS_SHADOW_DEPTH|farHazeAlpha|strongFarHazeAlpha|extended(Width|Height)|palette\.(outerBackground|playableBackground|groundSpecks|edgeAccent|cropStroke|leftBuffer|rightBuffer)|terrainPatches\.forEach|patch\.(shape|position|size|alpha|rotation|color|kind)|index\s*<\s*120|index\s*\*\s*(173|251)|index\s*%\s*(3|4|5|11)|crop\.(offset|crop|scale)|WORLD_SIZE\.(x|y)\s*-\s*FLOOR_TILE_SIZE\s*-\s*8|seamColor|rivetColor|cornerColor|accentColor|const\s+gold\s*=|const\s+cyan\s*=|0x030608|0x69dff6|0x11171b|0x1b2428|0x1a2529|0x263239|0x020304|0x0b1216|0xc58f39|0x020507|0x091015|0x1b252b|0x070a0d|0x223038|0xc08a31|0x0f171c|0x27353b|0xf0bf54|0x05090c|0x60727a|0x18242a|0x41535a|0x0a1014|0x0a1217|0x020405|0x23333a)\b/,
  "arena background object declarations or pure presentation rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/arena/occlusionAlphaController.ts",
  /\b(export\s+interface\s+OcclusionAlphaInput|function\s+(shouldFadeOccludable|overlapsTrigger)\b|OCCLUSION_(PROBE|FADE|LERP)|Math\.(hypot|abs))\b/,
  "arena occlusion alpha object declarations or pure rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/arena/obstacleSkinPresenter.ts",
  /\b(OBSTACLE_SKIN_|WALL_BRACE_COLOR|CRATE_BRACE_COLOR|function\s+(createObstacleCornerPlates|createCoverFootprintCues|isBorderObstacle)\b|Math\.max|obstacle\.obstacleId\.startsWith)\b/,
  "arena obstacle skin object declarations or pure rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/arena/arenaDecorationPresenter.ts",
  /\b(interface\s+NaturalPickupPadPalette|function\s+(createNaturalPickupPads|naturalPickupPadPalette)\b|type\s+BattleMapThemeId|point\.position\.y\s*\+\s*(8|9|4)|point\.position\.x\s*\+\s*5|0x29343a|0x11181c|0xf0bd58|0x21343c|0x0d1a1e|0x8ff3ff|0x8aa7b4|0xdceef5|0x7ea5b4|0xb9dbe8|0x243018|0x7d8b3c|0xd4b85a|0x1f301d|0x4f7b42|0x9fdd7a|0x2b2f1d|0xa57634|0xe0b15e|0x20301f|0x527546|0x9be77d)\b/,
  "arena decoration pickup-pad object declarations or pure rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/arena/arenaDecorationPresenter.ts",
  /\b(const\s+(pylons|machinery|lowDeckPlates)\b|scene\.add\.image\(position\.x,\s*position\.y,\s*(WALL_TEXTURE_KEY|ROCK_TEXTURE_KEY|CRATE_TEXTURE_KEY)\)|registerDecorativeOccludable\((pylon|machine),|ARENA_ENERGY_ACCENT_COLOR|0x020405|0x1b252c|0x2b363b|0x1f2b31|0x5fd9ff)\b/,
  "arena decoration industrial object declarations or pure rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/camera/battleCameraDirector.ts",
  /\b(type\s+(ConfigureBattleCameraInput|UpdateBattleCameraTargetInput)|const\s+(CAMERA_DEADZONE|POINTER_LOOK_AHEAD_RATIO|POINTER_LOOK_AHEAD_MAX|CAMERA_OFFSET_LERP)\b|function\s+resolveCameraPointer\b|Phaser\.Math\.(Clamp|Linear)|scaleSize\.width\s*\/\s*2|scaleSize\.height\s*\/\s*2|setZoom\(1\.40?\)|setBackgroundColor\(["']#57a6d9["']\))\b/,
  "battle camera object declarations or pure rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/session/localBattleFrameSceneBridge.ts",
  /\b(shouldSuppressPrimaryFireForSkill|suppressPrimaryFire)\b/,
  "player input fire-suppression rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/timers/heroWeaponSkillTimers.ts",
  /jumpCooldownMs\s*=\s*Math\.max|STAMINA_RECOVER_PER_SECOND\s*\*|velocity\s*=\s*\{\s*x:\s*0,\s*y:\s*0\s*\}|preparedSkill\s*=\s*null/,
  "actor runtime timer rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts",
  /\b(export\s+type\s+LocalProjectileTracerFeedback\b|export\s+interface\s+SharedAuthoritativeLocalFeedbackSceneBridgeOptions\b|MUZZLE_FEEDBACK_STYLES|canRequestReloadFeedback|canPresentPrimaryFeedback|resolveTargetedSkillFeedbackRequest|getPrimaryFeedbackIntervalMs|SKILL_REJECT_FEEDBACK_MIN_MS|RELOAD_INTENT_FEEDBACK_MIN_MS)\b/,
  "shared-authoritative local feedback rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/factories/GameSceneBattleFeedbackBridgeFactory.ts",
  /\bexport\s+interface\s+CreateGameSceneBattleFeedbackBridgeInput\b/,
  "game scene battle feedback bridge factory input contract"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/factories/GameSceneSharedAuthoritativeLocalFeedbackBridgeFactory.ts",
  /\bexport\s+interface\s+CreateGameSceneSharedAuthoritativeLocalFeedbackBridgeInput\b/,
  "game scene shared-authoritative local feedback bridge factory input contract"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/sceneVfxController.ts",
  /\b(interface\s+RingEffect\b|scene\.add\.circle\(position\.x,\s*position\.y,\s*radius,\s*color,\s*0\.18\)|setDepth\(45\)|setStrokeStyle\(2,\s*color,\s*0\.78\)|ttlMs:\s*220|maxTtlMs:\s*220|progress\s*\*\s*0\.42|setAlpha\(0\.18\s*\*\s*ttlRatio\))\b/,
  "scene ring pulse object declarations or pure visual planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/sceneVfxController.ts",
  /import\s+\{[^}]*type\s+(SkillFeedbackIntent|FloatingTone)[^}]*\}\s+from\s+["']\.\/(skillFeedbackVfxPresenter|floatingTextVfxPresenter)["']|export\s+type\s+\{\s*(SkillFeedbackIntent|FloatingTone)\s*\}\s+from\s+["']\.\/(skillFeedbackVfxPresenter|floatingTextVfxPresenter)["']/,
  "scene VFX controller adapter type facade imports"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/transientVfxLifecycle.ts",
  /\binterface\s+(TransientEffectRecord|SceneVfxDiagnosticsSnapshot|SlayDemoBattleDiagnosticsRoot|TransientVfxLifecycleOptions)\b|const\s+(MAX_TRANSIENT_VFX|TRANSIENT_COMPACTION_LIMIT)\b|Math\.max\(|diagnosticsRoot\.vfx\s*=\s*\{/,
  "transient VFX lifecycle object declarations or pure capacity/diagnostic rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/playerMotionTweenController.ts",
  /\b(export\s+interface\s+PlayerMotionTweenControllerOptions\b|type\s+MotionType\b|motionType\s*===\s*["']jump["']\s*\?\s*42\s*:\s*28|0xbce8ff|0xf4f6ff|0x86dfff|motionType\s*===\s*["']jump["']\s*\?\s*0\.18\s*:\s*0\.24|baseScale\s*\*\s*1\.12|ease:\s*["']Quad\.Out["']|motionType\s*===\s*["']blink["']\s*\?\s*["']Cubic\.InOut["']\s*:\s*["']Quad\.Out["']|Math\.sin\(start\.t\s*\*\s*Math\.PI\)\s*\*\s*0\.07|createPulse\(destination,\s*(28,\s*0xc5f3ff|22,\s*0xdfe8ff|44,\s*0x72e7ff)\)|setDepth\(41\)|scale\s*\*\s*0\.92|duration:\s*180)\b/,
  "player motion afterimage or motion feedback planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/skillFeedbackVfxPresenter.ts",
  /\b(DASH_FEEDBACK_COLOR|Math\.atan2|setDepth\(82\)|setScale\(0\.78\)|lineStyle\(4,\s*0x18334a,\s*0\.48\)|strokeCircle\(0,\s*0,\s*(20|24)\)|lineBetween\(facing\.x\s*\*\s*6|lineBetween\(\s*facing\.x\s*\*\s*16|position\.x\s*-\s*facing\.x\s*\*\s*(6|34)|\[-8,\s*0,\s*8\]|index\s*===\s*1\s*\?\s*(34|4|0\.72)|setDepth\(81\)|scaleX:\s*0\.32|scaleY:\s*1\.35|duration:\s*155\s*\+\s*index\s*\*\s*18)\b/,
  "skill feedback dash VFX geometry or tween planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/skillFeedbackVfxPresenter.ts",
  /\b(BLINK_FEEDBACK_COLOR|BLINK_FEEDBACK_CORE_COLOR|function\s+(normalizeDirection|perpendicularDirection)\b|Math\.hypot|setDepth\(84\)|setScale\(release\s*\?\s*0\.72\s*:\s*0\.82\)|lineStyle\(5,\s*0x173848,\s*0\.62\)|strokeDiamond\(marker,\s*radius\s*\*\s*(0\.88|0\.7)\)|strokeCircle\(0,\s*0,\s*radius\s*\*\s*0\.52\)|lineBetween\(\s*-facing\.x\s*\*\s*radius\s*\*\s*(1\.35|1\.1)|fillStyle\(BLINK_FEEDBACK_CORE_COLOR|fillCircle\(0,\s*0,\s*release\s*\?\s*4\s*:\s*3\)|scale:\s*release\s*\?\s*1\.34\s*:\s*1\.18|duration:\s*release\s*\?\s*230\s*:\s*180)\b/,
  "skill feedback blink VFX direction, graphics, or tween planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/skillFeedbackVfxPresenter.ts",
  /\b(FREEZE_FEEDBACK_COLOR|FREEZE_FEEDBACK_CORE_COLOR|SKILL_DEFINITIONS\.Freeze|setDepth\(83\)|setScale\(release\s*\?\s*0\.72\s*:\s*0\.74\)|fillCircle\(0,\s*0,\s*radius\s*\*\s*0\.78\)|lineStyle\(5,\s*0x123a46,\s*0\.5\)|strokeCircle\(0,\s*0,\s*radius\s*\*\s*0\.56\)|for\s*\(let\s+index\s*=\s*0;\s*index\s*<\s*shardCount|Math\.PI\s*\*\s*2\s*\*\s*index|Phaser\.Math\.FloatBetween\(0\.42,\s*0\.58\)|Phaser\.Math\.FloatBetween\(0\.82,\s*1\.08\)|Math\.(cos|sin)\(angle|angle\s*\+\s*0\.22|outer\s*-\s*5|scale:\s*release\s*\?\s*1\s*:\s*1\.12|rotation:\s*release\s*\?\s*0\.12\s*:\s*0\.04|duration:\s*release\s*\?\s*260\s*:\s*210)\b/,
  "skill feedback freeze VFX sampling, graphics, or tween planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/skillFeedbackVfxPresenter.ts",
  /\b(SKILL_REJECT_COLOR|Math\.max\(16,\s*radius\s*\*\s*0\.62\)|setDepth\(85\)|setScale\(0\.86\)|lineStyle\(5,\s*0x36141a,\s*0\.58\)|lineBetween\(-size,\s*-size,\s*size,\s*size\)|lineBetween\(-size,\s*size,\s*size,\s*-size\)|lineStyle\(3,\s*SKILL_REJECT_COLOR,\s*0\.96\)|lineStyle\(2,\s*0xffffff,\s*0\.42\)|size\s*\*\s*(0\.42|1\.18|0\.1|0\.76|0\.52|1\.08|0\.16|0\.72|1\.12|0\.18|0\.06|1\.1|0\.32|0\.66|0\.12)|strokeCircle\(0,\s*0,\s*Math\.max\(8,\s*radius\s*\*\s*0\.48\)\)|scale:\s*1\.14|duration:\s*150)\b/,
  "skill feedback rejection VFX graphics or tween planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/floatingTextVfxPresenter.ts",
  /\b(export\s+type\s+FloatingTone\s*=|type\s+(TrackTransient|DestroyTransient)\b|interface\s+FloatingTextVfxPresenterDependencies\b|FLOATING_TEXT_PALETTE|fontFamily:\s*["']Consolas["']|fontSize:\s*["']18px["']|setDepth\(80\)|setStroke\(["']#12212b["'],\s*3\)|position\.y\s*-\s*(10|42)|duration:\s*620|ease:\s*["']Cubic\.Out["'])\b/,
  "floating text VFX object declarations or pure presentation rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/weaponActionPlanPresenter.ts",
  /\b(export\s+interface\s+WeaponActionPlanPresenterCallbacks\b|WeaponActionMuzzleVfx|function\s+(applyMuzzleVfx|normalizeAimDirection)\b|Math\.(atan2|cos|sin)|normalizedAimDirection|plan\.(canFire|muzzle|projectiles|recoilStrength))\b/,
  "weapon action presentation object declarations or pure rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/combatProjectileEffectPresenter.ts",
  /\b(export\s+interface\s+CombatProjectileEffectPresenterCallbacks\b|normalizeVector|effect\.type\s*===|effect\.(projectileKind|killed|targetHeroId|ownerHeroId|origin)|snapshot\.heroes\.find|target\.alive|0xffb36f|0xffb677|0xffd57a|0xff9a9a|0xffe2ba|0xffffff|0\.0022|callbacks\.createShockwave\(effect\.origin)\b/,
  "combat projectile effect presentation object declarations or pure rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/combatProjectileEffectSceneBridge.ts",
  /\bexport\s+interface\s+CombatProjectileEffectSceneBridgeOptions\b|snapshot\.heroes\.find|target\.alive\b/,
  "combat projectile effect scene-bridge object declarations or knockback target lookup rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/weaponActionSceneBridge.ts",
  /\bexport\s+interface\s+WeaponActionSceneBridgeOptions\b|!player\.alive|player\.preparedSkill\s*!==\s*null|isPlayerMotionActive\(\)\s*\|\||getWeaponSwitchRemainingMs\(\)\s*>\s*0/,
  "weapon action scene-bridge object declarations or fire-readiness rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/playerAbilitySceneBridge.ts",
  /\bexport\s+interface\s+PlayerAbilitySceneBridgeOptions\b|getHeroViews\(\)\.get\(|["']hero-player["']/,
  "player ability scene-bridge object declarations or texture-key lookup rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/projectileFrameSceneBridge.ts",
  /\bexport\s+interface\s+ProjectileFrameSceneBridgeOptions\b|obstacleCollision\s*===\s*null\s*\|\||obstacleBoundsRef\s*!==\s*obstacleBounds/,
  "projectile frame scene-bridge object declarations or obstacle-collision cache rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/projectileFeedbackEffectPlanPresenter.ts",
  /\bexport\s+interface\s+BattleProjectileFeedbackEffectPresenterCallbacks\b|switch\s*\(\s*effect\.effect\s*\)|effect\.(position|radius|color|options|startRadius|endRadius|durationMs)/,
  "projectile feedback effect presenter object declarations or effect-to-action mapping rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/combatProjectileEffectPresenter.ts",
  /export\s+type\s+\{\s*CombatProjectileEffectPresenterCallbacks\s*\}\s+from\s+["']\.\/objects\/CombatProjectileEffectPresenterObjects["']/,
  "combat projectile effect presenter adapter type facade"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/floatingTextVfxPresenter.ts",
  /export\s+type\s+\{\s*FloatingTone\s*\}\s+from\s+["']\.\/objects\/FloatingTextVfxObjects["']/,
  "floating text presenter adapter type facade"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/remoteProjectileBirthFeedbackPresenter.ts",
  /export\s+type\s+\{\s*RemoteProjectileBirthFeedbackPresenterCallbacks\s*\}\s+from\s+["']\.\/objects\/RemoteProjectileBirthFeedbackPresenterObjects["']/,
  "remote projectile birth presenter adapter type facade"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/skillFeedbackVfxPresenter.ts",
  /export\s+type\s+\{\s*SkillFeedbackIntent\s*\}\s+from\s+["']\.\/objects\/SkillFeedbackVfxObjects["']/,
  "skill feedback presenter adapter type facade"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/weaponActionPlanPresenter.ts",
  /export\s+type\s+\{\s*WeaponActionPlanPresenterCallbacks\s*\}\s+from\s+["']\.\/objects\/WeaponActionPlanPresenterObjects["']/,
  "weapon action presenter adapter type facade"
);
for (const file of [
  "frontend/src/runtime/battle/game/renderer/effects/projectileFeedbackEffectPlanPresenter.ts",
  "frontend/src/runtime/battle/game/renderer/effects/projectileTerminalVfxPresenter.ts",
  "frontend/src/runtime/battle/game/renderer/effects/projectileTracerVfxRenderer.ts",
  "frontend/src/runtime/battle/game/renderer/effects/sceneVfxController.ts",
  "frontend/src/runtime/battle/game/renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge.ts"
]) {
  assertFileDoesNotMatch(file, /export\s+type\s+\{[\s\S]*?\}\s+from\s+["']\.\/objects\//, "effects adapter object type facade");
}
for (const file of [
  "frontend/src/runtime/battle/game/renderer/effects/factories/GameSceneBattleFeedbackBridgeFactory.ts",
  "frontend/src/runtime/battle/game/renderer/effects/factories/GameSceneSharedAuthoritativeLocalFeedbackBridgeFactory.ts"
]) {
  assertFileDoesNotMatch(file, /export\s+type\s+\{[\s\S]*?\}\s+from\s+["']\.\.\/objects\//, "effects factory object type facade");
}
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/projectileTracerVfxRenderer.ts",
  /\b(export\s+interface\s+(ProjectileTracerOptions|ProjectileTracerVfxRendererDependencies)\b|const\s+(DEFAULT_TRACER_DURATION_MS|TRACER_GHOST_RADIUS_SCALE)\b|function\s+(normalizeDirection|perpendicularDirection)\b|Math\.(hypot|atan2|max|min)|Phaser\.Math\.Clamp|options\.(direction|length|thickness|durationMs|alpha|ghostScale|glintAlphaScale|underglowAlphaScale|coreAlphaScale|ghostAlphaScale)|glintLength|glintOffset|coreAlphaScale|ghostAlphaScale|underglowAlphaScale)\b/,
  "projectile tracer VFX object declarations or pure geometry/tween rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/muzzleAndHitVfxPresenter.ts",
  /\b(type\s+(TrackTransient|DestroyTransient|CreateRingPulse)\b|interface\s+MuzzleAndHitVfxPresenterDependencies\b|scene\.add\.circle\(position\.x,\s*position\.y,\s*startRadius,\s*color,\s*0\.16\)|setDepth\(46\)|setStrokeStyle\(3,\s*color,\s*0\.84\)|scale[XY]:\s*endRadius\s*\/\s*startRadius)\b/,
  "muzzle and hit VFX object declarations or shockwave planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/muzzleAndHitVfxPresenter.ts",
  /\b(scene\.add\.circle\(position\.x,\s*position\.y,\s*5,\s*color,\s*0\.84\)|for\s*\(let\s+index\s*=\s*0;\s*index\s*<\s*5|Math\.PI\s*\*\s*2\s*\*\s*index|Phaser\.Math\.FloatBetween\(-0\.2,\s*0\.2\)|Phaser\.Math\.Between\(7,\s*12\)|Phaser\.Math\.Between\(14,\s*24\)|const\s+sparkLength\s*=\s*Phaser\.Math\.Between\(7,\s*12\)|const\s+angle\s*=|scaleX:\s*0\.28|scaleY:\s*0\.7|duration:\s*125)\b/,
  "muzzle and hit impact-spark planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/muzzleAndHitVfxPresenter.ts",
  /\b(scene\.add\.circle\(position\.x,\s*position\.y,\s*(6,\s*color,\s*0|2,\s*color,\s*0\.42)\)|setStrokeStyle\(1,\s*color,\s*0\.34\)|duration:\s*130)\b/,
  "muzzle and hit projectile dissipate planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/muzzleAndHitVfxPresenter.ts",
  /\b(marker\.(lineStyle|strokeCircle|fillStyle|fillCircle|lineBetween)|scene\.add\.graphics\(\)\.setDepth\(82\)|duration:\s*155|scale:\s*1\.35|0xffffff,\s*0\.58|color,\s*0\.26|color,\s*0\.72|lineBetween\(0,\s*-15,\s*4,\s*-11\)|strokeCircle\(0,\s*0,\s*10\))\b/,
  "muzzle and hit hit-confirm graphics planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/muzzleAndHitVfxPresenter.ts",
  /\b(MAX_MUZZLE_SPARKS|function\s+(normalizeDirection|perpendicularDirection)\b|Math\.(hypot|atan2)|Phaser\.Math\.FloatBetween\(-0\.68,\s*0\.68\)|Phaser\.Math\.Between\(18,\s*34\)|Phaser\.Math\.FloatBetween\(-radius\s*\*\s*0\.28,\s*radius\s*\*\s*0\.28\)|Phaser\.Math\.Between\(6,\s*12\)|Phaser\.Math\.Between\(0,\s*45\)|Math\.max\(4,\s*radius\s*\*\s*0\.42\)|Math\.max\(18,\s*radius\s*\*\s*1\.9\)|Math\.max\(4,\s*radius\s*\*\s*0\.48\)|scaleX:\s*0\.48|scaleY:\s*1\.6|scaleX:\s*0\.32|scaleY:\s*0\.76|position\.x\s*\+\s*facing\.x\s*\*\s*(3|4))\b/,
  "muzzle and hit muzzle-burst direction, sampling, or visual planning rules"
);
assertNoPath("frontend/src/runtime/battle/game/renderer/effects/projectileTerminalFeedbackPolicy.ts");
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/heroAndPickupFeedbackPresenter.ts",
  /\b(interface\s+(HeroFeedbackState|PickupFeedbackState)|createHeroFeedbackState|createWeaponPickupFeedbackState|createItemPickupFeedbackState|resolveCurrentWeaponAmmoTotal|presentAuthoritativeHealthDelta|presentAuthoritativeAmmoDelta)\b/,
  "hero or pickup feedback planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/heroAndPickupFeedbackPresenter.ts",
  /\b(interface\s+(HeroFeedbackPresentationOptions|AuthoritativePickupFeedbackPresentationOptions)\b|type\s+(HeroFeedbackPresentationAction|PickupFeedbackPresentationAction)\b|function\s+(presentHeroFeedbackPlan|presentPickupFeedbackPlan)\b|switch\s*\(\s*input\.plan\.kind\s*\)|input\.plan\.(floatingText|pulse|kind|position|text|tone|radius|color|heroId|durationMs|intensity))\b/,
  "hero or pickup feedback presenter object declarations or plan-to-action mapping rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/battleFeedbackSceneBridge.ts",
  /\bexport\s+interface\s+BattleFeedbackSceneBridgeOptions\b|hasPlayedAuthoritativeProjectileTerminalForProjectile|shouldPresentAuthoritativeTerminalTracer|resolveAuthoritativeProjectileTerminalFreshnessBaseline|scratchLiveProjectileIds|terminalKey\.startsWith|seenLiveProjectileIdQueue\.length\s*>|playedAuthoritativeProjectileTerminalQueue\.length\s*>|resolveAuthoritativeFrameElapsedWatermark/,
  "projectile feedback queue or freshness rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/battleFeedbackSceneBridge.ts",
  /\bshouldRecordProjectileTerminalDiagnostics\b/,
  "projectile terminal diagnostics gate usage in scene bridge"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/projectileTerminalVfxPresenter.ts",
  /\b(export\s+(type\s+ProjectileTerminalVfxPresenterCallbacks|interface\s+(ProjectileTerminalVfxPresentation|AuthoritativeProjectileTerminalVfxPresentation|AuthoritativeProjectileTerminalTracerPresentation))\b|ROCKET_SPLASH_VISUAL_RADIUS|createAuthoritativeProjectileTerminalCorrectionTracerOptions|createAuthoritativeProjectileTerminalTracerOptions|createProjectileTerminalCorrectionTracerOptions|createProjectileTerminalTracerOptions|resolveAuthoritativeTerminalVfxStrategy|resolveRocketShockwaveStartRadius|softenColor|previous\.ttlMs|previous\.kind|strategy\.)\b/,
  "projectile terminal VFX presentation planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/remoteProjectileBirthFeedbackPresenter.ts",
  /\b(export\s+interface\s+(RemoteProjectileBirthFeedbackPresenterCallbacks|AuthoritativeRemoteProjectileBirthFeedbackPresentation)\b|recordRemoteProjectileBirthDiagnostics\(\s*\{|plan\.(projectile|ownerDisplayName|position|effects)|PROJECTILE_SPARK_COLORS|createRemoteGatlingProjectileBirthTracerOptions|resolveRemoteProjectileBirthFeedbackPosition|previousProjectileStates\.has|projectile\.ownerHeroId\s*===|projectile\.kind\s*===)\b/,
  "remote projectile birth presentation planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/projectileTerminalDiagnosticsRecorder.ts",
  /\b(export\s+interface\s+(ProjectileTerminalDiagnosticsRecordInput|AuthoritativeProjectileTerminalDiagnosticsRecordInput)\b|function\s+collectHeroDisplayPositions\b|new\s+Map<string,\s*Vec2>|positions\.set\(hero\.heroId|createTerminalDiagnosticProjectileState|resolveNearestTerminalHero|terminalProjectile|nearestHero|previous\.ttlMs\s*<=|maxLifetimeMs:\s*previous\?\.maxLifetimeMs)\b/,
  "projectile terminal diagnostic planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/effects/projectileTerminalDiagnosticsRecorder.ts",
  /\bexport\s+function\s+shouldRecordProjectileTerminalDiagnostics\b/,
  "projectile terminal diagnostics gate export"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/diagnostics/remoteViewDiagnostics.ts",
  /\b(export\s+interface\s+(RemoteHeroView|RemoteProjectile|RemoteView)|function\s+(summarizeRemoteHeroMetric|percentile|cloneHeroSample|cloneProjectileBirthSample|cloneProjectileTerminalSample|isFiniteVec2|cloneVec2|cloneNullableVec2|distanceBetween|toFiniteNumberOrNull|normalizeOptionalString)\b)/,
  "remote view diagnostics object declarations or pure helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/diagnostics/localFeedbackDiagnostics.ts",
  /\b(export\s+interface\s+(LocalMotionFeedback|LocalMuzzleFeedback|LocalFeedback)|function\s+(createChannelSnapshot|distanceBetween|cloneVec2)\b|LOCAL_MOTION_DISTANCE_EPSILON)\b/,
  "local feedback diagnostics object declarations or pure helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/diagnostics/localHeroCorrectionDiagnostics.ts",
  /\b(export\s+interface\s+LocalHeroCorrection|function\s+(summarizeDistances|percentile|distanceBetween)\b|preDistance\s*=\s*distanceBetween|postDistance\s*=\s*distanceBetween)\b/,
  "local hero correction diagnostics object declarations or pure helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/diagnostics/visionDiagnostics.ts",
  /\b(interface\s+BattleVision|function\s+(cloneVec2|vectorLength|distanceBetween)\b|screenPxPerWorldUnitX|screenPxPerWorldUnitY|actualOffsetDistance:\s*vectorLength|targetAheadDistance:\s*distanceBetween)\b/,
  "vision diagnostics object declarations or pure helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/diagnostics/authoritativeLocalHeroReplayDiagnostics.ts",
  /\b(export\s+(interface|type)\s+AuthoritativeLocalHeroReplay|function\s+(safeCount|safeSeq|safeNonNegativeNumber|safeNullableDistance)\b|const\s+sample:\s*AuthoritativeLocalHeroReplayDiagnosticSample\s*=|recentSamples:\s*samples\.map|skipReasonCounts:\s*\{\s*\.\.\.skipReasonCounts\s*\})/,
  "authoritative local hero replay diagnostics object declarations or pure helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/runtime/BattleRuntimeFactory.ts",
  /\bexport\s+interface\s+(BattleRuntimeHandle|CreateBattleRuntimeOptions)\b/,
  "renderer runtime factory object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/runtime/BattleRuntimeBootSnapshotFactory.ts",
  /\bexport\s+interface\s+CreateBattleRuntimeBootSnapshotInput\b/,
  "runtime boot snapshot object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/runtime/BattlePhaserGameFactory.ts",
  /\b(export\s+interface\s+CreateBattlePhaserGameInput|mountNode\.clientWidth\s*\|\|\s*window\.innerWidth|mountNode\.clientHeight\s*\|\|\s*window\.innerHeight)\b/,
  "Phaser runtime object declarations or viewport fallback rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/runtime/BattleRuntimeDomLifecycle.ts",
  /\bexport\s+interface\s+BattleRuntimeMountRoots\b/,
  "runtime DOM object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/authoritative/BattleLocalAuthoritativeHeroCorrectionController.ts",
  /\b(interface\s+PendingLocalAuthoritativeCorrection|function\s+distanceBetween\b|Math\.hypot|Math\.exp|Math\.LN2|selectLocalAuthoritativeHeroCorrectionTuning|isFiniteLocalAuthoritativeCorrectionPosition)\b/,
  "local authoritative correction pending object declarations or pure update rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/authoritative/BattleAuthoritativeRenderPipeline.ts",
  /\bexport\s+interface\s+PhaserAuthoritativeRenderPipeline(Input|Frame)\b/,
  "authoritative render pipeline object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/authoritative/BattleAuthoritativeFrameSceneBridge.ts",
  /\b(export\s+type\s+GameSceneAuthoritativeFrame\s*=|export\s+interface\s+(GameSceneAuthoritativeFrameOptions|ApplyAuthoritativeFrameSceneBridgeInput|UpdateAuthoritativeLocalDisplayMotionInput))\b/,
  "authoritative scene bridge object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/authoritative/BattleAuthoritativeLocalHeroMotion.ts",
  /\bexport\s+interface\s+(ApplyAuthoritativeLocalHeroDisplayMotionInput|AuthoritativeLocalHeroDisplayMotionResult)\b/,
  "authoritative local hero motion object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/authoritative/BattleAuthoritativeFrameSnapshotApplier.ts",
  /\b(export\s+type\s+LocalPlayerAuthoritative(CorrectionTarget|ReplayContext)\s*=|export\s+interface\s+ApplyAuthoritativeFrameToSnapshotInput)\b/,
  "authoritative frame snapshot applier object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/authoritative/BattleAuthoritativeLocalHeroReplay.ts",
  /\bexport\s+interface\s+ResolveAuthoritativeLocalHeroReplayTargetInput\b/,
  "authoritative local hero replay object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/arena/arenaBuilder.ts",
  /\bexport\s+(type|interface)\s+(OccludableSprite|OccludableTrigger|OccludableMode|ObstacleBounds|OccludableView|ArenaBuilderContext)\b/,
  "arena builder object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/arena/arenaBuilder.ts",
  /\bfunction\s+(buildingWallObstacle|shapeFromSpec|collisionBoundsSize|triggerFromCollision|mapDecorativeKind|doorTextureForTheme|backgroundColorForTheme|depthForObstacle)\b/,
  "arena builder pure rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/BattleLocalHeroDisplay.ts",
  /\b(interface\s+LocalHeroDisplay(Actor|Pose|PoseReader|PoseStore|PositionStore)\b|export\s+interface\s+LocalHeroDisplay)/,
  "local hero display object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/BattleGameSceneHeroActorBridge.ts",
  /\bexport\s+interface\s+GameScenePlayerActorHandle\b/,
  "game scene player actor handle object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/BattleGameSceneHeroActorBridge.ts",
  /from\s+["']\.\/worldViewFactory["']/,
  "world view factory facade imports"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/BattleGameSceneHeroActorBridge.ts",
  /\b(resolveHeroVisual|BASE_MOVE_SPEED|SPRINT_MULTIPLIER|player\.radius\s*\*\s*2|delayedCall\(80|setTint\(resolveHeroVisual|flashColor\)|\.image\([^)]*resolveHeroVisual)\b/,
  "game scene hero actor visual planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/BattleGameSceneHeroDisplacementBridge.ts",
  /\bexport\s+interface\s+GameSceneHeroDisplacementBridge(Options)?\b/,
  "game scene hero displacement object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroReadabilityView.ts",
  /\b(export\s+interface\s+(WeaponCueReadabilityStyle|HeroReadabilityView|HeroReadabilitySyncView|HeroHealthView|HeroHealthVisualPlan)|export\s+const\s+WEAPON_CUE_READABILITY_STYLES)\b/,
  "hero readability view object declarations or style catalog"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroReadabilityView.ts",
  /\b(function\s+(resolveHeroReadabilityRadius|resolveHeroWeaponKind|getWeaponCueReadabilityStyle|isHeroInsideSlowField|resolveHeroHealthRatio|resolveHeroHealthVisualPlan|isFiniteVec2)\b|resolveHeroVisual|HERO_READABILITY_MIN_RADIUS|HERO_HEALTH_|Math\.sin|Phaser\.Math\.Clamp|displayWidth\s*=\s*48)\b/,
  "hero readability pure helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroReadabilityView.ts",
  /\b(isHeroInsideSlowField|resolveHeroReadabilityVisualPlan\([^)]*=>|const\s+(cueLength|cueOriginOffset|alpha|strokeAlpha)\s*=|radius\s*\*\s*1\.08|radius\s*\+\s*6|radius\s*\*\s*0\.22)\b/,
  "hero readability sync planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroReadabilityView.ts",
  /view\.weapon(Stock|Cue|Muzzle)\.setVisible\((true|false)\)/,
  "hero readability legacy weapon cue visibility planning"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroReadabilityView.ts",
  /\b(getWeaponCueReadabilityStyle|resolveHeroReadabilityRadius|resolveHeroWeaponKind|readabilityRadius|weaponCueStyle|HERO_READABILITY_|0x020711|0x06101b|0x9bf8ff|0x4ad9ff|0x76e4ff|setOrigin\(0\.74|setOrigin\(0\.08|scene\.add\.(circle|rectangle)\(\s*hero\.position)\b/,
  "hero readability creation planning rules"
);
assertNoPath("frontend/src/runtime/battle/game/renderer/entities/heroPresentationScale.ts");
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroWeaponOverlayView.ts",
  /\bexport\s+interface\s+(HeroWeaponOverlayView|SyncHeroWeaponOverlayVisualsInput)\b/,
  "hero weapon overlay object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroWeaponOverlayView.ts",
  /\b(function\s+(resolveHeroWeaponOverlayLayoutPlan|resolveHeroWeaponOverlayScale)\b|HERO_WEAPON_(MAX_DISPLAY_SIZE|FORWARD_OFFSET_RADIUS_SCALE|SIDE_OFFSET_RADIUS_SCALE)|Math\.(cos|sin|max)|sourceMax|forwardOffset|sideOffset|perpendicularX|perpendicularY)\b/,
  "hero weapon overlay helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroWeaponOverlayView.ts",
  /(HERO_READABILITY_WEAPON_OVERLAY_DEPTH|getWeaponWorldTextureRef|["']Pistol["']|setOrigin\(0\.5,\s*0\.5\)|setVisible\(false\))/,
  "hero weapon overlay creation or texture planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroWeaponOverlayView.ts",
  /resolveHeroWeaponOverlay(Texture|Layout)Plan|setVisible\(true\)|setAlpha\(alpha\)|layoutPlan\.|texturePlan\./,
  "hero weapon overlay sync visual planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/objects/WorldViewFactoryObjects.ts",
  /from\s+["']\.\.\/heroWeaponOverlayView["']/,
  "hero weapon overlay adapter imports"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/objects/HeroReadabilityViewObjects.ts",
  /from\s+["']\.\.\/heroWeaponOverlayView["']/,
  "hero weapon overlay adapter imports"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/remoteHeroDisplayStateSync.ts",
  /\b(export\s+interface\s+(RemoteHeroInterpolationSample|RemoteHeroInterpolationBuffer|RemoteHeroDisplayView|RemoteHeroInterpolationViewState)|interface\s+(RemoteHeroDisplayState|CleanupRemoteHeroInterpolationBuffersInput|ResolveRemoteHeroDisplayStateInput|ResolveSmoothedDisplayPositionInput))\b/,
  "remote hero interpolation object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/remoteHeroDisplayStateSync.ts",
  /\b(function\s+(createRemoteHeroInterpolationSample|recordRemoteHeroInterpolationSample|resolveInterpolatedRemoteHeroDisplayState|resolveRemoteHeroFallbackDisplayState|interpolateFacing|isFiniteVec2|resolveFinitePosition|resolveSmoothedDisplayPosition)\b|AUTHORITATIVE_REMOTE_HERO_(SNAP_DISTANCE|SMOOTHING_MS|POSITION_EPSILON|FACING_EPSILON|INTERPOLATION_BUFFER_CAP)|AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS)\b/,
  "remote hero interpolation helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/remoteHeroDisplayStateSync.ts",
  /\b(cleanupRemoteHeroInterpolationBuffers|scratchActiveRemoteHeroIds|snapshot\.heroes\.forEach|remoteHeroInterpolationBuffers\.delete)\b/,
  "remote hero interpolation buffer sync adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/remoteHeroInterpolationBufferSync.ts",
  /\b(resolveRemoteHeroDisplayState|createRemoteHeroInterpolationSample|recordRemoteHeroInterpolationSample|resolveInterpolatedRemoteHeroDisplayState|resolveRemoteHeroFallbackDisplayState|getRemoteHeroInterpolationBuffer|resolveRenderNowMs|RemoteHeroDisplayState|ResolveRemoteHeroDisplayStateInput)\b/,
  "remote hero display-state sync adapter"
);
assertNoPath("frontend/src/runtime/battle/game/renderer/entities/remoteHeroInterpolationView.ts");
assertNoPath("frontend/src/runtime/battle/game/renderer/entities/projectileAndFieldViewPresentation.ts");
assertNoPath("frontend/src/runtime/battle/game/renderer/entities/objects/ProjectileAndFieldViewObjects.ts");
assertNoPath("frontend/src/runtime/battle/game/renderer/entities/functions/ProjectileAndFieldPresentationRules.ts");
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/projectileViewSync.ts",
  /\b(export\s+interface\s+(ProjectileView|SlowFieldView|ProjectileInterpolationBuffer|ProjectileViewSyncContext)|interface\s+(ProjectileInterpolationSample|ProjectileViewState|ProjectileDisplayState|ResolveProjectileDisplayStateInput|ResolveSmoothedDisplayPositionInput))\b/,
  "projectile view object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/projectileViewSync.ts",
  /\b(function\s+(createProjectileInterpolationSample|recordProjectileInterpolationSample|resolveInterpolatedProjectileDisplayState|resolveProjectileFallbackDisplayState|interpolateFacing|isFiniteVec2|resolveFinitePosition|resolveSmoothedDisplayPosition)\b|AUTHORITATIVE_PROJECTILE_(SNAP_DISTANCE|SMOOTHING_MS|POSITION_EPSILON|FACING_EPSILON|INTERPOLATION_BUFFER_CAP)|AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS)\b/,
  "projectile interpolation helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/projectileViewSync.ts",
  /\b(function\s+isProjectileInsideCullBounds\b|PROJECTILE_VIEW_CULL_PADDING|Phaser\.Math\.Clamp\((field|projectile)\.ttlMs|(field|projectile)\.ttlMs\s*\/\s*Math\.max)\b/,
  "projectile and slow-field presentation helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/projectileViewSync.ts",
  /\b(BattleSlowFieldState|syncSlowFieldViews|createSlowFieldView|resolveSlowFieldAlpha|scratchLiveSlowFieldIds|slowFieldViews|SlowFieldView)\b/,
  "slow-field view sync adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/projectileViewSync.ts",
  /\bgetProjectileDisplayPositionFromViews\b|worldViews\.projectileViews\.get\(projectileId\)/,
  "projectile display-position reader adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/projectileViewSync.ts",
  /\b(PROJECTILE_VIEW_POOL_LIMIT|getProjectileTextureRef|createProjectileView|configureProjectileView|destroyProjectileView|syncProjectileReadabilityVisuals|resolveProjectileLifetimeAlpha)\b|projectileViewPool\.(push|pop)|sprite\.(setTexture|setTint|destroy)\b/,
  "projectile view lifecycle adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/projectileViewLifecycle.ts",
  /\b(PROJECTILE_VIEW_POOL_LIMIT|getProjectileTextureRef|resolveProjectileLifetimeAlpha|textureRef|setOrigin\(0\.5,\s*0\.5\)|setDepth\(43\)|projectile\.ttlMs|projectile\.maxLifetimeMs)\b/,
  "projectile view lifecycle planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/projectileViewLifecycle.ts",
  /setActive\((true|false)\)\.setVisible\((true|false)\)/,
  "projectile view activation planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/projectileViewSync.ts",
  /\b(createProjectileInterpolationSample|recordProjectileInterpolationSample|resolveInterpolatedProjectileDisplayState|resolveProjectileFallbackDisplayState|resolveProjectileDisplayState|getProjectileInterpolationBuffer|resolveRenderNowMs)\b/,
  "projectile display-state sync adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/objects/ProjectileViewObjects.ts",
  /\bexport\s+interface\s+(SlowFieldView|SlowFieldViewState|SlowFieldViewSyncContext)\b|\bslowFieldViews:|scratchLiveSlowFieldIds:/,
  "slow-field view object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/slowFieldViewSync.ts",
  /\bProjectileAndField(SyncContext|ViewObjects)\b/,
  "slow-field view sync object imports"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/slowFieldViewSync.ts",
  /\b(resolveSlowFieldAlpha|function\s+createSlowFieldView|scene\.add\.circle|setRadius|setFillStyle|setStrokeStyle|\.destroy\(\)|0x9beeff|0xb9f7ff)\b/,
  "slow-field lifecycle or visual mutation adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/slowFieldViewLifecycle.ts",
  /\b(resolveSlowFieldAlpha|SLOW_FIELD_|0x9beeff|0xb9f7ff|field\.ttlMs|field\.durationMs|SLOW_FIELD_FILL_ALPHA\s*\*|SLOW_FIELD_RIM_ALPHA\s*\*)\b/,
  "slow-field visual planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/slowFieldViewLifecycle.ts",
  /view\.(fill|rim)\.destroy\(\)/,
  "slow-field release planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/functions/ProjectilePresentationRules.ts",
  /\b(BattleSlowFieldState|resolveSlowFieldAlpha|durationMs)\b/,
  "slow-field presentation rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/functions/SlowFieldPresentationRules.ts",
  /\b(BattleProjectileState|isProjectileInsideCullBounds|resolveProjectileLifetimeAlpha|PROJECTILE_VIEW_CULL_PADDING|maxLifetimeMs)\b/,
  "projectile presentation rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/localHeroMotionStreakView.ts",
  /\bexport\s+interface\s+LocalHeroMotionStreakView\b/,
  "local hero motion streak object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/localHeroMotionStreakView.ts",
  /\b(function\s+isFiniteVec2\b|LOCAL_HERO_MOTION_(MIN_SPEED|MAX_SPEED|DECAY)|Math\.(hypot|atan2|cos|sin)|Phaser\.Math\.Clamp|speedIntensity|frameDistance)\b/,
  "local hero motion streak helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/localHeroMotionStreakView.ts",
  /\b(LOCAL_HERO_MOTION_STREAK_|LOCAL_HERO_MOTION_TINT|0x8fe8ff|Array\.from\(\{\s*length|18\s*\+|index\s*\*\s*8|setOrigin\(1,\s*0\.5\)|setDepth\(31\)|setFillStyle\([^,]+,\s*0\))\b/,
  "local hero motion streak creation or fill planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/localHeroMotionStreakView.ts",
  /streak\.setVisible\(true\)/,
  "local hero motion streak render visibility planning"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/preparedSkillIndicatorViewSync.ts",
  /\b(export\s+interface\s+PreparedSkillIndicatorViewState|interface\s+(PreparedSkillIndicatorDisplayOverride|PreparedSkillIndicatorViewSyncContext)|type\s+PreparedSkillIndicatorPlan)\b/,
  "prepared skill indicator object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/preparedSkillIndicatorViewSync.ts",
  /\b(getPreparedTargetSkillRuntimeProfile|isPreparedTargetSkillKind|isPreparedIndicatorTargetValid|isFreezeIndicatorTargetValid|isBlinkIndicatorTargetValid|Phaser\.Math\.Distance|0x69ff9f|0xff6b6b|cooldownMs\s*<=)\b/,
  "prepared skill indicator helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/preparedSkillIndicatorViewSync.ts",
  /\b(syncPreparedSkillIndicatorCircle|PreparedSkillIndicatorCirclePlan|worldViews\.(rangeIndicator|targetIndicator)|indicator\.set(Visible|Position|Radius|FillStyle|StrokeStyle))\b/,
  "prepared skill indicator visual mutation adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/preparedSkillIndicatorViewVisualSync.ts",
  /worldViews\.(rangeIndicator|targetIndicator)\.setVisible\(false\)|indicator\.setVisible\(true\)|PreparedSkillIndicatorCirclePlan\b|hidePreparedSkillIndicatorViews/,
  "prepared skill indicator direct visual mutation planning"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/pickupViewPresentation.ts",
  /\b(interface\s+(PickupReadabilityStyle|CreateBasePickupViewInput|SyncPickupViewVisualsInput)|export\s+interface\s+PickupView|WEAPON_PICKUP_READABILITY_STYLES|ITEM_PICKUP_READABILITY_STYLE)\b/,
  "pickup view object declarations or style catalog"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/pickupViewPresentation.ts",
  /\b(function\s+(getWeaponPickupReadabilityStyle|resolvePickupPulse)\b|Math\.sin\(\(elapsedMs|const\s+(bob|pulse)\s*=|strokePulseAlpha:|glintRotation:)\b/,
  "pickup presentation helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/pickupViewPresentation.ts",
  /\b(PICKUP_|style\.(radius|fillTint|fillAlpha|strokeTint|strokeAlpha|strokeWidth|spriteScale|labelColor|labelPlateTint|labelPlateAlpha|glintTint)|position\.(x|y)\s*[-+]|setDisplaySize\(Math\.max|fontFamily|fontSize|Segoe UI|setTint\(style\.strokeTint|0\.58|0\.32|0\.24|0\.74|0\.55|0\.22)\b/,
  "pickup creation or visual planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/pickupViewSync.ts",
  /\bexport\s+interface\s+PickupViewSync(State|Context)\b/,
  "pickup sync object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/pickupViewSync.ts",
  /\blive(Weapon|Item)PickupIds\.add\(pickup\.pickupId\)|for\s*\(const\s+\[pickupId,\s*view\]\s+of\s+worldViews\.(pickupViews|itemPickupViews)\.entries\(\)\)/,
  "pickup live-id or hidden-view planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\bexport\s+interface\s+(HeroView|WorldViewState|LocalHeroDisplayOverride|WorldViewFactoryContext|WorldViewSyncContext)\b/,
  "world view factory object declarations"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\b(WEAPON_DEFINITIONS|Phaser\.Math\.Clamp|1\s*-\s*weaponSwitchRemainingMs\s*\/|1\s*-\s*weapon\.reloadRemainingMs\s*\/)\b/,
  "hero action progress helper rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\bif\s*\(!hero\.alive\)|\bhero\.heroId\s*===\s*snapshot\.playerHeroId\b|\bisPlayer\s*&&\s*localHeroDisplayOverride\s*\?|remoteAuthoritativeHeroIds\.has\(hero\.heroId\)/,
  "hero display-state and visibility planning rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\b(resolveHeroVisual|createHeroReadabilityView|createHeroWeaponOverlayView|createLocalHeroMotionStreakView)\b|scene\.add\s*\.(image|text|rectangle)\(/,
  "hero world view creation adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroWorldViewFactory.ts",
  /(scene\.add\.rectangle\(hero\.position|hero\.position\.(x|y)\s*[-+]|setOrigin\(0\.5,\s*1\)|setOrigin\(0,\s*0\.5\)|setStrokeStyle\(1,\s*0xffffff,\s*0\.14\)|0x0d1014|0x10151d|0xe7edf5)/,
  "hero world view creation plan details"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\bnew\s+Map\s*\(|\bscratch(Live|Active)|scene\.add\s*\.circle\(|\b(createHeroWorldView|createWeaponPickupView|createItemPickupView)\b/,
  "world view state creation adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\b(setHeroWeaponOverlayVisible|hideLocalHeroMotionStreaks)\b|view\.(shadow|bodyDisc|silhouetteRing|hitRing|statusRing|weaponStock|weaponCue|weaponMuzzle|sprite|nameLabel|healthBackground|healthFill|marker\?)\.setVisible\(/,
  "hero world view visibility sync adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroWorldViewVisibilitySync.ts",
  /view\.(shadow|bodyDisc|silhouetteRing|hitRing|statusRing|weaponStock|weaponCue|weaponMuzzle|sprite|nameLabel|healthBackground|healthFill|actionBackground|actionFill|marker\?)\.setVisible\((true|false)\)|setHeroWeaponOverlayVisible\(view\.weaponOverlay,\s*(true|false)\)|hideLocalHeroMotionStreaks\(view\.localMotionStreaks,\s*visibilityPlan\.resetLocalMotionStreaks\)/,
  "direct hero world view visibility boolean planning"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\b(syncHeroHealthVisuals|syncHeroReadabilityVisuals|syncLocalHeroMotionStreaks|resolveHeroActionProgressPlan)\b|view\.(sprite|nameLabel|healthBackground|healthFill|actionBackground|actionFill|marker\?)\.(setPosition|setRotation|setText|setVisible)\(|displayWidth\s*=/,
  "hero world view frame sync adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroWorldViewSync.ts",
  /\b(displayPosition\.(x|y)\s*[-+]|displayWidth\s*=\s*50\s*\*|actionProgress\.progress|view\.(nameLabel|healthBackground|healthFill|actionBackground|actionFill|marker\?)\.setPosition\(displayPosition)/,
  "hero world view frame layout rules"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/heroWorldViewSync.ts",
  /view\.action(Background|Fill)\.setVisible\((true|false)\)/,
  "hero action bar frame visibility planning"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\b(recordRemoteHeroViewDiagnostics|resolveRemoteHeroDisplayState)\b|displayStatePlan\.kind\s*===\s*"remoteAuthoritative"|targetPosition:\s*hero\.position|targetFacing:\s*hero\.facing/,
  "hero remote-authoritative display and diagnostics adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\b(cleanupRemoteHeroInterpolationBuffers|resolveHeroVisibilityPlan|resolveHeroDisplayStatePlan|isLocalPlayerHero|syncHeroWorldViewFrame|hideHeroWorldView|showHeroWorldViewBase|resolveHeroWorldViewDisplayState|recordHeroWorldViewRemoteDiagnostics)\b|snapshot\.heroes\.forEach|remoteHeroInterpolationBuffers\.delete/,
  "hero world views sync loop adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\b(worldViews\.heroViews\.get|getProjectileDisplayPositionFromViews)\b/,
  "world view display-position reader adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewFactory.ts",
  /\b(syncPreparedSkillIndicatorViews|pointerWorld|isBlinkTargetValid|isPreparedTargetValid|localHeroDisplayOverride)\b/,
  "world view indicator sync adapter"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/game/renderer/entities/worldViewStateFactory.ts",
  /scene\.add\s*\.circle\(\s*0\s*,\s*0|setVisible\((true|false)\)/,
  "world view indicator creation planning"
);
assertFileDoesNotMatch(
  "frontend/src/runtime/battle/local/timers/heroWeaponSkillTimers.ts",
  /function\s+(advanceWeaponSwitchState|clampWeaponIndex)\b/,
  "weapon switch timer transition rules"
);
assertDir("frontend/src/runtime/bots");
assertCodeFiles("frontend/src/runtime/bots");

assertNoLegacyImports();
assertNoFlatBattleCompatImports();

if (failures.length > 0) {
  console.error("Frontend domain structure audit failed:");
  for (const failure of failures) {
    console.error(`- ${failure}`);
  }
  process.exit(1);
}

console.log("Frontend domain structure audit passed.");
