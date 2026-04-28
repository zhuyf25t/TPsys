import type {
  BattleCommandDto,
  BattleEventDto,
  BattleEventKindDto,
  BattlePhaseDto,
  BattlePickupViewDto,
  BattleProjectileViewDto,
  BattleSessionResultDto,
  BattleSessionResultOutcomeDto,
  BattleSessionId,
  BattleSnapshotDto,
  BattleTeamDto,
  BattleWeaponKindDto,
  BattleWeaponViewDto,
  BattleWorldObstacleDto,
  BattleHudViewDto,
  BattleHudMinimapDto,
  BattleHudMinimapDotDto,
  BattleHudMinimapRectDto
} from "../../../contracts/battle";
import type {
  GameEvent,
  GameSnapshot,
  Hero,
  ItemPickup,
  PlayerCommand,
  Projectile,
  SkillState,
  Vec2,
  WeaponPickup,
  WeaponState
} from "../../../domain/types";
import type { HudState } from "../../../ui/Hud";
import { getItemPickupDisplayLabel, getWeaponDisplayLabel } from "../presenters/battleDisplayCatalog";

export interface LocalBattleCommandAdapterInput {
  sessionId: BattleSessionId;
  playerId: string;
  tick: number;
  command: PlayerCommand;
  preparedSkill?: "Blink" | "Freeze" | null;
}

export interface LocalBattleSnapshotAdapterInput {
  sessionId: BattleSessionId;
  phase: BattlePhaseDto;
  snapshot: GameSnapshot;
  worldObstacles?: BattleWorldObstacleDto[];
  eventLifetimeMs?: number;
}

export interface LocalBattleResultAdapterInput {
  sessionId: BattleSessionId;
  playerHeroId: string;
  score: number;
  kills: number;
  deaths: number;
  placement: number | null;
  ratingDelta: number | null;
  earnedMailIds: string[];
  finishedAtMs: number;
  replayId?: string | null;
  outcome?: BattleSessionResultOutcomeDto;
}

export function toBattleCommandDto(input: LocalBattleCommandAdapterInput): BattleCommandDto {
  return {
    sessionId: input.sessionId,
    playerId: input.playerId,
    tick: input.tick,
    movement: toVec2Dto(input.command.movement),
    aim: toVec2Dto(input.command.aim),
    pointerWorld: toVec2Dto(input.command.pointerWorld),
    primaryHeld: input.command.primaryHeld,
    primaryJustPressed: input.command.primaryJustPressed,
    secondaryJustPressed: input.command.secondaryJustPressed,
    sprint: input.command.sprint,
    switchWeaponDirection: input.command.switchWeaponDirection,
    preparedSkill: input.preparedSkill ?? null,
    castBlink: input.command.toggleBlink,
    castDash: input.command.castDash,
    reloadPressed: input.command.reloadPressed
  };
}

export function toBattleSnapshotDto(input: LocalBattleSnapshotAdapterInput): BattleSnapshotDto {
  return {
    sessionId: input.sessionId,
    phase: input.phase,
    elapsedMs: input.snapshot.elapsedMs,
    world: {
      width: input.snapshot.worldSize.x,
      height: input.snapshot.worldSize.y,
      obstacles: input.worldObstacles ?? []
    },
    heroes: input.snapshot.heroes.map((hero) => toBattleHeroViewDto(hero)),
    projectiles: input.snapshot.projectiles.map((projectile) => toBattleProjectileViewDto(projectile)),
    pickups: [
      ...input.snapshot.weaponPickups.map((pickup) => toBattlePickupViewDto(pickup)),
      ...input.snapshot.itemPickups.map((pickup) => toBattleItemPickupViewDto(pickup))
    ],
    events: input.snapshot.events.map((event) => toBattleEventDto(event, input.snapshot.elapsedMs, input.eventLifetimeMs)),
    localPlayerHeroId: input.snapshot.playerHeroId
  };
}

export function toBattleHudViewDto(state: HudState): BattleHudViewDto {
  return {
    timerText: state.timer,
    fps: state.fps,
    score: state.score,
    playerName: state.playerName,
    hp: state.hp,
    maxHp: state.maxHp,
    stamina: state.stamina,
    maxStamina: state.maxStamina,
    currentWeaponName: state.currentWeaponName,
    currentWeaponAmmoText: state.currentWeaponAmmo,
    currentWeaponStateText: state.currentWeaponState,
    pickupHintText: state.pickupHint,
    weaponEntries: state.weaponEntries.map((entry) => ({
      label: entry.label,
      current: entry.current,
      warning: entry.warning
    })),
    skillEntries: state.skillEntries.map((entry) => ({
      key: entry.key,
      name: entry.name,
      state: entry.state,
      ready: entry.ready,
      prepared: entry.prepared
    })),
    leaderboard: state.leaderboard.map((entry) => ({
      rank: entry.rank,
      name: entry.name,
      score: entry.score,
      current: entry.current,
      alive: entry.alive
    })),
    feed: state.feed.map((entry) => ({
      message: entry.message,
      tone: entry.tone,
      alpha: entry.alpha
    })),
    minimap: toBattleHudMinimapDto(state.minimap),
    debugLines: [...state.debugLines]
  };
}

