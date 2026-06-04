import { fetchBotProfiles, type RemoteBotProfile } from "../../../apis/bots/botProfileApi";
import { findBotPluginManifestByBotId, type BotPluginManifest } from "./botPluginManifest";

export interface BotSkinMetadata {
  avatarKey: string;
  textureKey: string;
  label: string;
}

export interface BotProfile {
  botId: string;
  handle: string;
  displayName: string;
  initialRating: number;
  profileTone: string;
  strategyLabel: string;
  skin: BotSkinMetadata;
}

const ZOMBIE_BOT_SKIN: BotSkinMetadata = {
  avatarKey: "zombie",
  textureKey: "hero-zombie",
  label: "Zombie"
};

const LOCAL_BOT_REGISTRY: readonly BotProfile[] = [
  {
    botId: "bot-1",
    handle: "cpu-zombie-1",
    displayName: "Zombie 1",
    initialRating: 1010,
    profileTone: "steady",
    strategyLabel: "Infected anchor",
    skin: ZOMBIE_BOT_SKIN
  },
  {
    botId: "bot-2",
    handle: "cpu-zombie-2",
    displayName: "Zombie 2",
    initialRating: 990,
    profileTone: "scrappy",
    strategyLabel: "Infected rush",
    skin: ZOMBIE_BOT_SKIN
  },
  {
    botId: "bot-3",
    handle: "cpu-zombie-3",
    displayName: "Zombie 3",
    initialRating: 1040,
    profileTone: "aggressive",
    strategyLabel: "Infected pressure",
    skin: ZOMBIE_BOT_SKIN
  },
  {
    botId: "bot-4",
    handle: "cpu-zombie-4",
    displayName: "Zombie 4",
    initialRating: 1025,
    profileTone: "patient",
    strategyLabel: "Infected stalker",
    skin: ZOMBIE_BOT_SKIN
  },
  {
    botId: "bot-5",
    handle: "cpu-zombie-5",
    displayName: "Zombie 5",
    initialRating: 980,
    profileTone: "opportunist",
    strategyLabel: "Infected scavenger",
    skin: ZOMBIE_BOT_SKIN
  }
] as const;

export const BOT_REGISTRY: readonly BotProfile[] = LOCAL_BOT_REGISTRY;

let botRegistryCache: readonly BotProfile[] = LOCAL_BOT_REGISTRY;
let botRegistryRefreshPromise: Promise<void> | null = null;

/** 中文名：列表机器人profiles（listBotProfiles）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function listBotProfiles(): readonly BotProfile[] {
  return botRegistryCache;
}

/** 中文名：获取机器人profileby标识（getBotProfileById）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function getBotProfileById(botId: string): BotProfile | undefined {
  return botRegistryCache.find((profile) => profile.botId === botId);
}

/** 中文名：获取机器人profilebyslot（getBotProfileBySlot）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function getBotProfileBySlot(slotIndex: number): BotProfile | undefined {
  return botRegistryCache[slotIndex];
}

/** 中文名：获取机器人pluginmanifestforprofile（getBotPluginManifestForProfile）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function getBotPluginManifestForProfile(botId: string): BotPluginManifest | undefined {
  return findBotPluginManifestByBotId(botId);
}

/** 中文名：refresh机器人profiles（refreshBotProfiles）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function refreshBotProfiles(): Promise<void> {
  if (!botRegistryRefreshPromise) {
    botRegistryRefreshPromise = fetchBotProfiles()
      .then((remoteProfiles) => {
        if (remoteProfiles && remoteProfiles.length > 0) {
          botRegistryCache = remoteProfiles.map(toBotProfile);
        }
      })
      .finally(() => {
        botRegistryRefreshPromise = null;
      });
  }

  return botRegistryRefreshPromise;
}

void refreshBotProfiles();

function toBotProfile(profile: RemoteBotProfile): BotProfile {
  return {
    botId: profile.botId,
    handle: profile.handle,
    displayName: profile.displayName,
    initialRating: profile.initialRating,
    profileTone: profile.profileTone,
    strategyLabel: profile.strategyLabel,
    skin: ZOMBIE_BOT_SKIN
  };
}
