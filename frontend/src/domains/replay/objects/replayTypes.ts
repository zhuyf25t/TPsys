import type {
  HeroLifeState,
  ProjectileKind,
  Vec2,
  WeaponKind
} from "../../battle/objects/types";

export interface ReplayHeroFrame {
  heroId: string;
  displayName: string;
  position: Vec2;
  hp: number;
  maxHp: number;
  alive: boolean;
  lifeState: HeroLifeState;
  score: number;
  facing: number;
  currentWeaponKind: WeaponKind | null;
  eliminatedAtMs: number | null;
}

export interface ReplayProjectileFrame {
  projectileId: string;
  kind: ProjectileKind;
  position: Vec2;
  facing: number;
  alive: boolean;
  ttlMs: number;
  splashRadius: number;
}

export interface ReplayPickupFrame {
  id: string;
  kind: "weapon" | "medkit";
  position: Vec2;
  available: boolean;
}

export interface ReplayFrame {
  elapsedMs: number;
  worldSize: Vec2;
  heroes: ReplayHeroFrame[];
  projectiles: ReplayProjectileFrame[];
  pickups: ReplayPickupFrame[];
  eventMessages: string[];
}

export interface ReplayPlayback {
  id: string;
  playbackAvailable?: boolean;
  title: string;
  modeLabel: string;
  resultLabel: string;
  finishedAtLabel: string;
  mapLabel: string;
  highlightLine: string;
  timelineHint: string;
  playersLine: string;
  score: number;
  placement: number | null;
  ratingBefore: number | null;
  ratingAfter: number | null;
  ratingDelta: number | null;
  durationMs: number;
  aliveAtEnd: boolean;
  thumbnailDataUrl: string | null;
  frames: ReplayFrame[];
}

export interface ReplayExportArtifact {
  filename: string;
  json: string;
}
