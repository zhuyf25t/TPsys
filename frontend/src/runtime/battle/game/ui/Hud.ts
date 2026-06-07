export interface HudLeaderboardEntry {
  rank: number;
  name: string;
  score: number;
  current: boolean;
  alive: boolean;
}

export interface HudFeedEntry {
  message: string;
  tone: "kill" | "pickup" | "heal" | "respawn" | "info";
  alpha: number;
}

export interface HudWeaponEntry {
  label: string;
  current: boolean;
  warning: boolean;
  tone: "pistol" | "rocket" | "gatling" | "shotgun";
}

export interface HudSkillEntry {
  key: string;
  icon: string;
  name: string;
  state: string;
  ready: boolean;
  prepared: boolean;
  cooldownProgress: number;
  activeProgress: number;
}

export interface HudStatusEntry {
  label: string;
  tone: "danger" | "warning" | "success" | "info";
}

export interface HudMinimapRect {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface HudMinimapDot {
  x: number;
  y: number;
  radius: number;
  color: string;
}

export interface HudMinimapGasZone {
  x: number;
  y: number;
  radius: number;
  nextRadius: number;
  phase: "waiting" | "advancing" | "final";
}

export interface HudMinimapData {
  worldWidth: number;
  worldHeight: number;
  cameraRect: HudMinimapRect;
  obstacles: HudMinimapRect[];
  clearanceObstacles?: HudMinimapRect[];
  centerLimitRect?: HudMinimapRect;
  staticLayerSignature?: string;
  gasZone: HudMinimapGasZone | null;
  pickups: HudMinimapDot[];
  heroes: HudMinimapDot[];
}

export interface HudState {
  timer: string;
  fps: number;
  score: number;
  playerName: string;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  currentWeaponName: string;
  currentWeaponAmmo: string;
  currentWeaponState: string;
  pickupHint: string;
  weaponEntries: HudWeaponEntry[];
  skillEntries: HudSkillEntry[];
  statusEntries: HudStatusEntry[];
  leaderboard: HudLeaderboardEntry[];
  feed: HudFeedEntry[];
  minimap: HudMinimapData;
  mapExpanded: boolean;
  debugLines: string[];
}

const HUD_STYLE_ID = "slay-demo-dom-hud-style";
const BATTLE_DIAGNOSTICS_STORAGE_KEY = "slay-demo:battle-diagnostics";
const MINIMAP_HASH_OFFSET = 2166136261;
const MINIMAP_HASH_PRIME = 16777619;
const HUD_EDGE_GAP_PX = 16;
const HUD_PANEL_GAP_PX = 18;
const HUD_WEAPON_PANEL_WIDTH_PX = 214;
const HUD_SKILL_CARD_WIDTH_PX = 70;
const HUD_SKILL_CARD_GAP_PX = 8;
const HUD_SKILL_CARD_COUNT = 3;
const HUD_SKILL_GRID_WIDTH_PX =
  HUD_SKILL_CARD_WIDTH_PX * HUD_SKILL_CARD_COUNT + HUD_SKILL_CARD_GAP_PX * (HUD_SKILL_CARD_COUNT - 1);
const HUD_SKILL_PANEL_WIDTH_PX = HUD_SKILL_GRID_WIDTH_PX + 12;
const HUD_SKILL_PANEL_RIGHT_PX = HUD_EDGE_GAP_PX + HUD_WEAPON_PANEL_WIDTH_PX + HUD_PANEL_GAP_PX;

interface HudDiagnosticsSnapshot {
  minimapRenderCount: number;
  minimapStaticLayerRedrawCount: number;
  lastObstacleCount: number;
  lastHeroCount: number;
  lastPickupCount: number;
}

interface HudDiagnosticsRoot {
  hud?: HudDiagnosticsSnapshot;
}

type HudDiagnosticsWindow = Window & {
  __slayDemoBattleDiagnostics?: HudDiagnosticsRoot;
};

const styleValueCache = new WeakMap<HTMLElement, Map<string, string>>();
const minimapHashBuffer = new ArrayBuffer(8);
const minimapHashView = new DataView(minimapHashBuffer);
let cachedHudDiagnosticsEnabled: boolean | null = null;

function ensureHudStyle(): void {
  if (document.getElementById(HUD_STYLE_ID)) {
    return;
  }

  const style = document.createElement("style");
  style.id = HUD_STYLE_ID;
  style.textContent = `
    #hud-root {
      position: fixed;
      inset: 0;
      pointer-events: none;
      z-index: 9999;
      font-family: "Segoe UI", "Microsoft YaHei", sans-serif;
      color: #eef6ff;
      --hud-gold: #f3c36a;
      --hud-gold-soft: rgba(243, 195, 106, 0.48);
      --hud-cyan: #69dff6;
      --hud-cyan-soft: rgba(105, 223, 246, 0.34);
      --hud-panel-top: rgba(33, 38, 43, 0.88);
      --hud-panel-mid: rgba(13, 16, 20, 0.86);
      --hud-panel-low: rgba(3, 5, 8, 0.9);
      --hud-edge: rgba(228, 183, 96, 0.3);
      --hud-edge-cool: rgba(112, 219, 246, 0.22);
      --hud-steel-line: rgba(255, 255, 255, 0.08);
    }

    #hud-root .hud-timer {
      position: absolute;
      top: 14px;
      left: 50%;
      transform: translateX(-50%);
      min-width: 118px;
      padding: 6px 18px;
      border: 1px solid var(--hud-gold-soft);
      border-radius: 3px;
      background:
        linear-gradient(135deg, transparent 0 8px, rgba(255, 220, 128, 0.16) 8px 9px, transparent 9px calc(100% - 9px), rgba(105, 223, 246, 0.14) calc(100% - 9px) calc(100% - 8px), transparent calc(100% - 8px)),
        linear-gradient(180deg, rgba(54, 58, 61, 0.92) 0%, rgba(12, 14, 18, 0.94) 48%, rgba(1, 3, 6, 0.95) 100%);
      color: #ffe7a3;
      font-size: 18px;
      font-weight: 800;
      font-variant-numeric: tabular-nums;
      text-align: center;
      letter-spacing: 0.08em;
      text-shadow: 0 0 8px rgba(255, 202, 103, 0.32);
      box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.12),
        inset 0 -1px 0 rgba(0, 0, 0, 0.78),
        0 0 0 1px rgba(0, 0, 0, 0.58),
        0 8px 18px rgba(0, 0, 0, 0.32);
    }

    #hud-root .hud-panel {
      position: absolute;
      pointer-events: none;
      padding: 9px 10px;
      border: 1px solid var(--hud-edge);
      border-radius: 4px;
      background:
        linear-gradient(135deg, rgba(243, 195, 106, 0.22) 0 7px, transparent 7px) top left / 100% 100% no-repeat,
        linear-gradient(315deg, rgba(105, 223, 246, 0.16) 0 7px, transparent 7px) bottom right / 100% 100% no-repeat,
        linear-gradient(180deg, var(--hud-panel-top) 0%, var(--hud-panel-mid) 42%, var(--hud-panel-low) 100%);
      box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.09),
        inset 0 -1px 0 rgba(0, 0, 0, 0.78),
        inset 1px 0 0 rgba(255, 255, 255, 0.03),
        0 0 0 1px rgba(0, 0, 0, 0.5),
        0 10px 20px rgba(0, 0, 0, 0.28);
    }

    #hud-root .hud-title {
      margin-bottom: 6px;
      color: var(--hud-gold);
      font-size: 11px;
      letter-spacing: 0.14em;
      text-transform: uppercase;
      text-shadow: 0 0 7px rgba(243, 195, 106, 0.24);
    }

    #hud-root .hud-line {
      font-size: 12px;
      line-height: 1.32;
      color: #e8eef3;
      white-space: pre-wrap;
    }

    #hud-root .hud-bar-shell {
      width: 238px;
      height: 12px;
      margin-top: 4px;
      margin-bottom: 8px;
      border-radius: 999px;
      overflow: hidden;
      background:
        linear-gradient(180deg, rgba(0, 0, 0, 0.52), rgba(31, 36, 41, 0.88)),
        rgba(18, 23, 28, 0.96);
      border: 1px solid rgba(243, 195, 106, 0.2);
      box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.9), 0 0 0 1px rgba(255, 255, 255, 0.035);
    }

    #hud-root .hud-bar-fill {
      height: 100%;
      width: 0;
      transition: width 120ms linear;
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.28), 0 0 8px rgba(255, 198, 92, 0.2);
    }

    #hud-root .hud-minimap {
      display: block;
      width: 196px;
      height: 196px;
      margin-bottom: 8px;
      border: 1px solid var(--hud-edge-cool);
      background: rgba(8, 25, 34, 0.88);
      box-shadow:
        inset 0 0 0 1px rgba(255, 255, 255, 0.035),
        0 0 0 1px rgba(0, 0, 0, 0.5);
      image-rendering: pixelated;
    }

    #hud-root .hud-expanded-map-panel {
      position: absolute;
      left: 50%;
      top: 50%;
      transform: translate(-50%, -50%);
      width: min(72vw, 760px);
      max-width: calc(100vw - 48px);
      padding: 12px;
      display: none;
      gap: 8px;
      border: 1px solid rgba(255, 209, 112, 0.54);
      border-radius: 5px;
      background:
        linear-gradient(135deg, rgba(255, 198, 92, 0.22) 0 10px, transparent 10px),
        linear-gradient(315deg, rgba(105, 223, 246, 0.18) 0 10px, transparent 10px),
        linear-gradient(180deg, rgba(22, 26, 30, 0.96), rgba(4, 8, 12, 0.94));
      box-shadow:
        inset 0 1px 0 rgba(255, 255, 255, 0.1),
        0 0 0 1px rgba(0, 0, 0, 0.58),
        0 24px 70px rgba(0, 0, 0, 0.58),
        0 0 34px rgba(255, 182, 68, 0.15);
    }

    #hud-root .hud-expanded-map-panel.visible {
      display: grid;
    }

    #hud-root .hud-expanded-map-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      color: #ffe6a4;
      font-size: 14px;
      font-weight: 800;
      letter-spacing: 0.1em;
      text-shadow: 0 0 10px rgba(255, 196, 88, 0.26);
    }

    #hud-root .hud-expanded-map-hint,
    #hud-root .hud-expanded-map-legend {
      color: #aeefff;
      font-size: 11px;
      letter-spacing: 0.04em;
    }

    #hud-root .hud-expanded-map {
      display: block;
      width: 100%;
      aspect-ratio: 1 / 1;
      border: 1px solid rgba(105, 223, 246, 0.34);
      background: rgba(8, 25, 34, 0.92);
      box-shadow:
        inset 0 0 0 1px rgba(255, 255, 255, 0.04),
        inset 0 0 28px rgba(105, 223, 246, 0.06);
      image-rendering: pixelated;
    }

    #hud-root .hud-leaderboard-list,
    #hud-root .hud-weapon-list,
    #hud-root .hud-feed-list {
      display: grid;
      gap: 4px;
    }

    #hud-root .hud-leaderboard-list {
      max-height: 132px;
      overflow: hidden;
    }

    #hud-root .hud-leaderboard-entry,
    #hud-root .hud-weapon-entry,
    #hud-root .hud-feed-entry {
      font-size: 12px;
      line-height: 1.22;
    }

    #hud-root .hud-feed-entry {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 3px 6px;
      border: 1px solid rgba(255, 255, 255, 0.06);
      border-radius: 3px;
      position: relative;
      overflow: hidden;
      background: linear-gradient(90deg, rgba(255, 255, 255, 0.055), rgba(8, 11, 15, 0.64));
      color: #cfe4df;
      opacity: 0.78;
    }

    #hud-root .hud-feed-tag {
      min-width: 30px;
      padding: 1px 4px;
      border: 1px solid rgba(243, 195, 106, 0.16);
      border-radius: 2px;
      background: rgba(8, 10, 13, 0.66);
      font-size: 9px;
      font-weight: 700;
      text-align: center;
    }

    #hud-root .hud-feed-entry.kill {
      color: #ffb37a;
      background: linear-gradient(90deg, rgba(255, 132, 82, 0.2) 0%, rgba(255, 132, 82, 0.09) 100%);
      box-shadow: inset 3px 0 0 rgba(255, 115, 76, 0.92), 0 0 12px rgba(255, 112, 72, 0.16);
      text-shadow: 0 0 8px rgba(255, 136, 82, 0.28);
    }

    #hud-root .hud-feed-entry.kill .hud-feed-tag {
      background: rgba(255, 120, 70, 0.24);
      color: #ffe0c8;
      box-shadow: 0 0 8px rgba(255, 108, 58, 0.18);
    }

    #hud-root .hud-feed-entry.kill span:last-child {
      font-weight: 800;
    }

    #hud-root .hud-feed-entry.pickup {
      color: #9dffb4;
      background: rgba(112, 255, 151, 0.1);
    }

    #hud-root .hud-feed-entry.heal {
      color: #a8ffca;
      background: rgba(112, 255, 151, 0.09);
    }

    #hud-root .hud-feed-entry.respawn {
      color: #ffd08a;
      background: linear-gradient(90deg, rgba(255, 139, 74, 0.19) 0%, rgba(255, 90, 74, 0.09) 100%);
      box-shadow: inset 3px 0 0 rgba(255, 125, 74, 0.84), 0 0 11px rgba(255, 95, 74, 0.13);
      text-shadow: 0 0 8px rgba(255, 132, 74, 0.24);
    }

    #hud-root .hud-feed-entry.respawn .hud-feed-tag {
      background: rgba(255, 118, 74, 0.22);
      color: #ffe0c8;
      box-shadow: 0 0 8px rgba(255, 96, 74, 0.16);
    }

    #hud-root .hud-feed-entry.respawn span:last-child {
      font-weight: 760;
    }

    #hud-root .hud-leaderboard-entry.current {
      color: #7fe4ff;
      text-shadow: 0 0 7px rgba(105, 223, 246, 0.28);
    }

    #hud-root .hud-leaderboard-entry.dead {
      opacity: 0.5;
    }

    #hud-root .hud-weapon-entry {
      padding: 4px 7px;
      border: 1px solid rgba(123, 141, 160, 0.24);
      border-radius: 3px;
      background: linear-gradient(180deg, rgba(37, 43, 49, 0.7), rgba(8, 11, 15, 0.8));
      color: #d4dde6;
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.045);
    }

    #hud-root .hud-weapon-entry.current {
      border-color: rgba(105, 223, 246, 0.74);
      background: linear-gradient(90deg, rgba(105, 223, 246, 0.18), rgba(17, 25, 31, 0.84));
      color: #fafff2;
      box-shadow: inset 3px 0 0 rgba(105, 223, 246, 0.72), inset 0 1px 0 rgba(255, 255, 255, 0.08);
    }

    #hud-root .hud-weapon-entry.pistol.current {
      border-color: rgba(174, 238, 255, 0.72);
      box-shadow: inset 3px 0 0 rgba(174, 238, 255, 0.66), inset 0 1px 0 rgba(255, 255, 255, 0.08);
    }

    #hud-root .hud-weapon-entry.rocket.current {
      border-color: rgba(255, 155, 85, 0.78);
      background: linear-gradient(90deg, rgba(255, 155, 85, 0.2), rgba(22, 15, 11, 0.84));
      box-shadow: inset 3px 0 0 rgba(255, 155, 85, 0.78), 0 0 10px rgba(255, 155, 85, 0.1);
    }

    #hud-root .hud-weapon-entry.gatling.current {
      border-color: rgba(255, 216, 109, 0.76);
      background: linear-gradient(90deg, rgba(255, 216, 109, 0.17), rgba(20, 17, 10, 0.84));
      box-shadow: inset 3px 0 0 rgba(255, 216, 109, 0.74), 0 0 10px rgba(255, 216, 109, 0.08);
    }

    #hud-root .hud-weapon-entry.shotgun.current {
      border-color: rgba(255, 239, 183, 0.74);
      background: linear-gradient(90deg, rgba(255, 239, 183, 0.16), rgba(22, 17, 13, 0.84));
      box-shadow: inset 3px 0 0 rgba(255, 239, 183, 0.7), 0 0 10px rgba(255, 239, 183, 0.07);
    }

    #hud-root .hud-weapon-entry.warning {
      color: #ff8c8c;
      border-color: rgba(255, 114, 114, 0.44);
    }

    #hud-root .hud-status-list {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      margin: 5px 0 6px;
    }

    #hud-root .hud-status-entry {
      padding: 3px 7px;
      border: 1px solid rgba(255, 255, 255, 0.13);
      border-radius: 3px;
      background: linear-gradient(180deg, rgba(255, 255, 255, 0.075), rgba(0, 0, 0, 0.18));
      color: #dce7f2;
      font-size: 10px;
      font-weight: 700;
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.055);
    }

    #hud-root .hud-status-entry.danger {
      border-color: rgba(255, 114, 114, 0.46);
      color: #ff9a9a;
      background: rgba(255, 88, 88, 0.12);
    }

    #hud-root .hud-status-entry.warning {
      border-color: rgba(255, 211, 110, 0.44);
      color: #ffd36e;
      background: rgba(255, 193, 80, 0.11);
    }

    #hud-root .hud-status-entry.success {
      border-color: rgba(125, 255, 157, 0.44);
      color: #9dffb4;
      background: rgba(95, 255, 145, 0.1);
    }

    #hud-root .hud-status-entry.info {
      border-color: rgba(124, 229, 255, 0.42);
      color: #9eeaff;
      background: rgba(95, 203, 255, 0.1);
    }

    #hud-root .hud-skills-grid {
      display: grid;
      grid-template-columns: repeat(3, ${HUD_SKILL_CARD_WIDTH_PX}px);
      gap: ${HUD_SKILL_CARD_GAP_PX}px;
    }

    #hud-root .hud-skill-entry {
      position: relative;
      min-height: 72px;
      padding: 7px;
      border: 1px solid rgba(123, 141, 160, 0.26);
      border-radius: 3px;
      overflow: hidden;
      --hud-skill-progress: 0deg;
      --hud-skill-fill: 0%;
      background:
        linear-gradient(135deg, rgba(243, 195, 106, 0.16) 0 5px, transparent 5px),
        linear-gradient(180deg, rgba(34, 39, 45, 0.78), rgba(8, 11, 15, 0.84));
      color: #d0dae4;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.055);
    }

    #hud-root .hud-skill-entry.cooldown,
    #hud-root .hud-skill-entry.active {
      color: #ffe0a1;
      border-color: rgba(255, 201, 91, 0.56);
      background:
        linear-gradient(135deg, rgba(255, 201, 91, 0.2) 0 5px, transparent 5px),
        linear-gradient(180deg, rgba(52, 42, 24, 0.9), rgba(10, 10, 11, 0.88));
    }

    #hud-root .hud-skill-entry.ready {
      border-color: rgba(181, 255, 109, 0.62);
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06), 0 0 8px rgba(181, 255, 109, 0.08);
    }

    #hud-root .hud-skill-entry.prepared {
      border-color: rgba(116, 242, 255, 0.8);
      background:
        linear-gradient(135deg, rgba(116, 242, 255, 0.22) 0 5px, transparent 5px),
        linear-gradient(180deg, rgba(28, 56, 66, 0.88), rgba(9, 23, 29, 0.86));
      color: #74f2ff;
      text-shadow: 0 0 7px rgba(116, 242, 255, 0.22);
    }

    #hud-root .hud-skill-key {
      position: relative;
      z-index: 1;
      font-size: 16px;
      font-weight: 700;
    }

    #hud-root .hud-skill-icon {
      position: relative;
      z-index: 1;
      font-size: 18px;
      line-height: 1;
      text-shadow: 0 0 8px rgba(255, 205, 96, 0.28);
    }

    #hud-root .hud-skill-name,
    #hud-root .hud-skill-state {
      position: relative;
      z-index: 1;
      font-size: 10px;
      line-height: 1.15;
    }

    #hud-root .hud-skill-clock {
      position: absolute;
      top: 6px;
      right: 6px;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      border: 1px solid rgba(255, 226, 158, 0.28);
      background:
        radial-gradient(circle at center, rgba(8, 10, 12, 0.88) 0 47%, transparent 49%),
        conic-gradient(rgba(255, 199, 86, 0.74) var(--hud-skill-progress), rgba(255, 255, 255, 0.08) 0);
      opacity: 0.76;
    }

    #hud-root .hud-skill-bar {
      position: absolute;
      left: 7px;
      right: 7px;
      bottom: 6px;
      height: 4px;
      border-radius: 999px;
      overflow: hidden;
      background: rgba(255, 255, 255, 0.08);
    }

    #hud-root .hud-skill-bar-fill {
      width: var(--hud-skill-fill);
      height: 100%;
      border-radius: inherit;
      background: linear-gradient(90deg, rgba(105, 223, 246, 0.72), rgba(255, 201, 91, 0.92));
      transition: width 90ms linear;
    }
  `;

  document.head.appendChild(style);
}

function createElement<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  className?: string,
  text?: string
): HTMLElementTagNameMap[K] {
  const element = document.createElement(tag);
  if (className) {
    element.className = className;
  }
  if (text !== undefined) {
    element.textContent = text;
  }
  return element;
}

