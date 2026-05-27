import type { GameSnapshot, Hero, ItemPickup, Vec2, WeaponPickup } from "../../../../../objects/battle/types";

export interface HeroFeedbackState {
  hp: number;
  alive: boolean;
  score: number;
  currentWeaponAmmoTotal: number | null;
  position: Vec2;
}

export interface PickupFeedbackState {
  available: boolean;
  position: Vec2;
}

const AMMO_PICKUP_PULSE_RADIUS = 30;
const AMMO_PICKUP_PULSE_COLOR = 0xffd86d;

interface HeroFeedbackPresentationOptions {
  snapshot: GameSnapshot;
  previousHeroStates: ReadonlyMap<string, HeroFeedbackState>;
  sharedAuthoritativeRuntime: boolean;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  flashHero(heroId: string, color: number): void;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "success" | "warning" | "error"): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  createHitConfirm(position: Vec2, color: number): void;
  shakeCamera(duration: number, intensity: number): void;
}

interface AuthoritativePickupFeedbackPresentationOptions {
  snapshot: GameSnapshot;
  previousWeaponPickupStates: ReadonlyMap<string, PickupFeedbackState>;
  previousItemPickupStates: ReadonlyMap<string, PickupFeedbackState>;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "success" | "warning" | "error"): void;
  createPulse(position: Vec2, radius: number, color: number): void;
}

interface AuthoritativeHealthDeltaPresentationOptions {
  hero: Hero;
  previous: HeroFeedbackState;
  playerHeroId: string;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  flashHero(heroId: string, color: number): void;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "success" | "warning" | "error"): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  createHitConfirm(position: Vec2, color: number): void;
  shakeCamera(duration: number, intensity: number): void;
}

interface AuthoritativeAmmoDeltaPresentationOptions {
  hero: Hero;
  previous: HeroFeedbackState;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "success" | "warning" | "error"): void;
  createPulse(position: Vec2, radius: number, color: number): void;
}

