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
}

export interface HudSkillEntry {
  key: string;
  name: string;
  state: string;
  ready: boolean;
  prepared: boolean;
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

export interface HudMinimapData {
  worldWidth: number;
  worldHeight: number;
  cameraRect: HudMinimapRect;
  obstacles: HudMinimapRect[];
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
  leaderboard: HudLeaderboardEntry[];
  feed: HudFeedEntry[];
  minimap: HudMinimapData;
  debugLines: string[];
}

const HUD_STYLE_ID = "slay-demo-dom-hud-style";

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
    }

    #hud-root .hud-timer {
      position: absolute;
      top: 14px;
      left: 50%;
      transform: translateX(-50%);
      min-width: 118px;
      padding: 6px 18px;
      border: 1px solid rgba(255, 214, 112, 0.22);
      border-radius: 999px;
      background: rgba(4, 5, 6, 0.72);
      color: #fff1bf;
      font-size: 18px;
      font-weight: 800;
      font-variant-numeric: tabular-nums;
      text-align: center;
      letter-spacing: 0.08em;
      box-shadow: 0 0 20px rgba(0, 0, 0, 0.34);
    }

    #hud-root .hud-panel {
      position: absolute;
      pointer-events: none;
      padding: 10px 12px;
      border: 1px solid rgba(255, 209, 121, 0.18);
      border-radius: 12px;
      background: rgba(9, 11, 14, 0.82);
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
      backdrop-filter: blur(2px);
    }

    #hud-root .hud-title {
      margin-bottom: 6px;
      color: #ffd58a;
      font-size: 11px;
      letter-spacing: 0.16em;
      text-transform: uppercase;
    }

    #hud-root .hud-line {
      font-size: 12px;
      line-height: 1.35;
      color: #e8eef3;
      white-space: pre-wrap;
    }

    #hud-root .hud-bar-shell {
      width: 220px;
      height: 10px;
      margin-top: 4px;
      margin-bottom: 8px;
      border-radius: 999px;
      overflow: hidden;
      background: rgba(18, 23, 28, 0.96);
      border: 1px solid rgba(255, 255, 255, 0.08);
    }

    #hud-root .hud-bar-fill {
      height: 100%;
      width: 0;
      transition: width 120ms linear;
    }

    #hud-root .hud-minimap {
      display: block;
      width: 140px;
      height: 140px;
      margin-bottom: 8px;
      border: 1px solid rgba(136, 170, 192, 0.4);
      background: rgba(14, 34, 46, 0.9);
      image-rendering: pixelated;
    }

    #hud-root .hud-leaderboard-list,
    #hud-root .hud-weapon-list,
    #hud-root .hud-feed-list {
      display: grid;
      gap: 5px;
    }

    #hud-root .hud-leaderboard-entry,
    #hud-root .hud-weapon-entry,
    #hud-root .hud-feed-entry {
      font-size: 12px;
      line-height: 1.3;
    }

    #hud-root .hud-feed-entry {
      color: #cfe4df;
      opacity: 0.88;
    }

    #hud-root .hud-feed-entry.kill {
      color: #ffb37a;
    }

    #hud-root .hud-feed-entry.pickup {
      color: #9dffb4;
    }

    #hud-root .hud-feed-entry.respawn {
      color: #86dfff;
    }

    #hud-root .hud-leaderboard-entry.current {
      color: #7fe4ff;
    }

    #hud-root .hud-leaderboard-entry.dead {
      opacity: 0.5;
    }

    #hud-root .hud-weapon-entry {
      padding: 2px 6px;
      border: 1px solid rgba(123, 141, 160, 0.2);
      border-radius: 8px;
      background: rgba(13, 17, 22, 0.82);
      color: #d4dde6;
    }

    #hud-root .hud-weapon-entry.current {
      border-color: rgba(181, 255, 109, 0.7);
      color: #fafff2;
    }

    #hud-root .hud-weapon-entry.warning {
      color: #ff8c8c;
    }

    #hud-root .hud-skills-grid {
      display: grid;
      grid-template-columns: repeat(2, 64px);
      gap: 8px;
    }

    #hud-root .hud-skill-entry {
      min-height: 62px;
      padding: 6px;
      border: 1px solid rgba(123, 141, 160, 0.22);
      border-radius: 10px;
      background: rgba(11, 15, 19, 0.88);
      color: #d0dae4;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }

    #hud-root .hud-skill-entry.ready {
      border-color: rgba(181, 255, 109, 0.6);
    }

    #hud-root .hud-skill-entry.prepared {
      border-color: rgba(116, 242, 255, 0.8);
      background: rgba(17, 39, 50, 0.92);
      color: #74f2ff;
    }

    #hud-root .hud-skill-key {
      font-size: 15px;
      font-weight: 700;
    }

    #hud-root .hud-skill-name,
    #hud-root .hud-skill-state {
      font-size: 10px;
      line-height: 1.2;
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
  private weaponList: HTMLDivElement;
  private skillGrid: HTMLDivElement;
  private minimapCanvas: HTMLCanvasElement;
  private minimapContext: CanvasRenderingContext2D;
  private leaderboardList: HTMLDivElement;
  private feedList: HTMLDivElement;

  public constructor(root: HTMLElement) {
    ensureHudStyle();
    this.root = root;
    this.root.innerHTML = "";

    this.overlay = createElement("div");
    this.root.appendChild(this.overlay);

    this.timer = createElement("div", "hud-timer", "05:00");
    this.feedPanel = this.createPanel();
    this.rightTop = this.createPanel();
    this.leftBottom = this.createPanel();
    this.weaponPanel = this.createPanel();
    this.skillPanel = this.createPanel();

    this.nameLine = createElement("div", "hud-title", "玩家");
    this.hpLabel = createElement("div", "hud-line", "生命值 0 / 0");
    this.hpFill = createElement("div", "hud-bar-fill");
    this.hpFill.style.background = "linear-gradient(90deg, #ff6072 0%, #ff876d 100%)";
    this.staminaLabel = createElement("div", "hud-line", "体力 0 / 0");
    this.staminaFill = createElement("div", "hud-bar-fill");
    this.staminaFill.style.background = "linear-gradient(90deg, #ffc94d 0%, #ff9d2b 100%)";

    this.weaponName = createElement("div", "hud-line", "手枪");
    this.weaponAmmo = createElement("div", "hud-line", "1 / 48");
    this.weaponState = createElement("div", "hud-line", "就绪");
    this.pickupHint = createElement("div", "hud-line", "滚轮切换 · R 换弹");
    this.weaponList = createElement("div", "hud-weapon-list");

    this.skillGrid = createElement("div", "hud-skills-grid");
    this.minimapCanvas = createElement("canvas", "hud-minimap");
    this.minimapCanvas.width = 140;
    this.minimapCanvas.height = 140;
    const context = this.minimapCanvas.getContext("2d");
    if (!context) {
      throw new Error("HUD minimap canvas context unavailable");
    }
    this.minimapContext = context;
    this.leaderboardList = createElement("div", "hud-leaderboard-list");
    this.feedList = createElement("div", "hud-feed-list");
    this.feedPanel.append(createElement("div", "hud-title", "LOG"), this.feedList);

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
      this.weaponList
    );

    this.skillPanel.append(createElement("div", "hud-title", "技能"), this.skillGrid);
    this.rightTop.append(createElement("div", "hud-title", "小地图 / 排行"), this.minimapCanvas, this.leaderboardList);

    this.overlay.append(this.timer, this.feedPanel, this.rightTop, this.leftBottom, this.weaponPanel, this.skillPanel);
    this.layout(window.innerWidth, window.innerHeight);
  }

  public layout(width: number, height: number): void {
    this.overlay.style.position = "absolute";
    this.overlay.style.inset = "0";

    Object.assign(this.feedPanel.style, {
      left: "18px",
      top: "70px",
      width: `${Math.min(286, Math.max(240, width - 48))}px`,
      minHeight: "82px"
    });

    Object.assign(this.rightTop.style, {
      right: "18px",
      top: "18px",
      width: "176px"
    });

    Object.assign(this.leftBottom.style, {
      left: "18px",
      bottom: "18px",
      width: `${Math.min(248, Math.max(220, width - 48))}px`
    });

    Object.assign(this.weaponPanel.style, {
      right: "18px",
      bottom: "18px",
      width: "236px"
    });

    Object.assign(this.skillPanel.style, {
      right: "268px",
      bottom: "18px",
      width: "154px"
    });

    if (width < 760) {
      this.skillPanel.style.right = "18px";
      this.skillPanel.style.bottom = "188px";
    }

    if (height < 720) {
      this.rightTop.style.top = "54px";
    } else {
      this.rightTop.style.top = "18px";
    }
  }

  public update(state: HudState): void {
    this.timer.textContent = state.timer;
    this.nameLine.textContent = state.playerName;
    this.hpLabel.textContent = `生命值 ${Math.ceil(state.hp)} / ${state.maxHp}`;
    this.staminaLabel.textContent = `体力 ${Math.ceil(state.stamina)} / ${state.maxStamina}`;
    this.hpFill.style.width = `${Math.max(0, Math.min(100, (state.hp / state.maxHp) * 100))}%`;
    this.staminaFill.style.width = `${Math.max(0, Math.min(100, (state.stamina / state.maxStamina) * 100))}%`;

    this.weaponName.textContent = state.currentWeaponName;
    this.weaponAmmo.textContent = state.currentWeaponAmmo;
    this.weaponState.textContent = state.currentWeaponState;
    this.pickupHint.textContent = state.pickupHint;
    this.weaponState.style.color = state.currentWeaponState.includes("过热") ? "#ff8c8c" : "#f6f6f6";

    this.renderWeaponEntries(state.weaponEntries);
    this.renderSkillEntries(state.skillEntries);
    this.renderLeaderboard(state.leaderboard);
    this.renderFeed(state.feed);
    this.renderMinimap(state.minimap);
  }

  public destroy(): void {
    this.root.innerHTML = "";
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
    this.weaponList.innerHTML = "";
    entries.forEach((entry) => {
      const row = createElement("div", "hud-weapon-entry", entry.label);
      if (entry.current) {
        row.classList.add("current");
      }
      if (entry.warning) {
        row.classList.add("warning");
      }
      this.weaponList.appendChild(row);
    });
  }

  private renderSkillEntries(entries: HudSkillEntry[]): void {
    this.skillGrid.innerHTML = "";
    entries.forEach((entry) => {
      const cell = createElement("div", "hud-skill-entry");
      if (entry.ready) {
        cell.classList.add("ready");
      }
      if (entry.prepared) {
        cell.classList.add("prepared");
      }

      cell.append(
        createElement("div", "hud-skill-key", entry.key),
        createElement("div", "hud-skill-name", entry.name),
        createElement("div", "hud-skill-state", entry.state)
      );
      this.skillGrid.appendChild(cell);
    });
  }

  private renderLeaderboard(entries: HudLeaderboardEntry[]): void {
    this.leaderboardList.innerHTML = "";
    entries.forEach((entry) => {
      const row = createElement("div", "hud-leaderboard-entry", `${entry.rank} ${entry.name} ${entry.score}`);
      if (entry.current) {
        row.classList.add("current");
      }
      if (!entry.alive) {
        row.classList.add("dead");
      }
      this.leaderboardList.appendChild(row);
    });
  }

  private renderFeed(entries: HudFeedEntry[]): void {
    this.feedList.innerHTML = "";
    if (entries.length === 0) {
      this.feedList.appendChild(createElement("div", "hud-feed-entry", "等待战斗事件..."));
      return;
    }

    entries.forEach((entry) => {
      const row = createElement("div", "hud-feed-entry", entry.message);
      row.classList.add(entry.tone);
      row.style.opacity = `${Math.max(0.28, Math.min(1, entry.alpha))}`;
      this.feedList.appendChild(row);
    });
  }

  private renderMinimap(minimap: HudMinimapData): void {
    const ctx = this.minimapContext;
    ctx.clearRect(0, 0, this.minimapCanvas.width, this.minimapCanvas.height);
    ctx.fillStyle = "rgba(14, 34, 46, 0.9)";
    ctx.fillRect(0, 0, this.minimapCanvas.width, this.minimapCanvas.height);

    const scaleX = this.minimapCanvas.width / minimap.worldWidth;
    const scaleY = this.minimapCanvas.height / minimap.worldHeight;

    ctx.strokeStyle = "rgba(84, 110, 128, 0.9)";
    ctx.lineWidth = 1;
    minimap.obstacles.forEach((obstacle) => {
      ctx.strokeRect(obstacle.x * scaleX, obstacle.y * scaleY, obstacle.width * scaleX, obstacle.height * scaleY);
    });

    ctx.strokeStyle = "rgba(255, 214, 124, 0.7)";
    ctx.strokeRect(
      minimap.cameraRect.x * scaleX,
      minimap.cameraRect.y * scaleY,
      Math.max(10, minimap.cameraRect.width * scaleX),
      Math.max(10, minimap.cameraRect.height * scaleY)
    );

    minimap.pickups.forEach((pickup) => {
      ctx.fillStyle = pickup.color;
      ctx.fillRect(pickup.x * scaleX - 2, pickup.y * scaleY - 2, 4, 4);
    });

    minimap.heroes.forEach((hero) => {
      ctx.beginPath();
      ctx.fillStyle = hero.color;
      ctx.arc(hero.x * scaleX, hero.y * scaleY, hero.radius, 0, Math.PI * 2);
      ctx.fill();
    });
  }
}