function setTextIfChanged(element: HTMLElement, text: string): void {
  if (element.textContent !== text) {
    element.textContent = text;
  }
}

function setClassNameIfChanged(element: HTMLElement, className: string): void {
  if (element.className !== className) {
    element.className = className;
  }
}

function setStylePropertyIfChanged(element: HTMLElement, property: string, value: string): void {
  let cachedValues = styleValueCache.get(element);
  if (!cachedValues) {
    cachedValues = new Map<string, string>();
    styleValueCache.set(element, cachedValues);
  }

  if (cachedValues.get(property) !== value) {
    cachedValues.set(property, value);
    element.style.setProperty(property, value);
  }
}

function reconcileChildrenCount(parent: HTMLElement, count: number, createChild: () => HTMLElement): void {
  while (parent.children.length > count) {
    parent.lastElementChild?.remove();
  }

  while (parent.children.length < count) {
    parent.appendChild(createChild());
  }
}

function isHudDiagnosticsEnabled(): boolean {
  if (cachedHudDiagnosticsEnabled !== null) {
    return cachedHudDiagnosticsEnabled;
  }

  cachedHudDiagnosticsEnabled = readHudDiagnosticsEnabled();
  return cachedHudDiagnosticsEnabled;
}

function readHudDiagnosticsEnabled(): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  const params = new URLSearchParams(window.location.search);
  if (isEnabledDiagnosticsValue(params.get("diagnostics")) || isEnabledDiagnosticsValue(params.get("battleDiagnostics"))) {
    return true;
  }
  if (isTargetDiagnosticsEnabled(params.get("target"))) {
    return true;
  }

  try {
    return isEnabledDiagnosticsValue(window.localStorage.getItem(BATTLE_DIAGNOSTICS_STORAGE_KEY));
  } catch {
    return false;
  }
}

