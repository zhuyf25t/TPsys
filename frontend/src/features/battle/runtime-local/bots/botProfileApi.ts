export interface RemoteBotSkinProfile {
  avatarKey: string;
  textureKey: string;
  label: string;
}

export interface RemoteBotProfile {
  botId: string;
  handle: string;
  displayName: string;
  initialRating: number;
  profileTone: string;
  strategyLabel: string;
  skin: RemoteBotSkinProfile;
}

interface RemoteBotProfilesResponse {
  profiles?: unknown;
}

export async function fetchBotProfiles(): Promise<RemoteBotProfile[] | null> {
  try {
    const response = await fetch("/api/bots/profiles", {
      headers: {
        Accept: "application/json"
      }
    });

    if (!response.ok) {
      return null;
    }

    const payload = (await response.json()) as RemoteBotProfilesResponse;
    const profiles = Array.isArray(payload.profiles) ? payload.profiles : null;
    if (!profiles) {
      return null;
    }

    const normalizedProfiles = profiles.map(normalizeBotProfile).filter((profile): profile is RemoteBotProfile => profile !== null);
    return normalizedProfiles.length === profiles.length ? normalizedProfiles : null;
  } catch {
    return null;
  }
}

function normalizeBotProfile(value: unknown): RemoteBotProfile | null {
  if (!isRecord(value)) {
    return null;
  }

  const skin = isRecord(value.skin) ? value.skin : null;
  if (!skin) {
    return null;
  }

  const botId = readString(value.botId);
  const handle = readString(value.handle);
  const displayName = readString(value.displayName);
  const profileTone = readString(value.profileTone);
  const strategyLabel = readString(value.strategyLabel);
  const initialRating = readNumber(value.initialRating);
  const avatarKey = readString(skin.avatarKey);
  const textureKey = readString(skin.textureKey);
  const label = readString(skin.label);

  if (
    !botId ||
    !handle ||
    !displayName ||
    !profileTone ||
    !strategyLabel ||
    initialRating === null ||
    !avatarKey ||
    !textureKey ||
    !label
  ) {
    return null;
  }

  return {
    botId,
    handle,
    displayName,
    initialRating,
    profileTone,
    strategyLabel,
    skin: {
      avatarKey,
      textureKey,
      label
    }
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim().length > 0 ? value : null;
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
