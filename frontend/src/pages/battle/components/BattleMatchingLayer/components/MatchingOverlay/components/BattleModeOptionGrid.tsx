import type { BattlePlayModeId, BattlePlayModeOption } from "../../../../../../../runtime/battle/microservices/world/services/BattleArenaCatalog";
import { cn } from "../../../../../../../components/ui/classNames";

interface BattleModeOptionGridProps {
  options: readonly BattlePlayModeOption[];
  selectedBattleModeId: BattlePlayModeId;
  disabled: boolean;
  onBattleModeChange: (modeId: BattlePlayModeId) => void;
}

export function BattleModeOptionGrid({
  options,
  selectedBattleModeId,
  disabled,
  onBattleModeChange
}: BattleModeOptionGridProps) {
  return (
    <div className="grid gap-3 sm:grid-cols-2" aria-label="鐜╂硶閫夋嫨">
      {options.map((option) => {
        const active = option.modeId === selectedBattleModeId;
        return (
          <button
            key={option.modeId}
            type="button"
            className={cn(
              "rounded border p-4 text-left transition disabled:cursor-not-allowed disabled:opacity-50",
              active
                ? "border-amber-200/70 bg-amber-300/15 text-amber-50"
                : "border-white/10 bg-white/[0.04] text-slate-200 hover:border-cyan-300/40 hover:bg-cyan-300/10"
            )}
            aria-pressed={active}
            disabled={disabled}
            onClick={() => onBattleModeChange(option.modeId)}
          >
            <strong className="block text-lg">{option.label}</strong>
            <span className="mt-1 block text-sm text-slate-400">{option.mapLabel}</span>
          </button>
        );
      })}
    </div>
  );
}