function isEnabledDiagnosticsValue(value: string | null): boolean {
  if (value === null) {
    return false;
  }

  return value === "1" || value.toLowerCase() === "true";
}

function isTargetDiagnosticsEnabled(target: string | null): boolean {
  if (!target) {
    return false;
  }

  try {
    const targetUrl = new URL(target, window.location.origin);
    const targetParams = targetUrl.searchParams;
    return (
      isEnabledDiagnosticsValue(targetParams.get("diagnostics")) ||
      isEnabledDiagnosticsValue(targetParams.get("battleDiagnostics"))
    );
  } catch {
    return false;
  }
}

function getHudDiagnosticsRoot(): HudDiagnosticsRoot | null {
  if (!isHudDiagnosticsEnabled() || typeof window === "undefined") {
    return null;
  }

  const diagnosticsWindow = window as HudDiagnosticsWindow;
  diagnosticsWindow.__slayDemoBattleDiagnostics = diagnosticsWindow.__slayDemoBattleDiagnostics ?? {};
  return diagnosticsWindow.__slayDemoBattleDiagnostics;
}

function mixMinimapHashByte(hash: number, value: number): number {
  return Math.imul(hash ^ (value & 0xff), MINIMAP_HASH_PRIME) >>> 0;
}

function mixMinimapHashNumber(hash: number, value: number): number {
  minimapHashView.setFloat64(0, value, true);
  let nextHash = hash;
  for (let index = 0; index < 8; index += 1) {
    nextHash = mixMinimapHashByte(nextHash, minimapHashView.getUint8(index));
  }
  return nextHash;
}

