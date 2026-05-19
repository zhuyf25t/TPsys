export interface CombatDebugSnapshot {
  wheelPhaserReceived: boolean;
  wheelWindowReceived: boolean;
  wheelSource: string;
  wheelDeltaY: number;
  combatDebugLine: string;
}

export interface CombatDebugReporterOptions {
  enabled: boolean;
}

export interface CombatDebugHitInput {
  projectileId: string;
  projectileKind: string;
  projectileLabel: string;
  targetDisplayName: string;
  damage: number;
  hpBefore: number;
  hpAfter: number;
}

export interface CombatDebugLinesInput {
  currentWeaponIndex: number;
  currentWeaponLabel: string;
  wheelPhaserReceived: boolean;
  wheelWindowReceived: boolean;
  wheelSource: string;
  wheelDeltaY: number;
  combatDebugLine: string;
}

export class CombatDebugReporter {
  private lastCombatDebug = "最近命中：暂无";
  private lastWheelSource = "未收到";
  private lastWheelDeltaY = 0;
  private phaserWheelReceived = false;
  private windowWheelReceived = false;

  public constructor(private readonly options: CombatDebugReporterOptions) {}

  public reportHit(input: CombatDebugHitInput): void {
    this.lastCombatDebug = `最近命中：${input.projectileLabel} -> ${input.targetDisplayName} -> ${input.damage}`;
    if (this.options.enabled) {
      console.log(
        `[HIT] projectile=${input.projectileId} kind=${input.projectileKind} target=${input.targetDisplayName} damage=${input.damage} hpBefore=${input.hpBefore} hpAfter=${input.hpAfter}`
      );
    }
  }

  public reportNoDamage(reason: string): void {
    this.lastCombatDebug = `最近命中失败：${reason}`;
    if (this.options.enabled) {
      console.log(`[NO_DAMAGE] reason=${reason}`);
    }
  }

  public reportWheelSample(source: "Phaser" | "Window", deltaY: number): void {
    if (source === "Phaser") {
      this.phaserWheelReceived = true;
    } else {
      this.windowWheelReceived = true;
    }

    this.lastWheelSource = source;
    this.lastWheelDeltaY = deltaY;

    if (this.options.enabled) {
      console.log(`[WHEEL][${source.toUpperCase()}]`, deltaY);
    }
  }

  public reportWeaponSwitch(input: { source: "Phaser" | "Window"; deltaY: number; nextIndex: number; weapon: string }): void {
    if (this.options.enabled) {
      console.log("[WHEEL][SWITCH]", input);
    }
  }

  public getSnapshot(): CombatDebugSnapshot {
    return {
      wheelPhaserReceived: this.phaserWheelReceived,
      wheelWindowReceived: this.windowWheelReceived,
      wheelSource: this.lastWheelSource,
      wheelDeltaY: this.lastWheelDeltaY,
      combatDebugLine: this.lastCombatDebug
    };
  }

  public buildDebugLines(input: CombatDebugLinesInput): string[] {
    return buildCombatDebugLines(input);
  }
}

/** 中文名：构建combatdebuglines（buildCombatDebugLines）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function buildCombatDebugLines(input: CombatDebugLinesInput): string[] {
  return [
    `滚轮事件：${input.wheelPhaserReceived ? "Phaser已收到" : "Phaser未收到"} · ${
      input.wheelWindowReceived ? "Window已收到" : "Window未收到"
    }`,
    `当前滚轮：${input.wheelSource} · deltaY ${input.wheelDeltaY}`,
    `当前武器索引：${input.currentWeaponIndex} · 当前武器：${input.currentWeaponLabel}`,
    input.combatDebugLine
  ];
}
