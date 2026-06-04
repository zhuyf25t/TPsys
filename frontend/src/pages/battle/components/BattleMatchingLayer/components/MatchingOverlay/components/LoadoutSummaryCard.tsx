import type { MatchingLoadoutSummary } from "../objects/MatchingLoadoutSummary";

interface LoadoutSummaryCardProps {
  loadout: MatchingLoadoutSummary;
}

export function LoadoutSummaryCard({ loadout }: LoadoutSummaryCardProps) {
  return (
    <div className="rounded border border-white/10 bg-white/[0.04] p-4">
      <div className="flex items-center gap-4">
        <div className="grid h-20 w-20 flex-none place-items-center overflow-hidden rounded-full border border-cyan-200/40 bg-cyan-300/10">
          <img className="h-full w-full object-cover" src={loadout.skinImageSrc} alt={loadout.skinLabel} />
        </div>
        <div className="min-w-0">
          <strong className="block truncate text-xl text-white">{loadout.handle}</strong>
          <span className="mt-1 block text-sm text-slate-300">{loadout.presetLabel}</span>
          <small className="mt-1 block text-xs text-slate-400">{loadout.modeLabel}</small>
          <small className="block text-xs text-slate-400">{loadout.primary}</small>
        </div>
      </div>
      <div className="mt-4 flex flex-wrap gap-2">
        {loadout.skills.map((skill) => (
          <span key={skill} className="rounded border border-cyan-200/20 bg-cyan-300/10 px-2 py-1 text-xs font-bold text-cyan-100">
            {skill}
          </span>
        ))}
      </div>
    </div>
  );
}
