import type { ReactNode } from "react";
import { GameCornerButton, type GameCornerIconKey } from "./GameCornerButton";

export interface BattleChromeButton {
  label: string;
  iconKey: GameCornerIconKey;
  onClick: () => void;
}

interface BattleChromeProps {
  phase: "matching" | "playing" | "settled";
  leftButtons: BattleChromeButton[];
  rightButtons: BattleChromeButton[];
  matchingOverlay?: ReactNode;
  settlementOverlay?: ReactNode;
  drawerOverlay?: ReactNode;
  children: ReactNode;
}

export function BattleChrome({
  phase,
  leftButtons,
  rightButtons,
  matchingOverlay,
  settlementOverlay,
  drawerOverlay,
  children
}: BattleChromeProps) {
  const showCornerButtons = false;

  return (
    <section className={`arena-shell arena-shell--${phase}`}>
      <div className="arena-shell__viewport">{children}</div>

      <div className="arena-shell__escape">
        <GameCornerButton label="返回大厅" iconKey="back" to="/" tooltipPlacement="bottom" />
      </div>

      {showCornerButtons ? (
        <>
          <div className="arena-shell__cluster arena-shell__cluster--left" aria-label="信息入口">
            {leftButtons.map((button) => (
              <GameCornerButton
                key={button.label}
                iconKey={button.iconKey}
                onClick={button.onClick}
                label={button.label}
                tooltipPlacement="bottom"
              />
            ))}
          </div>

          <div className="arena-shell__cluster arena-shell__cluster--right" aria-label="消息入口">
            {rightButtons.map((button) => (
              <GameCornerButton
                key={button.label}
                iconKey={button.iconKey}
                onClick={button.onClick}
                label={button.label}
                tooltipPlacement="top"
              />
            ))}
          </div>
        </>
      ) : null}

      {matchingOverlay}
      {settlementOverlay}
      {drawerOverlay}
    </section>
  );
}
