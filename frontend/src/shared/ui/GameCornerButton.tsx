import { useId, useState } from "react";
import { Link } from "react-router-dom";
import { cn } from "./classNames";

type CornerPlacement = "top" | "bottom";

export type GameCornerIconKey = "replay" | "discussion" | "ranking" | "mails" | "social" | "back";

const ICON_SRC: Record<GameCornerIconKey, string> = {
  replay: "/pics/icons/replay.png",
  discussion: "/pics/icons/discussion.png",
  ranking: "/pics/icons/ranking.png",
  mails: "/pics/icons/mails.png",
  social: "/pics/icons/social.png",
  back: "/pics/icons/back.png"
};

interface GameCornerButtonProps {
  label: string;
  iconKey: GameCornerIconKey;
  onClick?: () => void;
  to?: string;
  tooltipPlacement: CornerPlacement;
  badgeCount?: number;
}

/** 中文名：角落快捷按钮（GameCornerButton）。游戏职责：提供大厅角落的图片按钮和悬浮提示。 */
export function GameCornerButton({ label, iconKey, onClick, to, tooltipPlacement, badgeCount }: GameCornerButtonProps) {
  const tooltipId = useId();
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const visible = hovered || focused;
  const visibleBadgeCount = Math.max(0, badgeCount ?? 0);
  const hasBadge = visibleBadgeCount > 0;
  const badgeLabel = hasBadge ? `${label} unread ${visibleBadgeCount}` : undefined;
  const badgeText = visibleBadgeCount > 99 ? "99+" : String(visibleBadgeCount);
  const rootClassName = "relative inline-grid h-[70px] w-[92px] flex-none place-items-center bg-transparent p-0 text-inherit no-underline";
  const sharedProps = {
    className: rootClassName,
    onPointerEnter: () => setHovered(true),
    onPointerLeave: () => setHovered(false),
    onFocus: () => setFocused(true),
    onBlur: () => setFocused(false),
    "aria-label": label,
    "aria-describedby": tooltipId
  };
  const content = (
    <>
      <span
        className={cn(
          "relative grid h-full w-full place-items-center transition duration-150",
          visible && "-translate-y-0.5 scale-105 brightness-110 saturate-110"
        )}
        aria-hidden="true"
      >
        <span className="relative grid h-[70px] w-[92px] place-items-center">
          <img
            className={cn("h-[70px] w-[92px] object-contain drop-shadow-[0_8px_10px_rgba(0,0,0,0.34)]", iconKey === "social" && "translate-y-1")}
            src={ICON_SRC[iconKey]}
            alt=""
            draggable={false}
          />
          {hasBadge ? (
            <span
              className="absolute right-[13px] top-2.5 grid h-4 min-w-[18px] place-items-center rounded-full border border-orange-100/90 bg-red-600 px-1.5 text-[9px] font-black leading-none text-orange-50 shadow-[0_0_0_2px_rgba(15,7,5,0.72),0_4px_9px_rgba(167,0,23,0.42)]"
              aria-label={badgeLabel}
            >
              {badgeText}
            </span>
          ) : null}
        </span>
      </span>
      <span
        id={tooltipId}
        className={cn(
          "pointer-events-none absolute left-1/2 z-20 min-h-[30px] -translate-x-1/2 whitespace-nowrap rounded border border-amber-200/20 bg-zinc-950/90 px-4 py-2 text-[11px] font-bold uppercase tracking-wider text-orange-50 opacity-0 shadow-xl transition duration-150",
          tooltipPlacement === "top" ? "bottom-[calc(100%+5px)]" : "top-[calc(100%+5px)]",
          visible && "opacity-100",
          visible && tooltipPlacement === "top" && "-translate-y-0.5",
          visible && tooltipPlacement === "bottom" && "translate-y-0.5"
        )}
        role="tooltip"
      >
        {label}
      </span>
    </>
  );

  return to ? (
    <Link to={to} {...sharedProps}>
      {content}
    </Link>
  ) : (
    <button type="button" onClick={onClick} {...sharedProps}>
      {content}
    </button>
  );
}