/** 中文名：present英雄feedback（presentHeroFeedback）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function presentHeroFeedback({
  snapshot,
  previousHeroStates,
  sharedAuthoritativeRuntime,
  getHeroDisplayPosition,
  flashHero,
  showFloatingText,
  createPulse,
  createImpactSpark,
  createHitConfirm,
  shakeCamera
}: HeroFeedbackPresentationOptions): void {
  snapshot.heroes.forEach((hero) => {
    const previous = previousHeroStates.get(hero.heroId);
    if (!previous) {
      return;
    }

    if (sharedAuthoritativeRuntime) {
      presentAuthoritativeHealthDelta({
        hero,
        previous,
        playerHeroId: snapshot.playerHeroId,
        getHeroDisplayPosition,
        flashHero,
        showFloatingText,
        createPulse,
        createImpactSpark,
        createHitConfirm,
        shakeCamera
      });
      presentAuthoritativeAmmoDelta({
        hero,
        previous,
        getHeroDisplayPosition,
        showFloatingText,
        createPulse
      });
    }

    if (previous.alive && !hero.alive) {
      showFloatingText(previous.position, "出局", "error");
      createPulse(previous.position, 42, 0xff6b6b);
      if (hero.heroId === snapshot.playerHeroId) {
        shakeCamera(140, 0.0024);
      }
    }

    if (!sharedAuthoritativeRuntime && !previous.alive && hero.alive) {
      const feedbackPosition = getHeroDisplayPosition(hero.heroId) ?? hero.position;
      // Legacy snapshot compatibility: one-life mode must not present this as player return.
      createPulse(feedbackPosition, 42, 0xffb36f);
    }

    if (hero.heroId === snapshot.playerHeroId && hero.score > previous.score) {
      const feedbackPosition = getHeroDisplayPosition(hero.heroId) ?? hero.position;
      showFloatingText(feedbackPosition, `击败 +${hero.score - previous.score}`, "success");
    }
  });
}

/** 中文名：presentauthoritative拾取物feedback（presentAuthoritativePickupFeedback）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function presentAuthoritativePickupFeedback({
  snapshot,
  previousWeaponPickupStates,
  previousItemPickupStates,
  showFloatingText,
  createPulse
}: AuthoritativePickupFeedbackPresentationOptions): void {
  snapshot.weaponPickups.forEach((pickup) => {
    const previous = previousWeaponPickupStates.get(pickup.weaponId);
    if (previous?.available && !pickup.available) {
      showFloatingText(previous.position, "拾取武器", "success");
      createPulse(previous.position, 34, 0x9dffb4);
    }
  });

  snapshot.itemPickups.forEach((pickup) => {
    const previous = previousItemPickupStates.get(pickup.pickupId);
    if (previous?.available && !pickup.available) {
      showFloatingText(previous.position, "拾取补给", "success");
      createPulse(previous.position, 34, 0x7dff9d);
    }
  });
}

/** 中文名：创建英雄feedback状态（createHeroFeedbackState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createHeroFeedbackState(hero: Hero, position: Vec2): HeroFeedbackState {
  return {
    hp: hero.hp,
    alive: hero.alive,
    score: hero.score,
    currentWeaponAmmoTotal: resolveCurrentWeaponAmmoTotal(hero),
    position: { x: position.x, y: position.y }
  };
}

/** 中文名：创建武器拾取物feedback状态（createWeaponPickupFeedbackState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createWeaponPickupFeedbackState(pickup: WeaponPickup): PickupFeedbackState {
  return {
    available: pickup.available,
    position: { x: pickup.position.x, y: pickup.position.y }
  };
}

/** 中文名：创建item拾取物feedback状态（createItemPickupFeedbackState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createItemPickupFeedbackState(pickup: ItemPickup): PickupFeedbackState {
  return {
    available: pickup.available,
    position: { x: pickup.position.x, y: pickup.position.y }
  };
}

function presentAuthoritativeHealthDelta({
  hero,
  previous,
  playerHeroId,
  getHeroDisplayPosition,
  flashHero,
  showFloatingText,
  createPulse,
  createImpactSpark,
  createHitConfirm,
  shakeCamera
}: AuthoritativeHealthDeltaPresentationOptions): void {
  if (!hero.alive && !previous.alive) {
    return;
  }

  const hpDelta = hero.hp - previous.hp;
  const displayPosition = getHeroDisplayPosition(hero.heroId);
  const feedbackPosition = displayPosition ?? (previous.alive && !hero.alive ? previous.position : hero.position);
  if (hpDelta < 0) {
    const damage = Math.round(Math.abs(hpDelta));
    const isPlayerDamage = hero.heroId === playerHeroId;
    flashHero(hero.heroId, 0xffffff);
    createImpactSpark(feedbackPosition, 0xffe2ba);
    createHitConfirm(feedbackPosition, isPlayerDamage ? 0xff6b6b : 0xfff0c6);
    showFloatingText(feedbackPosition, `-${damage}`, "error");
    if (isPlayerDamage && previous.alive && hero.alive) {
      const shakeScale = Math.min(1, damage / Math.max(1, hero.maxHp * 0.35));
      createPulse(feedbackPosition, 28, 0xff5c5c);
      shakeCamera(70 + Math.round(shakeScale * 40), 0.0012 + shakeScale * 0.0008);
    }
    return;
  }

  if (hpDelta > 0 && hero.alive) {
    showFloatingText(feedbackPosition, `+${Math.round(hpDelta)}`, "success");
    createPulse(feedbackPosition, 36, 0x7dff9d);
  }
}

function presentAuthoritativeAmmoDelta({
  hero,
  previous,
  getHeroDisplayPosition,
  showFloatingText,
  createPulse
}: AuthoritativeAmmoDeltaPresentationOptions): void {
  if (!previous.alive || !hero.alive) {
    return;
  }

  const currentWeaponAmmoTotal = resolveCurrentWeaponAmmoTotal(hero);
  if (currentWeaponAmmoTotal === null || previous.currentWeaponAmmoTotal === null) {
    return;
  }

  const ammoDelta = currentWeaponAmmoTotal - previous.currentWeaponAmmoTotal;
  if (ammoDelta <= 0) {
    return;
  }

  const feedbackPosition = getHeroDisplayPosition(hero.heroId) ?? hero.position;
  showFloatingText(feedbackPosition, `弹药 +${ammoDelta}`, "success");
  createPulse(feedbackPosition, AMMO_PICKUP_PULSE_RADIUS, AMMO_PICKUP_PULSE_COLOR);
}

function resolveCurrentWeaponAmmoTotal(hero: Hero): number | null {
  const weapon = hero.weapons[hero.currentWeaponIndex];
  if (!weapon) {
    return null;
  }

  return toSafeAmmoCount(weapon.ammoInMagazine) + toSafeAmmoCount(weapon.reserveAmmo);
}

function toSafeAmmoCount(value: number | null | undefined): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0;
}
