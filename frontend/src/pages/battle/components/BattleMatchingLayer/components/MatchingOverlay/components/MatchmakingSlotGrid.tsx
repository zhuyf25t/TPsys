import type { buildMatchmakingSlots } from "../../../../../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import { cn } from "../../../../../../../components/ui/classNames";

type MatchmakingSlot = ReturnType<typeof buildMatchmakingSlots>[number];

interface MatchmakingSlotGridProps {
  slots: MatchmakingSlot[];
}

export function MatchmakingSlotGrid({ slots }: MatchmakingSlotGridProps) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3" aria-label="Matchmaking slots">
      {slots.map((slot) => (
        <article
          key={slot.slotLabel}
          className={cn(
            "min-h-24 rounded border p-3",
            slot.isLocalPlayer
              ? "border-amber-200/60 bg-amber-300/15"
              : slot.isInteractive
                ? "border-cyan-200/30 bg-cyan-300/10"
                : "border-white/10 bg-white/[0.03]"
          )}
          aria-current={slot.isLocalPlayer ? "true" : undefined}
          aria-disabled={slot.isInteractive ? undefined : "true"}
        >
          <span className="text-xs font-black uppercase tracking-[0.16em] text-slate-400">{slot.slotLabel}</span>
          <strong className="mt-2 block text-white">{slot.title}</strong>
          <small className="mt-1 block text-xs text-slate-400">{slot.detail}</small>
        </article>
      ))}
    </div>
  );
}
