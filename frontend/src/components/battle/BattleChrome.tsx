import type { ReactNode } from "react";
import { cn } from "../../shared/ui/classNames";
import { GameCornerButton, type GameCornerIconKey } from "../../shared/ui/GameCornerButton";

export interface BattleChromeButton {
  label: string;
  iconKey: GameCornerIconKey;
  onClick: () => void;
  badgeCount?: number;
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

/** 中文名：战斗壳层（BattleChrome）。游戏职责：承载 Phaser runtime、HUD、等待层和结果层。 */
export function BattleChrome({
  phase,
  leftButtons,
  rightButtons,
  matchingOverlay,
  settlementOverlay,
  drawerOverlay,
  children
}: BattleChromeProps) {
  const showEscape = phase !== "matching";
  const showCornerButtons = leftButtons.length > 0 || rightButtons.length > 0;

  return (
    <section className={cn("relative min-h-screen overflow-hidden bg-slate-950 text-slate-100", phase === "matching" && "bg-slate-950")}>
      <div className="absolute inset-0">{children}</div>

      {showEscape ? (
        <div className="absolute left-4 top-4 z-30">
          <GameCornerButton label="返回大厅" iconKey="back" to="/" tooltipPlacement="bottom" />
        </div>
      ) : null}

      {showCornerButtons ? (
        <>
          <div className="absolute left-4 top-4 z-30 flex gap-2" aria-label="信息入口">
            {leftButtons.map((button) => (
              <GameCornerButton
                key={button.label}
                iconKey={button.iconKey}
                onClick={button.onClick}
                label={button.label}
                tooltipPlacement="bottom"
                badgeCount={button.badgeCount}
              />
            ))}
          </div>

          <div className="absolute bottom-4 right-4 z-30 flex gap-2" aria-label="消息入口">
            {rightButtons.map((button) => (
              <GameCornerButton
                key={button.label}
                iconKey={button.iconKey}
                onClick={button.onClick}
                label={button.label}
                tooltipPlacement="top"
                badgeCount={button.badgeCount}
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
