import { useId, useState } from "react";
import { Link } from "react-router-dom";

type CornerPlacement = "top" | "bottom";

export type GameCornerIconKey = "replay" | "discussion" | "ranking" | "mails" | "social" | "back";

interface GameCornerButtonProps {
  label: string;
  iconKey: GameCornerIconKey;
  onClick?: () => void;
  to?: string;
  tooltipPlacement: CornerPlacement;
  badgeCount?: number;
}

/** 中文名称：角落快捷按钮（GameCornerButton）。游戏职责：提供大厅角落的图片按钮和悬浮提示。 */
export function GameCornerButton({ label, iconKey, onClick, to, tooltipPlacement, badgeCount }: GameCornerButtonProps) {
  const tooltipId = useId();
  const [hovered, setHovered] = useState(false);
  const [focused, setFocused] = useState(false);
  const visible = hovered || focused;
  const visibleBadgeCount = Math.max(0, badgeCount ?? 0);
  const hasBadge = visibleBadgeCount > 0;
  const badgeLabel = hasBadge ? `${label} unread ${visibleBadgeCount}` : undefined;
  const badgeText = visibleBadgeCount > 99 ? "99+" : String(visibleBadgeCount);
  const className = `game-corner-button game-corner-button--${tooltipPlacement}${visible ? " is-active" : ""}`;
  const sharedProps = {
    "aria-describedby": tooltipId,
    "aria-label": label,
    className,
    onBlur: () => setFocused(false),
    onFocus: () => setFocused(true),
    onPointerEnter: () => setHovered(true),
    onPointerLeave: () => setHovered(false)
  };
  const content = (
    <>
      <span className="game-corner-button__icon" aria-hidden="true">
        <span className="game-corner-button__plate">
          <span className={`game-corner-button__glyph game-corner-button__glyph--${iconKey}`} />
          {hasBadge ? (
            <span className="game-corner-button__badge" aria-label={badgeLabel}>
              {badgeText}
            </span>
          ) : null}
        </span>
      </span>
      <span id={tooltipId} className={`game-corner-button__tooltip game-corner-button__tooltip--${tooltipPlacement}`} role="tooltip">
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
