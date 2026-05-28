export type BotId = string;
export type BotInitialRating = number;
export type BotStrategyLabel = string;
export type BotAvatarKey = string;
export type BotTextureKey = string;
export type BotSkinLabel = string;

export type BotProfileToneDto =
  | "steady"
  | "scrappy"
  | "aggressive"
  | "patient"
  | "opportunist";

export interface BotSkinProfileResponseDto {
  avatarKey: BotAvatarKey;
  textureKey: BotTextureKey;
  label: BotSkinLabel;
}

export interface BotProfileResponseDto {
  botId: BotId;
  handle: string;
  displayName: string;
  initialRating: BotInitialRating;
  profileTone: BotProfileToneDto;
  strategyLabel: BotStrategyLabel;
  skin: BotSkinProfileResponseDto;
}

export interface BotProfilesResponseDto {
  profiles: BotProfileResponseDto[];
}
