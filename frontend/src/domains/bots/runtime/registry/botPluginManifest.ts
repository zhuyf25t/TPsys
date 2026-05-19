export const BOT_PLUGIN_API_VERSION = "bot-sdk/v1";

export type BotPluginApiVersion = typeof BOT_PLUGIN_API_VERSION;

export type BotPluginPermission = "bot:read-context" | "bot:issue-command";

export interface BotPluginManifest {
  readonly pluginId: string;
  readonly displayName: string;
  readonly version: string;
  readonly apiVersion: BotPluginApiVersion;
  readonly author: string;
  readonly description: string;
  readonly strategyIds: readonly string[];
  readonly botIds: readonly string[];
  readonly permissions: readonly BotPluginPermission[];
}

export interface BotPluginManifestIdAudit {
  readonly duplicatePluginIds: readonly string[];
  readonly duplicateStrategyIds: readonly string[];
  readonly duplicateBotIds: readonly string[];
}

export const BOT_PLUGIN_MANIFESTS = [
  {
    pluginId: "builtin-local-bots",
    displayName: "内置本地机器人",
    version: "1.0.0",
    apiVersion: BOT_PLUGIN_API_VERSION,
    author: "Slay Demo",
    description: "内置本地机器人档案和策略标签的只读插件声明。",
    strategyIds: [
      "anchor-skirmisher",
      "close-range-looter",
      "pressure-duelist",
      "mid-range-kiter",
      "pickup-chaser"
    ],
    botIds: ["bot-1", "bot-2", "bot-3", "bot-4", "bot-5"],
    permissions: ["bot:read-context", "bot:issue-command"]
  }
] as const satisfies readonly BotPluginManifest[];

export function listBotPluginManifests(): readonly BotPluginManifest[] {
  return BOT_PLUGIN_MANIFESTS;
}

export function findBotPluginManifestByStrategyId(strategyId: string): BotPluginManifest | undefined {
  const normalizedStrategyId = normalizeManifestId(strategyId);
  return normalizedStrategyId
    ? BOT_PLUGIN_MANIFESTS.find((manifest) =>
        manifest.strategyIds.some((candidate) => normalizeManifestId(candidate) === normalizedStrategyId)
      )
    : undefined;
}

export function findBotPluginManifestByBotId(botId: string): BotPluginManifest | undefined {
  const normalizedBotId = normalizeManifestId(botId);
  return normalizedBotId
    ? BOT_PLUGIN_MANIFESTS.find((manifest) =>
        manifest.botIds.some((candidate) => normalizeManifestId(candidate) === normalizedBotId)
      )
    : undefined;
}

export function validateBotPluginManifestIds(
  manifests: readonly BotPluginManifest[] = BOT_PLUGIN_MANIFESTS
): BotPluginManifestIdAudit {
  return {
    duplicatePluginIds: findDuplicateIds(manifests.map((manifest) => manifest.pluginId)),
    duplicateStrategyIds: findDuplicateIds(manifests.flatMap((manifest) => [...manifest.strategyIds])),
    duplicateBotIds: findDuplicateIds(manifests.flatMap((manifest) => [...manifest.botIds]))
  };
}

export function assertUniqueBotPluginManifestIds(manifests: readonly BotPluginManifest[] = BOT_PLUGIN_MANIFESTS): void {
  const audit = validateBotPluginManifestIds(manifests);
  const failures = [
    formatDuplicateIds("pluginId", audit.duplicatePluginIds),
    formatDuplicateIds("strategyIds", audit.duplicateStrategyIds),
    formatDuplicateIds("botIds", audit.duplicateBotIds)
  ].filter((message): message is string => Boolean(message));

  if (failures.length > 0) {
    throw new Error(`Bot plugin manifest IDs must be unique. ${failures.join(" ")}`);
  }
}

function findDuplicateIds(values: readonly string[]): readonly string[] {
  const seen = new Set<string>();
  const duplicates = new Set<string>();

  for (const value of values) {
    const normalized = normalizeManifestId(value);
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

function formatDuplicateIds(label: string, duplicates: readonly string[]): string | null {
  return duplicates.length > 0 ? `${label}: ${duplicates.join(", ")}` : null;
}

function normalizeManifestId(value: string): string | null {
  const normalized = value.trim().toLowerCase();
  return normalized.length > 0 ? normalized : null;
}