function mixMinimapHashRect(hash: number, rect: HudMinimapRect): number {
  let nextHash = mixMinimapHashNumber(hash, rect.x);
  nextHash = mixMinimapHashNumber(nextHash, rect.y);
  nextHash = mixMinimapHashNumber(nextHash, rect.width);
  return mixMinimapHashNumber(nextHash, rect.height);
}

function buildMinimapStaticLayerSignature(
  minimap: HudMinimapData,
  canvasWidth: number,
  canvasHeight: number
): string {
  if (minimap.staticLayerSignature) {
    return `${canvasWidth}x${canvasHeight}:${minimap.staticLayerSignature}`;
  }

  let hash = MINIMAP_HASH_OFFSET;
  hash = mixMinimapHashNumber(hash, canvasWidth);
  hash = mixMinimapHashNumber(hash, canvasHeight);
  hash = mixMinimapHashNumber(hash, minimap.worldWidth);
  hash = mixMinimapHashNumber(hash, minimap.worldHeight);

  const clearanceObstacles = minimap.clearanceObstacles ?? [];
  hash = mixMinimapHashNumber(hash, clearanceObstacles.length);
  clearanceObstacles.forEach((clearance) => {
    hash = mixMinimapHashRect(hash, clearance);
  });

  hash = mixMinimapHashNumber(hash, minimap.obstacles.length);
  minimap.obstacles.forEach((obstacle) => {
    hash = mixMinimapHashRect(hash, obstacle);
  });

  if (minimap.centerLimitRect) {
    hash = mixMinimapHashByte(hash, 1);
    hash = mixMinimapHashRect(hash, minimap.centerLimitRect);
  } else {
    hash = mixMinimapHashByte(hash, 0);
  }

  return hash.toString(36);
}

