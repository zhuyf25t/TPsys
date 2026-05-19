import type { Hero } from "../../../battle/objects/types";
import { getEngageRange, isWeaponScarce, type BotBrainState, type BotTarget } from "./botTargeting";

export interface BotTacticState {
  enemyPressure: boolean;
  enemyClose: boolean;
  shouldRetreat: boolean;
  shouldStrafe: boolean;
  shouldContestPickup: boolean;
  fireDistanceFactor: number;
  pursuitWeight: number;
  retreatWeight: number;
  strafeWeight: number;
  supportWeight: number;
}

/** 中文名：解析机器人tactic（resolveBotTactic）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function resolveBotTactic(input: {
  bot: Hero;
  weapon: Hero["weapons"][number];
  enemyTarget: BotTarget | null;
  pickupTarget: BotTarget | null;
  brain: BotBrainState;
  elapsedMs: number;
}): BotTacticState {
  const engageRange = getEngageRange(input.weapon.weaponKind);
  const enemyDistance = input.enemyTarget?.distance ?? Number.POSITIVE_INFINITY;
  const enemyPressure = Boolean(input.enemyTarget && enemyDistance <= engageRange * 1.35);
  const enemyClose = Boolean(input.enemyTarget && enemyDistance <= engageRange * 0.82);
  const lowHp = input.bot.hp <= input.bot.maxHp * 0.62;
  const criticalHp = input.bot.hp <= input.bot.maxHp * 0.36;
  const overheated = input.weapon.weaponKind === "Gatling" && input.weapon.overheated;
  const heatHigh = input.weapon.weaponKind === "Gatling" && input.weapon.heat >= 74;
  const needsRecovery = lowHp || overheated || heatHigh;
  const needsAmmo = input.weapon.weaponKind !== "Gatling" && isWeaponScarce(input.weapon);
  const shouldRetreat = Boolean(
    input.enemyTarget &&
      (input.elapsedMs < input.brain.stuckUntilMs || (criticalHp && enemyPressure) || (overheated && enemyClose))
  );
  const shouldStrafe = Boolean(input.enemyTarget && enemyPressure && !shouldRetreat);
  const shouldContestPickup = Boolean(
    input.pickupTarget &&
      (!enemyClose || criticalHp) &&
      (needsRecovery || needsAmmo || input.elapsedMs < input.brain.stuckUntilMs)
  );

  let fireDistanceFactor = 1.2;
  if (input.weapon.weaponKind === "Gatling") {
    fireDistanceFactor = shouldRetreat ? 1.46 : enemyClose ? 1.34 : enemyPressure ? 1.26 : 1.18;
  } else if (shouldRetreat) {
    fireDistanceFactor = 1.24;
  } else if (enemyClose) {
    fireDistanceFactor = 1.28;
  } else if (enemyPressure) {
    fireDistanceFactor = 1.22;
  }

  const pursuitWeight = enemyPressure ? 1.12 : 0.92;
  const retreatWeight = shouldRetreat ? 1.12 : needsRecovery ? 0.42 : 0.2;
  const strafeWeight = shouldStrafe ? 0.88 : enemyPressure ? 0.56 : 0.12;
  const supportWeight = shouldContestPickup ? 1.18 : needsRecovery || needsAmmo ? 0.72 : input.pickupTarget ? 0.32 : 0.18;

  return {
    enemyPressure,
    enemyClose,
    shouldRetreat,
    shouldStrafe,
    shouldContestPickup,
    fireDistanceFactor,
    pursuitWeight,
    retreatWeight,
    strafeWeight,
    supportWeight
  };
}
