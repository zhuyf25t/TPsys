export interface BattleHudLeaderboardEntryDto {
  rank: number;
  name: string;
  score: number;
  current: boolean;
  alive: boolean;
}

export interface BattleHudFeedEntryDto {
  message: string;
  tone: "kill" | "pickup" | "heal" | "respawn" | "info";
  alpha: number;
}

export interface BattleHudWeaponEntryDto {
  label: string;
  current: boolean;
  warning: boolean;
}

export interface BattleHudSkillEntryDto {
  key: string;
  name: string;
  state: string;
  ready: boolean;
  prepared: boolean;
}

export interface BattleHudMinimapRectDto {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface BattleHudMinimapDotDto {
  x: number;
  y: number;
  radius: number;
  color: string;
}

export interface BattleHudMinimapDto {
  worldWidth: number;
  worldHeight: number;
  cameraRect: BattleHudMinimapRectDto;
  obstacles: BattleHudMinimapRectDto[];
  pickups: BattleHudMinimapDotDto[];
  heroes: BattleHudMinimapDotDto[];
}

export interface BattleHudViewDto {
  timerText: string;
  fps: number;
  score: number;
  playerName: string;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  currentWeaponName: string;
  currentWeaponAmmoText: string;
  currentWeaponStateText: string;
  pickupHintText: string;
  weaponEntries: BattleHudWeaponEntryDto[];
  skillEntries: BattleHudSkillEntryDto[];
  leaderboard: BattleHudLeaderboardEntryDto[];
  feed: BattleHudFeedEntryDto[];
  minimap: BattleHudMinimapDto;
  debugLines: string[];
}