export class Hud {
  private root: HTMLElement;
  private overlay: HTMLDivElement;
  private timer: HTMLDivElement;
  private feedPanel: HTMLDivElement;
  private rightTop: HTMLDivElement;
  private leftBottom: HTMLDivElement;
  private weaponPanel: HTMLDivElement;
  private skillPanel: HTMLDivElement;
  private nameLine: HTMLDivElement;
  private hpLabel: HTMLDivElement;
  private hpFill: HTMLDivElement;
  private staminaLabel: HTMLDivElement;
  private staminaFill: HTMLDivElement;
  private weaponName: HTMLDivElement;
  private weaponAmmo: HTMLDivElement;
  private weaponState: HTMLDivElement;
  private pickupHint: HTMLDivElement;
  private statusList: HTMLDivElement;
  private weaponList: HTMLDivElement;
  private skillGrid: HTMLDivElement;
  private minimapCanvas: HTMLCanvasElement;
  private minimapContext: CanvasRenderingContext2D;
  private minimapStaticCanvas: HTMLCanvasElement;
  private minimapStaticContext: CanvasRenderingContext2D;
  private minimapStaticLayerSignature = "";
  private expandedMapPanel: HTMLDivElement;
  private expandedMapCanvas: HTMLCanvasElement;
  private expandedMapContext: CanvasRenderingContext2D;
  private expandedMapStaticCanvas: HTMLCanvasElement;
  private expandedMapStaticContext: CanvasRenderingContext2D;
  private expandedMapStaticLayerSignature = "";
  private minimapRenderCount = 0;
  private minimapStaticLayerRedrawCount = 0;
  private readonly diagnosticsEnabled = isHudDiagnosticsEnabled();
  private leaderboardList: HTMLDivElement;
  private feedList: HTMLDivElement;

  public constructor(root: HTMLElement) {
    ensureHudStyle();
    this.root = root;
    this.root.innerHTML = "";

    this.overlay = createElement("div");
    this.root.appendChild(this.overlay);

    this.timer = createElement("div", "hud-timer", "--:--");
    this.feedPanel = this.createPanel();
    this.rightTop = this.createPanel();
    this.leftBottom = this.createPanel();
    this.weaponPanel = this.createPanel();
    this.skillPanel = this.createPanel();

    this.nameLine = createElement("div", "hud-title", "玩家同步中");
    this.hpLabel = createElement("div", "hud-line", "生命值同步中");
    this.hpFill = createElement("div", "hud-bar-fill");
    this.hpFill.style.background = "linear-gradient(90deg, #ff6072 0%, #ff876d 100%)";
    this.staminaLabel = createElement("div", "hud-line", "体力同步中");
    this.staminaFill = createElement("div", "hud-bar-fill");
    this.staminaFill.style.background = "linear-gradient(90deg, #ffc94d 0%, #ff9d2b 100%)";

    this.weaponName = createElement("div", "hud-line", "武器同步中");
    this.weaponAmmo = createElement("div", "hud-line", "弹药同步中");
    this.weaponState = createElement("div", "hud-line", "状态同步中");
    this.pickupHint = createElement("div", "hud-line", "操作同步中");
    this.statusList = createElement("div", "hud-status-list");
    this.weaponList = createElement("div", "hud-weapon-list");

    this.skillGrid = createElement("div", "hud-skills-grid");
    this.minimapCanvas = createElement("canvas", "hud-minimap");
    this.minimapCanvas.width = 196;
    this.minimapCanvas.height = 196;
    const context = this.minimapCanvas.getContext("2d");
    if (!context) {
      throw new Error("HUD minimap canvas context unavailable");
    }
    this.minimapContext = context;
    this.minimapStaticCanvas = createElement("canvas");
    this.minimapStaticCanvas.width = this.minimapCanvas.width;
    this.minimapStaticCanvas.height = this.minimapCanvas.height;
    const staticContext = this.minimapStaticCanvas.getContext("2d");
    if (!staticContext) {
      throw new Error("HUD minimap static canvas context unavailable");
    }
    this.minimapStaticContext = staticContext;
    this.expandedMapPanel = createElement("div", "hud-expanded-map-panel");
    const expandedMapHeader = createElement("div", "hud-expanded-map-header");
    expandedMapHeader.append(
      createElement("span", undefined, "战术地图"),
      createElement("span", "hud-expanded-map-hint", "松开 TAB 返回")
    );
    this.expandedMapCanvas = createElement("canvas", "hud-expanded-map");
    this.expandedMapCanvas.width = 560;
    this.expandedMapCanvas.height = 560;
    const expandedMapContext = this.expandedMapCanvas.getContext("2d");
    if (!expandedMapContext) {
      throw new Error("HUD expanded map canvas context unavailable");
    }
    this.expandedMapContext = expandedMapContext;
    this.expandedMapStaticCanvas = createElement("canvas");
    this.expandedMapStaticCanvas.width = this.expandedMapCanvas.width;
    this.expandedMapStaticCanvas.height = this.expandedMapCanvas.height;
    const expandedMapStaticContext = this.expandedMapStaticCanvas.getContext("2d");
    if (!expandedMapStaticContext) {
      throw new Error("HUD expanded map static canvas context unavailable");
    }
    this.expandedMapStaticContext = expandedMapStaticContext;
    this.expandedMapPanel.append(
      expandedMapHeader,
      this.expandedMapCanvas,
      createElement("div", "hud-expanded-map-legend", "蓝色：你  /  红色：敌人  /  橙色：毒圈")
    );
    this.leaderboardList = createElement("div", "hud-leaderboard-list");
    this.feedList = createElement("div", "hud-feed-list");
    this.feedPanel.append(createElement("div", "hud-title", "战斗日志"), this.feedList);

    this.leftBottom.append(
      this.nameLine,
      this.hpLabel,
      this.createBarShell(this.hpFill),
      this.staminaLabel,
      this.createBarShell(this.staminaFill)
    );

    this.weaponPanel.append(
      createElement("div", "hud-title", "武器栏"),
      this.weaponName,
      this.weaponAmmo,
      this.weaponState,
      this.pickupHint,
      this.statusList,
      this.weaponList
    );

    this.skillPanel.append(createElement("div", "hud-title", "技能"), this.skillGrid);
    this.rightTop.append(createElement("div", "hud-title", "小地图 / 排行"), this.minimapCanvas, this.leaderboardList);

    this.overlay.append(
      this.timer,
      this.feedPanel,
      this.rightTop,
      this.leftBottom,
      this.weaponPanel,
      this.skillPanel,
      this.expandedMapPanel
    );
    this.layout(window.innerWidth, window.innerHeight);
  }

