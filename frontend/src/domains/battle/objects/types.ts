export interface Vec2 {
  x: number;
  y: number;
}

export type TeamMode = "FreeForAll";
export type HeroLifeState = "alive" | "dead" | "respawning";
export type WeaponKind = "Pistol" | "RocketLauncher" | "Gatling" | "Shotgun";
export type ProjectileKind = "pistol-bullet" | "rocket" | "gatling-bullet" | "shotgun-pellet";
export type SkillKind = "Blink" | "Dash" | "Freeze";
export type PreparedSkill = "Blink" | "Freeze" | null;
export type ItemPickupKind = "Medkit";

export interface WeaponState {
  weaponKind: WeaponKind;
  ammoInMagazine: number;
  magazineSize: number;
  reserveAmmo: number | null;
  cooldownRemaining: number;
  reloadRemaining: number;
  heat: number;
  overheated: boolean;
  overheatRemaining: number;
}

export interface WeaponInventory {
  currentWeaponIndex: number;
  weapons: WeaponState[];
}

export interface SkillState {
  kind: SkillKind;
  cooldownMs: number;
  activeMs: number;
}

export interface Hero {
  heroId: string;
  displayName: string;
  team: TeamMode;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  position: Vec2;
  facing: number;
  radius: number;
  alive: boolean;
  lifeState: HeroLifeState;
  score: number;
  currentWeaponIndex: number;
  weapons: WeaponState[];
  skills: SkillState[];
  preparedSkill: PreparedSkill;
  velocity: Vec2;
  respawnMs: number;
  jumpCooldownMs: number;
  eliminatedAtMs: number | null;
}

export interface WeaponPickup {
  weaponId: string;
  weaponKind: WeaponKind;
  position: Vec2;
  available: boolean;
  respawnMs: number;
}

export interface PickupSpawnPoint {
  id: string;
  kind: "weapon" | "medkit";
  position: Vec2;
  occupied: boolean;
}

export interface ItemPickup {
  pickupId: string;
  kind: ItemPickupKind;
  position: Vec2;
  available: boolean;
  respawnMs: number;
}

export interface Projectile {
  projectileId: string;
  kind: ProjectileKind;
  ownerHeroId: string;
  team: TeamMode;
  position: Vec2;
  velocity: Vec2;
  facing: number;
  radius: number;
  damage: number;
  ttlMs: number;
  maxLifetimeMs: number;
  splashRadius: number;
  alive: boolean;
  hitTargets: string[];
}

export interface SlowField {
  fieldId: string;
  ownerHeroId: string;
  position: Vec2;
  radius: number;
  ttlMs: number;
  durationMs: number;
}

export interface GameEvent {
  eventId: string;
  type: "kill" | "heal" | "pickup" | "respawn" | "jump" | "switch";
  message: string;
  ttlMs: number;
}

export interface GameSnapshot {
  heroes: Hero[];
  projectiles: Projectile[];
  slowFields: SlowField[];
  weaponPickups: WeaponPickup[];
  itemPickups: ItemPickup[];
  events: GameEvent[];
  worldSize: Vec2;
  elapsedMs: number;
  playerHeroId: string;
}

export interface PlayerCommand {
  movement: Vec2;
  aim: Vec2;
  pointerWorld: Vec2;
  primaryHeld: boolean;
  primaryJustPressed: boolean;
  secondaryJustPressed: boolean;
  sprint: boolean;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex: number | null;
  toggleBlink: boolean;
  toggleFreeze: boolean;
  castDash: boolean;
  reloadPressed: boolean;
}
