import { useId, useState, type CSSProperties } from "react";
import { Link } from "react-router-dom";

type CornerPlacement = "top" | "bottom";

export type GameCornerIconKey = "replay" | "discussion" | "ranking" | "mails" | "social" | "back";

const ICON_POSITIONS: Record<GameCornerIconKey, string> = {
  replay: "0% 0%",
  discussion: "33.333% 0%",
  ranking: "66.666% 0%",
  mails: "100% 0%",
  social: "0% 50%",
  back: "100% 50%"
};

interface GameCornerButtonProps {
  label: string;
  iconKey: GameCornerIconKey;
  onClick?: () => void;
  to?: string;
  tooltipPlacement: CornerPlacement;
  badgeCount?: number;
}

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
  const iconStyle = {
    "--corner-icon-position": ICON_POSITIONS[iconKey]
  } as CSSProperties;
  const sharedProps = {
    className,
    onPointerEnter: () => setHovered(true),
    onPointerLeave: () => setHovered(false),
    onFocus: () => setFocused(true),
    onBlur: () => setFocused(false),
    "aria-label": label,
    "aria-describedby": tooltipId
  };

  return to ? (
    <Link to={to} {...sharedProps}>
      <span className="game-corner-button__icon" aria-hidden="true">
        <span className="game-corner-button__plate">
          <span className={`game-corner-button__glyph game-corner-button__glyph--${iconKey}`} style={iconStyle} />
          {hasBadge ? <span className="game-corner-button__badge" aria-label={badgeLabel}>{badgeText}</span> : null}
        </span>
      </span>
      <span id={tooltipId} className={`game-corner-button__tooltip game-corner-button__tooltip--${tooltipPlacement}`} role="tooltip">
        {label}
      </span>
    </Link>
  ) : (
    <button
      type="button"
      className={className}
      onClick={onClick}
      onPointerEnter={sharedProps.onPointerEnter}
      onPointerLeave={sharedProps.onPointerLeave}
      onFocus={sharedProps.onFocus}
      onBlur={sharedProps.onBlur}
      aria-label={label}
      aria-describedby={tooltipId}
    >
      <span className="game-corner-button__icon" aria-hidden="true">
        <span className="game-corner-button__plate">
          <span className={`game-corner-button__glyph game-corner-button__glyph--${iconKey}`} style={iconStyle} />
          {hasBadge ? <span className="game-corner-button__badge" aria-label={badgeLabel}>{badgeText}</span> : null}
        </span>
      </span>
      <span id={tooltipId} className={`game-corner-button__tooltip game-corner-button__tooltip--${tooltipPlacement}`} role="tooltip">
        {label}
      </span>
    </button>
  );
}