  public layout(width: number, height: number): void {
    this.overlay.style.position = "absolute";
    this.overlay.style.inset = "0";

    Object.assign(this.feedPanel.style, {
      left: "14px",
      top: "64px",
      width: `${Math.min(252, Math.max(214, width - 40))}px`,
      minHeight: "58px"
    });

    Object.assign(this.rightTop.style, {
      right: `${HUD_EDGE_GAP_PX}px`,
      top: `${HUD_EDGE_GAP_PX}px`,
      width: "188px"
    });

    Object.assign(this.leftBottom.style, {
      left: `${HUD_EDGE_GAP_PX}px`,
      bottom: `${HUD_EDGE_GAP_PX}px`,
      width: `${Math.min(256, Math.max(242, width - 40))}px`
    });

    Object.assign(this.weaponPanel.style, {
      right: `${HUD_EDGE_GAP_PX}px`,
      bottom: `${HUD_EDGE_GAP_PX}px`,
      width: `${HUD_WEAPON_PANEL_WIDTH_PX}px`
    });

    Object.assign(this.skillPanel.style, {
      right: `${HUD_SKILL_PANEL_RIGHT_PX}px`,
      bottom: `${HUD_EDGE_GAP_PX}px`,
      width: `${HUD_SKILL_PANEL_WIDTH_PX}px`
    });

    if (width < 760) {
      this.skillPanel.style.right = `${HUD_EDGE_GAP_PX}px`;
      this.skillPanel.style.bottom = "196px";
    }

    if (height < 720) {
      this.rightTop.style.top = "50px";
    } else {
      this.rightTop.style.top = `${HUD_EDGE_GAP_PX}px`;
    }

    const expandedMapSize = Math.round(Math.min(760, Math.max(300, Math.min(width - 48, height - 126))));
    this.resizeExpandedMapCanvas(expandedMapSize);
  }

  public update(state: HudState): void {
    setTextIfChanged(this.timer, state.timer);
    setTextIfChanged(this.nameLine, state.playerName);
    setTextIfChanged(this.hpLabel, `生命值 ${Math.ceil(state.hp)} / ${state.maxHp}`);
    setTextIfChanged(this.staminaLabel, `体力 ${Math.ceil(state.stamina)} / ${state.maxStamina}`);
    setStylePropertyIfChanged(this.hpFill, "width", `${Math.max(0, Math.min(100, (state.hp / state.maxHp) * 100))}%`);
    setStylePropertyIfChanged(
      this.staminaFill,
      "width",
      `${Math.max(0, Math.min(100, (state.stamina / state.maxStamina) * 100))}%`
    );

    setTextIfChanged(this.weaponName, state.currentWeaponName);
    setTextIfChanged(this.weaponAmmo, state.currentWeaponAmmo);
    setTextIfChanged(this.weaponState, state.currentWeaponState);
    setTextIfChanged(this.pickupHint, state.pickupHint);
    setStylePropertyIfChanged(this.weaponState, "color", state.currentWeaponState.includes("过热") ? "#ff8c8c" : "#f6f6f6");

    this.renderStatusEntries(state.statusEntries);
    this.renderWeaponEntries(state.weaponEntries);
    this.renderSkillEntries(state.skillEntries);
    this.renderLeaderboard(state.leaderboard);
    this.renderFeed(state.feed);
    this.renderMinimap(state.minimap, state.mapExpanded);
  }

  public destroy(): void {
    this.root.innerHTML = "";
  }

  private resizeExpandedMapCanvas(size: number): void {
    const safeSize = Math.max(240, Math.round(size));
    if (this.expandedMapCanvas.width === safeSize && this.expandedMapCanvas.height === safeSize) {
      return;
    }

    this.expandedMapCanvas.width = safeSize;
    this.expandedMapCanvas.height = safeSize;
    this.expandedMapStaticCanvas.width = safeSize;
    this.expandedMapStaticCanvas.height = safeSize;
    this.expandedMapStaticLayerSignature = "";
  }

  private createPanel(): HTMLDivElement {
    return createElement("div", "hud-panel");
  }

  private createBarShell(fill: HTMLDivElement): HTMLDivElement {
    const shell = createElement("div", "hud-bar-shell");
    shell.appendChild(fill);
    return shell;
  }

  private renderWeaponEntries(entries: HudWeaponEntry[]): void {
    reconcileChildrenCount(this.weaponList, entries.length, () => createElement("div", "hud-weapon-entry"));
    entries.forEach((entry, index) => {
      const row = this.weaponList.children[index] as HTMLElement;
      setClassNameIfChanged(
        row,
        `hud-weapon-entry ${entry.tone}${entry.current ? " current" : ""}${entry.warning ? " warning" : ""}`
      );
      setTextIfChanged(row, entry.label);
    });
  }

