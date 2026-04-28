import { fetchBotProfiles, type RemoteBotProfile } from "./botProfileApi";

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

const LOCAL_BOT_REGISTRY: readonly BotProfile[] = [
  {
    botId: "bot-1",
    handle: "cpu-sable",
    displayName: "Sable",
    initialRating: 1010,
    profileTone: "steady",
    strategyLabel: "Anchor skirmisher",
    skin: {
      avatarKey: "survivor",
      textureKey: "hero-survivor",
      label: "Survivor"
    }
  },
  {
    botId: "bot-2",
    handle: "cpu-rivet",
    displayName: "Rivet",
    initialRating: 990,
    profileTone: "scrappy",
    strategyLabel: "Close-range looter",
    skin: {
      avatarKey: "soldier",
      textureKey: "hero-soldier",
      label: "Soldier"
    }
  },
  {
    botId: "bot-3",
    handle: "cpu-ember",
    displayName: "Ember",
    initialRating: 1040,
    profileTone: "aggressive",
    strategyLabel: "Pressure duelist",
    skin: {
      avatarKey: "brown",
      textureKey: "hero-brown",
      label: "Brown jacket"
    }
  },
  {
    botId: "bot-4",
    handle: "cpu-orbit",
    displayName: "Orbit",
    initialRating: 1025,
    profileTone: "patient",
    strategyLabel: "Mid-range kiter",
    skin: {
      avatarKey: "old",
      textureKey: "hero-old",
      label: "Veteran"
    }
  },
  {
    botId: "bot-5",
    handle: "cpu-nova",
    displayName: "Nova",
    initialRating: 980,
    profileTone: "opportunist",
    strategyLabel: "Pickup chaser",
    skin: {
      avatarKey: "woman",
      textureKey: "hero-woman",
      label: "Runner"
    }
  }
] as const;

export const BOT_REGISTRY: readonly BotProfile[] = LOCAL_BOT_REGISTRY;

let botRegistryCache: readonly BotProfile[] = LOCAL_BOT_REGISTRY;
let botRegistryRefreshPromise: Promise<void> | null = null;

export function listBotProfiles(): readonly BotProfile[] {
  return botRegistryCache;
}

export function getBotProfileById(botId: string): BotProfile | undefined {
  return botRegistryCache.find((profile) => profile.botId === botId);
}

export function getBotProfileBySlot(slotIndex: number): BotProfile | undefined {
  return botRegistryCache[slotIndex];
}

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
    skin: {
      avatarKey: profile.skin.avatarKey,
      textureKey: profile.skin.textureKey,
      label: profile.skin.label
    }
  };
}
