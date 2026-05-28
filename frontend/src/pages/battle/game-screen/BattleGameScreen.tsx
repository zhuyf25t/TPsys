import type { RefObject } from "react";
import type { BattlePageTransientNotice } from "../hooks/useBattlePageRuntime";

interface BattleGameScreenProps {
  entryBlockNotice: string | null;
  transientNotice: BattlePageTransientNotice | null;
  runtimeRootRef: RefObject<HTMLDivElement | null>;
  hudRootRef: RefObject<HTMLDivElement | null>;
}

/** 中文名：战斗游戏画面（BattleGameScreen）。游戏职责：只承载 Phaser runtime 与 HUD 渲染根节点。 */
export function BattleGameScreen({
  entryBlockNotice,
  transientNotice,
  runtimeRootRef,
  hudRootRef
}: BattleGameScreenProps) {
  return (
    <>
      {entryBlockNotice ? null : transientNotice ? (
        <div
          key={transientNotice.id}
          className="absolute left-1/2 top-6 z-30 -translate-x-1/2 rounded border border-cyan-200/40 bg-slate-950/90 px-4 py-2 text-sm font-bold text-cyan-100 shadow-lg"
          role="status"
          aria-live="polite"
        >
          {transientNotice.message}
        </div>
      ) : null}
      <div ref={runtimeRootRef} className="h-full w-full" aria-label="battle runtime" />
      <div id="hud-root" ref={hudRootRef} className="pointer-events-none absolute inset-0 z-10" />
    </>
  );
}