  private renderStatusEntries(entries: HudStatusEntry[]): void {
    reconcileChildrenCount(this.statusList, entries.length, () => createElement("div", "hud-status-entry"));
    entries.forEach((entry, index) => {
      const chip = this.statusList.children[index] as HTMLElement;
      setClassNameIfChanged(chip, `hud-status-entry ${entry.tone}`);
      setTextIfChanged(chip, entry.label);
    });
  }

  private renderSkillEntries(entries: HudSkillEntry[]): void {
    reconcileChildrenCount(this.skillGrid, entries.length, () => {
      const cell = createElement("div", "hud-skill-entry");
      const bar = createElement("div", "hud-skill-bar");
      bar.appendChild(createElement("div", "hud-skill-bar-fill"));
      cell.append(
        createElement("div", "hud-skill-key"),
        createElement("div", "hud-skill-icon"),
        createElement("div", "hud-skill-name"),
        createElement("div", "hud-skill-state"),
        createElement("div", "hud-skill-clock"),
        bar
      );
      return cell;
    });

    entries.forEach((entry, index) => {
      const cell = this.skillGrid.children[index] as HTMLElement;
      const progress = Math.max(0, Math.min(1, Math.max(entry.cooldownProgress, entry.activeProgress)));
      setClassNameIfChanged(
        cell,
        `hud-skill-entry${entry.ready ? " ready" : ""}${entry.prepared ? " prepared" : ""}${entry.cooldownProgress > 0 ? " cooldown" : ""}${entry.activeProgress > 0 ? " active" : ""}`
      );
      setStylePropertyIfChanged(cell, "--hud-skill-progress", `${Math.round(progress * 360)}deg`);
      setStylePropertyIfChanged(cell, "--hud-skill-fill", `${Math.round(progress * 100)}%`);
      setTextIfChanged(cell.children[0] as HTMLElement, entry.key);
      setTextIfChanged(cell.children[1] as HTMLElement, entry.icon);
      setTextIfChanged(cell.children[2] as HTMLElement, entry.name);
      setTextIfChanged(cell.children[3] as HTMLElement, entry.state);
    });
  }

  private renderLeaderboard(entries: HudLeaderboardEntry[]): void {
    reconcileChildrenCount(this.leaderboardList, entries.length, () => createElement("div", "hud-leaderboard-entry"));
    entries.forEach((entry, index) => {
      const row = this.leaderboardList.children[index] as HTMLElement;
      setClassNameIfChanged(
        row,
        `hud-leaderboard-entry${entry.current ? " current" : ""}${entry.alive ? "" : " dead"}`
      );
      setTextIfChanged(row, `${entry.rank} ${entry.name} ${entry.score}`);
    });
  }

  private renderFeed(entries: HudFeedEntry[]): void {
    if (entries.length === 0) {
      reconcileChildrenCount(this.feedList, 1, () => createElement("div", "hud-feed-entry"));
      const row = this.feedList.children[0] as HTMLElement;
      setClassNameIfChanged(row, "hud-feed-entry");
      setStylePropertyIfChanged(row, "opacity", "");
      setTextIfChanged(row, "等待战斗事件...");
      return;
    }

    reconcileChildrenCount(this.feedList, entries.length, () => {
      const row = createElement("div", "hud-feed-entry");
      row.append(createElement("span", "hud-feed-tag"), createElement("span"));
      return row;
    });

    entries.forEach((entry, index) => {
      const row = this.feedList.children[index] as HTMLElement;
      if (row.children.length !== 2) {
        row.replaceChildren(createElement("span", "hud-feed-tag"), createElement("span"));
      }
      setClassNameIfChanged(row, `hud-feed-entry ${entry.tone}`);
      setStylePropertyIfChanged(row, "opacity", `${Math.max(0.28, Math.min(1, entry.alpha))}`);
      setTextIfChanged(row.children[0] as HTMLElement, getFeedToneLabel(entry.tone));
      setTextIfChanged(row.children[1] as HTMLElement, entry.message);
    });
  }

  private renderMinimap(minimap: HudMinimapData, expanded: boolean): void {
    this.minimapRenderCount += 1;
    this.minimapStaticLayerSignature = this.renderMinimapCanvas(
      minimap,
      this.minimapCanvas,
      this.minimapContext,
      this.minimapStaticCanvas,
      this.minimapStaticContext,
      this.minimapStaticLayerSignature,
      true
    );
    setClassNameIfChanged(this.expandedMapPanel, `hud-expanded-map-panel${expanded ? " visible" : ""}`);

    if (expanded) {
      this.expandedMapStaticLayerSignature = this.renderMinimapCanvas(
        minimap,
        this.expandedMapCanvas,
        this.expandedMapContext,
        this.expandedMapStaticCanvas,
        this.expandedMapStaticContext,
        this.expandedMapStaticLayerSignature,
        false
      );
    }

    this.publishMinimapDiagnostics(minimap);
  }

  private renderMinimapCanvas(
    minimap: HudMinimapData,
    canvas: HTMLCanvasElement,
    context: CanvasRenderingContext2D,
    staticCanvas: HTMLCanvasElement,
    staticContext: CanvasRenderingContext2D,
    currentStaticLayerSignature: string,
    trackStaticRedraw: boolean
  ): string {
    const resized = this.syncMinimapStaticCanvasSize(canvas, staticCanvas);
    const scaleX = canvas.width / minimap.worldWidth;
    const scaleY = canvas.height / minimap.worldHeight;
    const nextStaticLayerSignature = buildMinimapStaticLayerSignature(
      minimap,
      canvas.width,
      canvas.height
    );

    if (resized || nextStaticLayerSignature !== currentStaticLayerSignature) {
      this.renderMinimapStaticLayer(minimap, staticCanvas, staticContext, scaleX, scaleY);
      if (trackStaticRedraw) {
        this.minimapStaticLayerRedrawCount += 1;
      }
    }

    context.clearRect(0, 0, canvas.width, canvas.height);
    context.drawImage(staticCanvas, 0, 0);
    this.renderMinimapDynamicLayer(minimap, canvas, context, scaleX, scaleY);
    return nextStaticLayerSignature;
  }

  private syncMinimapStaticCanvasSize(canvas: HTMLCanvasElement, staticCanvas: HTMLCanvasElement): boolean {
    if (
      staticCanvas.width === canvas.width &&
      staticCanvas.height === canvas.height
    ) {
      return false;
    }

    staticCanvas.width = canvas.width;
    staticCanvas.height = canvas.height;
    return true;
  }

