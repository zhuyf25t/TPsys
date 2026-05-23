import { useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { cn } from "./classNames";
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

export interface LobbyTopStatusItem extends LobbyRailItem {
  detail?: string;
  tone?: "ready" | "alert" | "data" | "idle";
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
  topStatusItems?: LobbyTopStatusItem[];
  menuBody?: ReactNode;
  leftDock?: ReactNode;
  rightDock?: ReactNode;
}

const lobbyVideoSrc = "/pics/demo.mp4";

/** 中文名：大厅壳层（LobbyShell）。游戏职责：组织大厅背景、玩家面板、主操作区和快捷入口。 */
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
  topStatusItems = [],
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
  const hasLeftActions = leftActions.length > 0;
  const hasRightActions = rightActions.length > 0;

  return (
    <main className="relative min-h-screen overflow-hidden bg-slate-950 text-slate-100">
      <video className="absolute inset-0 h-full w-full object-cover opacity-50" autoPlay muted loop playsInline preload="auto">
        <source src={lobbyVideoSrc} type="video/mp4" />
      </video>
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_50%_36%,rgba(245,158,11,0.18),transparent_34%),linear-gradient(180deg,rgba(2,6,23,0.58),rgba(2,6,23,0.94))]" aria-hidden="true" />
      <div className="pointer-events-none absolute inset-0 opacity-[0.08] [background-image:linear-gradient(rgba(255,255,255,.14)_1px,transparent_1px),linear-gradient(90deg,rgba(255,255,255,.14)_1px,transparent_1px)] [background-size:24px_24px]" aria-hidden="true" />

      {hasLeftActions ? (
        <div className="absolute left-4 top-4 z-20 flex gap-2" aria-label="左上功能入口">
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
      ) : null}

      {hasRightActions ? (
        <div className="absolute bottom-4 right-4 z-20 flex gap-2" aria-label="右下功能入口">
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
      ) : null}

      {topStatusItems.length > 0 ? (
        <section className="relative z-10 mx-auto flex w-full max-w-6xl flex-wrap justify-center gap-3 px-4 pt-4" aria-label="顶部战区状态">
          {topStatusItems.map((item) => (
            <article key={`${item.label}:${item.value}`} className={topStatusClassName(item.tone)}>
              <span className="text-[10px] font-black uppercase tracking-[0.2em] text-slate-400">{item.label}</span>
              <strong className="text-sm text-white">{item.value}</strong>
              {item.detail ? <small className="text-xs text-slate-400">{item.detail}</small> : null}
            </article>
          ))}
        </section>
      ) : null}

      <section
        className={cn(
          "relative z-10 mx-auto grid min-h-screen w-full max-w-[1480px] gap-5 px-4 py-24",
          hasSideDocks ? "xl:grid-cols-[minmax(220px,320px)_minmax(0,1fr)_minmax(220px,320px)]" : "xl:grid-cols-[minmax(0,1fr)]",
          isSolo && "max-w-5xl"
        )}
        aria-label="游戏大厅"
      >
        {leftDock ? <aside className="min-w-0 self-center">{leftDock}</aside> : null}

        <section
          className={cn(
            "grid min-h-[620px] gap-5 rounded border border-white/10 bg-slate-950/75 p-4 shadow-2xl shadow-black/50 backdrop-blur md:grid-cols-[260px_minmax(0,1fr)_190px]",
            isSolo && "md:grid-cols-[220px_minmax(0,1fr)]"
          )}
        >
          <aside className="flex flex-col items-center gap-3 rounded border border-white/10 bg-white/[0.04] p-4 text-center" aria-label="玩家面板">
            <span className="rounded-full border border-amber-200/40 bg-amber-300/10 px-3 py-1 text-xs font-black text-amber-100">{playerBadge}</span>
            <div className="grid h-24 w-24 place-items-center overflow-hidden rounded-full border border-cyan-200/40 bg-cyan-200/10 text-3xl font-black text-cyan-100">
              {playerAvatarSrc ? <img className="h-full w-full object-cover" src={playerAvatarSrc} alt={playerName} /> : playerBadge}
            </div>

            <small className="text-xs font-black uppercase tracking-[0.22em] text-cyan-200">{brand}</small>
            <strong className="text-2xl font-black text-white">{playerName}</strong>
            <span className="text-sm text-slate-300">{playerMeta}</span>
            {playerRating ? <span className="rounded border border-amber-200/30 bg-amber-300/10 px-3 py-1 text-xs font-black text-amber-100">RATING {playerRating}</span> : null}

            <div className="mt-2 w-full rounded border border-white/10 bg-black/20 p-3 text-left">
              <small className="text-xs font-bold text-slate-400">当前配置</small>
              <strong className="mt-1 block text-sm text-white">{currentLoadoutLabel}</strong>
            </div>

            <div className="flex flex-wrap justify-center gap-2" aria-label="技能摘要">
              {skillTags.map((skill) => (
                <span key={skill} className="rounded border border-cyan-200/20 bg-cyan-300/10 px-2 py-1 text-xs font-bold text-cyan-100">
                  {skill}
                </span>
              ))}
            </div>
          </aside>

          <section className="flex min-w-0 flex-col justify-between gap-6 rounded border border-white/10 bg-slate-900/55 p-5" aria-label="中心操作窗">
            <div>
              <small className="text-xs font-black uppercase tracking-[0.24em] text-amber-200">{brand}</small>
              <h1 className="mt-3 text-4xl font-black leading-tight text-white md:text-5xl">{title}</h1>
              {subtitle ? <p className="mt-3 max-w-2xl text-sm leading-6 text-slate-300">{subtitle}</p> : null}
            </div>

            {menuBody ? <div className="min-h-0">{menuBody}</div> : null}

            <div className="rounded border border-white/10 bg-black/20 p-4" aria-label="主操作区">
              <div className="mb-3 flex items-center justify-between gap-3 text-xs font-black uppercase tracking-[0.2em] text-slate-400">
                <span>COMMAND INPUT</span>
                <b className="text-amber-200">主操作台</b>
              </div>
              <div className="grid gap-3 sm:grid-cols-3">
                {tertiaryAction ? renderAction({ ...tertiaryAction, variant: tertiaryAction.variant ?? "ghost" }) : <span />}
                {renderAction({ ...primaryAction, variant: primaryAction.variant ?? "primary" })}
                {renderAction({ ...secondaryAction, variant: secondaryAction.variant ?? "default" })}
              </div>
            </div>
          </section>

          {!isSolo ? (
            <aside className="flex flex-col gap-3 rounded border border-white/10 bg-white/[0.04] p-3" aria-label="状态栏">
              {railItems.map((item) => (
                <article key={item.label} className="rounded border border-white/10 bg-black/20 p-3">
                  <span className="text-xs font-bold text-slate-400">{item.label}</span>
                  <strong className="mt-1 block text-sm text-white">{item.value}</strong>
                </article>
              ))}
            </aside>
          ) : null}
        </section>

        {rightDock ? <aside className="min-w-0 self-center">{rightDock}</aside> : null}
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

function topStatusClassName(tone: LobbyTopStatusItem["tone"]): string {
  return cn(
    "flex min-w-[130px] flex-col rounded border bg-slate-950/70 px-3 py-2 shadow-lg backdrop-blur",
    tone === "ready" && "border-emerald-300/30",
    tone === "alert" && "border-red-300/30",
    tone === "idle" && "border-slate-300/20",
    (!tone || tone === "data") && "border-cyan-300/30"
  );
}

function renderAction(action: LobbyAction): ReactNode {
  const className = cn(
    "grid min-h-12 place-items-center rounded border px-4 py-3 text-center text-sm font-black uppercase tracking-[0.08em] transition disabled:cursor-not-allowed disabled:opacity-50",
    action.variant === "primary" && "border-amber-200/60 bg-amber-300/20 text-amber-50 shadow-lg shadow-amber-950/20 hover:bg-amber-300/30",
    action.variant === "ghost" && "border-white/10 bg-white/[0.03] text-slate-300 hover:border-white/20 hover:bg-white/[0.07]",
    (!action.variant || action.variant === "default") && "border-cyan-200/40 bg-cyan-300/10 text-cyan-50 hover:bg-cyan-300/20"
  );

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