export function toBattleSessionResultDto(input: LocalBattleResultAdapterInput): BattleSessionResultDto {
  return {
    sessionId: input.sessionId,
    replayId: input.replayId ?? null,
    outcome: input.outcome ?? "finished",
    playerHeroId: input.playerHeroId,
    score: input.score,
    kills: input.kills,
    deaths: input.deaths,
    placement: input.placement,
    ratingDelta: input.ratingDelta,
    earnedMailIds: [...input.earnedMailIds],
    finishedAtMs: input.finishedAtMs
  };
}

function toBattleHeroViewDto(hero: Hero) {
  return {
    heroId: hero.heroId,
    displayName: hero.displayName,
    team: toBattleTeamDto(hero.team),
    hp: hero.hp,
    maxHp: hero.maxHp,
    stamina: hero.stamina,
    maxStamina: hero.maxStamina,
    position: toVec2Dto(hero.position),
    facing: hero.facing,
    radius: hero.radius,
    lifeState: hero.lifeState,
    score: hero.score,
    currentWeaponIndex: hero.currentWeaponIndex,
    weapons: hero.weapons.map((weapon) => toBattleWeaponViewDto(weapon)),
    skills: hero.skills.map((skill) => toBattleSkillViewDto(skill)),
    preparedSkill: hero.preparedSkill,
    velocity: toVec2Dto(hero.velocity),
    respawnMs: hero.respawnMs,
    jumpCooldownMs: hero.jumpCooldownMs
  };
}

function toBattleWeaponViewDto(weapon: WeaponState): BattleWeaponViewDto {
  const usesHeat = weapon.weaponKind === "Gatling";

  return {
    weaponKind: toBattleWeaponKindDto(weapon.weaponKind),
    ammoInMagazine: usesHeat ? null : weapon.ammoInMagazine,
    reserveAmmo: usesHeat ? null : weapon.reserveAmmo,
    heat: usesHeat ? weapon.heat : null,
    overheated: weapon.overheated,
    cooldownRemaining: weapon.cooldownRemaining,
    reloadRemaining: weapon.reloadRemaining
  };
}

function toBattleSkillViewDto(skill: SkillState) {
  return {
    kind: skill.kind,
    cooldownMs: skill.cooldownMs,
    activeMs: skill.activeMs
  };
}

function toBattleProjectileViewDto(projectile: Projectile): BattleProjectileViewDto {
  return {
    projectileId: projectile.projectileId,
    kind: projectile.kind,
    ownerHeroId: projectile.ownerHeroId,
    position: toVec2Dto(projectile.position),
    velocity: toVec2Dto(projectile.velocity),
    facing: projectile.facing,
    radius: projectile.radius,
    damage: projectile.damage,
    splashRadius: projectile.splashRadius
  };
}

function toBattlePickupViewDto(pickup: WeaponPickup): BattlePickupViewDto {
  return {
    pickupId: pickup.weaponId,
    kind: "weapon",
    label: labelForWeaponKind(pickup.weaponKind),
    position: toVec2Dto(pickup.position),
    available: pickup.available
  };
}

function toBattleItemPickupViewDto(pickup: ItemPickup): BattlePickupViewDto {
  return {
    pickupId: pickup.pickupId,
    kind: "medkit",
    label: labelForItemPickupKind(pickup.kind),
    position: toVec2Dto(pickup.position),
    available: pickup.available
  };
}

function toBattleEventDto(event: GameEvent, elapsedMs: number, eventLifetimeMs: number | undefined): BattleEventDto {
  const ttl = eventLifetimeMs ?? 3000;
  return {
    eventId: event.eventId,
    kind: mapEventKind(event.type),
    message: event.message,
    createdAtMs: Math.max(0, elapsedMs - Math.max(0, ttl - event.ttlMs))
  };
}

function toBattleHudMinimapDto(minimap: HudState["minimap"]): BattleHudMinimapDto {
  return {
    worldWidth: minimap.worldWidth,
    worldHeight: minimap.worldHeight,
    cameraRect: toBattleHudRectDto(minimap.cameraRect),
    obstacles: minimap.obstacles.map((rect) => toBattleHudRectDto(rect)),
    pickups: minimap.pickups.map((dot) => toBattleHudDotDto(dot)),
    heroes: minimap.heroes.map((dot) => toBattleHudDotDto(dot))
  };
}

function toBattleHudRectDto(rect: HudState["minimap"]["cameraRect"]): BattleHudMinimapRectDto {
  return {
    x: rect.x,
    y: rect.y,
    width: rect.width,
    height: rect.height
  };
}

function toBattleHudDotDto(dot: HudState["minimap"]["pickups"][number]): BattleHudMinimapDotDto {
  return {
    x: dot.x,
    y: dot.y,
    radius: dot.radius,
    color: dot.color
  };
}

function toVec2Dto(vec: Vec2) {
  return {
    x: vec.x,
    y: vec.y
  };
}

function toBattleTeamDto(team: Hero["team"]): BattleTeamDto {
  return team;
}

function toBattleWeaponKindDto(kind: WeaponState["weaponKind"]): BattleWeaponKindDto {
  return kind;
}

function mapEventKind(kind: GameEvent["type"]): BattleEventKindDto {
  return kind;
}

function labelForWeaponKind(weaponKind: WeaponPickup["weaponKind"]): string {
  return getWeaponDisplayLabel(weaponKind);
}

function labelForItemPickupKind(kind: ItemPickup["kind"]): string {
  return getItemPickupDisplayLabel(kind);
}
