import { useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { GameCornerButton, type GameCornerIconKey } from "./GameCornerButton";
import { QuickPreviewOverlay, type QuickPreviewEntry } from "./QuickPreviewOverlay";

export type LobbyQuickKey = "replay" | "discussion" | "ranking" | "mails" | "social";

export interface LobbyQuickAction {
  key: LobbyQuickKey;
  label: string;
  iconKey: GameCornerIconKey;
  anchor: "left" | "right";
  badgeCount?: number;
}

export interface LobbyPreviewSet {
  title: string;
  eyebrow: string;
  detail: string;
  emptyTitle: string;
  emptyDetail: string;
  viewAllPath: string;
  anchor: "left" | "right";
  items: QuickPreviewEntry[];
}

interface LobbyAction {
  label: string;
  to?: string;
  onClick?: () => void;
  disabled?: boolean;
  variant?: "primary" | "default" | "ghost";
}

interface LobbyRailItem {
  label: string;
  value: string;
}

export interface LobbyShellProps {
  brand?: string;
  layoutMode?: "lobby" | "solo";
  title: string;
  subtitle?: string;
  playerName: string;
  playerBadge: string;
  playerAvatarSrc?: string;
  playerMeta: string;
  playerRating?: string;
  currentLoadoutLabel: string;
  skillTags: string[];
  quickActions: LobbyQuickAction[];
  previewSets: Record<LobbyQuickKey, LobbyPreviewSet>;
  primaryAction: LobbyAction;
  secondaryAction: LobbyAction;
  tertiaryAction?: LobbyAction;
  railItems?: LobbyRailItem[];
  menuBody?: ReactNode;
  leftDock?: ReactNode;
  rightDock?: ReactNode;
}

const lobbyVideoSrc = "/pics/demo.mp4";

export function LobbyShell({
  brand = "ARENA MENU",
  layoutMode = "lobby",
  title,
  subtitle,
  playerName,
  playerBadge,
  playerAvatarSrc,
  playerMeta,
  playerRating,
  currentLoadoutLabel,
  skillTags,
  quickActions,
  previewSets,
  primaryAction,
  secondaryAction,
  tertiaryAction,
  railItems = [],
  menuBody,
  leftDock,
  rightDock
}: LobbyShellProps) {
  const [activePreview, setActivePreview] = useState<LobbyQuickKey | null>(null);
  const leftActions = useMemo(() => quickActions.filter((action) => action.anchor === "left"), [quickActions]);
  const rightActions = useMemo(() => quickActions.filter((action) => action.anchor === "right"), [quickActions]);
  const activePreviewSet = activePreview ? previewSets[activePreview] : null;
  const hasSideDocks = Boolean(leftDock || rightDock);
  const isSolo = layoutMode === "solo";
  const stageClassName = [
    "game-lobby__stage",
    hasSideDocks || !isSolo ? "" : "game-lobby__stage--solo",
    !hasSideDocks && !isSolo ? "game-lobby__stage--menu" : ""
  ]
    .filter(Boolean)
    .join(" ");

  return (
    <main className={`game-lobby game-lobby--${layoutMode}`}>
      <video className="game-lobby__video" autoPlay muted loop playsInline preload="auto">
        <source src={lobbyVideoSrc} type="video/mp4" />
      </video>
      <div className="game-lobby__veil" aria-hidden="true" />
      <div className="game-lobby__noise" aria-hidden="true" />

      {!isSolo ? (
        <>
          <div className="game-lobby__corner game-lobby__corner--left" aria-label="左上功能入口">
            {leftActions.map((action) => (
              <GameCornerButton
                key={action.key}
                iconKey={action.iconKey}
                onClick={() => setActivePreview(action.key)}
                label={action.label}
                tooltipPlacement="bottom"
                badgeCount={action.badgeCount}
              />
            ))}
          </div>

          <div className="game-lobby__corner game-lobby__corner--right" aria-label="右下功能入口">
            {rightActions.map((action) => (
              <GameCornerButton
                key={action.key}
                iconKey={action.iconKey}
                onClick={() => setActivePreview(action.key)}
                label={action.label}
                tooltipPlacement="top"
                badgeCount={action.badgeCount}
              />
            ))}
          </div>
        </>
      ) : null}

      <div className="game-lobby__background-mark" aria-hidden="true" />

      <section className={stageClassName} aria-label="游戏大厅">
        {leftDock ? <aside className="game-lobby__dock game-lobby__dock--left">{leftDock}</aside> : null}

        <section className={`game-lobby__panel${isSolo ? " game-lobby__panel--solo" : ""}`}>
          <aside className="game-lobby__profile" aria-label="玩家面板">
            <span className="game-lobby__profile-pin">{playerBadge}</span>
            <div className="game-lobby__badge-wrap">
              <div className="game-lobby__badge">
                {playerAvatarSrc ? <img src={playerAvatarSrc} alt={playerName} /> : playerBadge}
              </div>
            </div>

            <small className="game-lobby__eyebrow">{brand}</small>
            <strong className="game-lobby__name">{playerName}</strong>
            <span className="game-lobby__meta">{playerMeta}</span>
            {playerRating ? <span className="game-lobby__rating">RATING {playerRating}</span> : null}

            <div className="game-lobby__kit">
              <small>当前配置</small>
              <strong>{currentLoadoutLabel}</strong>
            </div>

            <div className="game-lobby__skills" aria-label="技能摘要">
              {skillTags.map((skill) => (
                <span key={skill}>{skill}</span>
              ))}
            </div>
          </aside>

          <section className="game-lobby__center" aria-label="中心操作窗">
            <div className="game-lobby__copy">
              <small className="game-lobby__eyebrow">{brand}</small>
              <h1>{title}</h1>
              {subtitle ? <p>{subtitle}</p> : null}
            </div>

            {menuBody ? <div className="game-lobby__body">{menuBody}</div> : null}

            <div className="game-lobby__actions">
              {tertiaryAction ? renderAction({ ...tertiaryAction, variant: tertiaryAction.variant ?? "ghost" }) : <span />}
              {renderAction({ ...primaryAction, variant: primaryAction.variant ?? "primary" })}
              {renderAction({ ...secondaryAction, variant: secondaryAction.variant ?? "default" })}
            </div>
          </section>

          <aside className="game-lobby__rail" aria-label="状态栏">
            {railItems.map((item) => (
              <article key={item.label} className="game-lobby__rail-item">
                <span>{item.label}</span>
                <strong>{item.value}</strong>
              </article>
            ))}
          </aside>
        </section>

        {rightDock ? <aside className="game-lobby__dock game-lobby__dock--right">{rightDock}</aside> : null}
      </section>

      {activePreviewSet ? (
        <QuickPreviewOverlay
          title={activePreviewSet.title}
          eyebrow={activePreviewSet.eyebrow}
          detail={activePreviewSet.detail}
          emptyTitle={activePreviewSet.emptyTitle}
          emptyDetail={activePreviewSet.emptyDetail}
          viewAllPath={activePreviewSet.viewAllPath}
          anchor={activePreviewSet.anchor}
          items={activePreviewSet.items}
          onClose={() => setActivePreview(null)}
        />
      ) : null}
    </main>
  );
}

function renderAction(action: LobbyAction): ReactNode {
  const className = `game-lobby__action game-lobby__action--${action.variant ?? "default"}`;

  if (action.to) {
    return (
      <Link className={className} to={action.to}>
        {action.label}
      </Link>
    );
  }

  return (
    <button type="button" className={className} onClick={action.onClick} disabled={action.disabled}>
      {action.label}
    </button>
  );
}