  private renderMinimapStaticLayer(
    minimap: HudMinimapData,
    staticCanvas: HTMLCanvasElement,
    context: CanvasRenderingContext2D,
    scaleX: number,
    scaleY: number
  ): void {
    const ctx = context;
    ctx.setLineDash([]);
    ctx.clearRect(0, 0, staticCanvas.width, staticCanvas.height);
    ctx.fillStyle = "rgba(14, 34, 46, 0.9)";
    ctx.fillRect(0, 0, staticCanvas.width, staticCanvas.height);

    ctx.fillStyle = "rgba(217, 154, 52, 0.13)";
    ctx.strokeStyle = "rgba(255, 214, 124, 0.18)";
    ctx.lineWidth = 1;
    minimap.clearanceObstacles?.forEach((clearance) => {
      const x = clearance.x * scaleX;
      const y = clearance.y * scaleY;
      const width = clearance.width * scaleX;
      const height = clearance.height * scaleY;

      ctx.fillRect(x, y, width, height);
      ctx.strokeRect(x, y, width, height);
    });

    ctx.fillStyle = "rgba(10, 16, 21, 0.82)";
    ctx.strokeStyle = "rgba(168, 204, 222, 0.78)";
    ctx.lineWidth = 1.25;
    minimap.obstacles.forEach((obstacle) => {
      const x = obstacle.x * scaleX;
      const y = obstacle.y * scaleY;
      const width = obstacle.width * scaleX;
      const height = obstacle.height * scaleY;

      ctx.fillRect(x, y, width, height);
      ctx.strokeRect(x, y, width, height);
    });

    if (minimap.centerLimitRect) {
      const limit = minimap.centerLimitRect;
      ctx.strokeStyle = "rgba(105, 223, 246, 0.82)";
      ctx.lineWidth = 1.5;
      ctx.setLineDash([4, 3]);
      ctx.strokeRect(limit.x * scaleX, limit.y * scaleY, limit.width * scaleX, limit.height * scaleY);
      ctx.setLineDash([]);
    }
  }

  private renderMinimapDynamicLayer(
    minimap: HudMinimapData,
    canvas: HTMLCanvasElement,
    context: CanvasRenderingContext2D,
    scaleX: number,
    scaleY: number
  ): void {
    const ctx = context;
    this.renderMinimapGasZone(minimap, canvas, ctx, scaleX, scaleY);

    ctx.strokeStyle = "rgba(255, 214, 124, 0.7)";
    ctx.lineWidth = canvas.width >= 360 ? 2 : 1;
    ctx.strokeRect(
      minimap.cameraRect.x * scaleX,
      minimap.cameraRect.y * scaleY,
      Math.max(10, minimap.cameraRect.width * scaleX),
      Math.max(10, minimap.cameraRect.height * scaleY)
    );

    const pickupSize = canvas.width >= 360 ? 8 : 6;
    minimap.pickups.forEach((pickup) => {
      ctx.fillStyle = pickup.color;
      ctx.fillRect(pickup.x * scaleX - pickupSize / 2, pickup.y * scaleY - pickupSize / 2, pickupSize, pickupSize);
    });

    minimap.heroes.forEach((hero) => {
      ctx.beginPath();
      ctx.fillStyle = hero.color;
      ctx.arc(hero.x * scaleX, hero.y * scaleY, Math.max(canvas.width >= 360 ? 7 : 3.5, hero.radius * 1.45), 0, Math.PI * 2);
      ctx.fill();
      if (hero.color === "#6ee4ff") {
        ctx.strokeStyle = "rgba(255, 255, 255, 0.92)";
        ctx.lineWidth = canvas.width >= 360 ? 2 : 1;
        ctx.stroke();
      }
    });
  }

  private renderMinimapGasZone(
    minimap: HudMinimapData,
    canvas: HTMLCanvasElement,
    ctx: CanvasRenderingContext2D,
    scaleX: number,
    scaleY: number
  ): void {
    const gas = minimap.gasZone;
    if (!gas) {
      return;
    }

    const alpha = gas.phase === "waiting" ? 0.1 : 0.28;
    ctx.fillStyle = `rgba(255, 74, 0, ${alpha})`;
    const outsideGasPath = new Path2D();
    outsideGasPath.rect(0, 0, canvas.width, canvas.height);
    outsideGasPath.ellipse(gas.x * scaleX, gas.y * scaleY, gas.radius * scaleX, gas.radius * scaleY, 0, 0, Math.PI * 2);
    ctx.fill(outsideGasPath, "evenodd");

    ctx.beginPath();
    ctx.strokeStyle = "rgba(255, 88, 24, 0.95)";
    ctx.lineWidth = canvas.width >= 360 ? 5 : 2;
    ctx.ellipse(gas.x * scaleX, gas.y * scaleY, gas.radius * scaleX, gas.radius * scaleY, 0, 0, Math.PI * 2);
    ctx.stroke();

    if (gas.nextRadius !== gas.radius) {
      ctx.save();
      ctx.setLineDash([8, 6]);
      ctx.beginPath();
      ctx.strokeStyle = "rgba(255, 216, 124, 0.72)";
      ctx.lineWidth = canvas.width >= 360 ? 2 : 1;
      ctx.ellipse(gas.x * scaleX, gas.y * scaleY, gas.nextRadius * scaleX, gas.nextRadius * scaleY, 0, 0, Math.PI * 2);
      ctx.stroke();
      ctx.restore();
    }
  }

  private publishMinimapDiagnostics(minimap: HudMinimapData): void {
    if (!this.diagnosticsEnabled) {
      return;
    }

    const diagnosticsRoot = getHudDiagnosticsRoot();
    if (!diagnosticsRoot) {
      return;
    }

    diagnosticsRoot.hud = {
      minimapRenderCount: this.minimapRenderCount,
      minimapStaticLayerRedrawCount: this.minimapStaticLayerRedrawCount,
      lastObstacleCount: minimap.obstacles.length,
      lastHeroCount: minimap.heroes.length,
      lastPickupCount: minimap.pickups.length
    };
  }
}

function getFeedToneLabel(tone: HudFeedEntry["tone"]): string {
  switch (tone) {
    case "kill":
      return "击杀";
    case "pickup":
      return "拾取";
    case "heal":
      return "治疗";
    case "respawn":
      return "淘汰";
    default:
      return "状态";
  }
}
