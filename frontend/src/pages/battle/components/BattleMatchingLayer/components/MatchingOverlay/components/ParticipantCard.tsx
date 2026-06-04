import type { MatchmakingQueueState } from "../../../../../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import { cn } from "../../../../../../../components/ui/classNames";

interface ParticipantCardProps {
  participant: MatchmakingQueueState["participants"][number];
  isLocalPlayer: boolean;
  localPlayerLabel: string;
  remotePlayerLabel: string;
  pendingRatingLabel: string;
  ratingLabelPrefix: string;
}

export function ParticipantCard({
  participant,
  isLocalPlayer,
  localPlayerLabel,
  remotePlayerLabel,
  pendingRatingLabel,
  ratingLabelPrefix
}: ParticipantCardProps) {
  return (
    <article className={cn("rounded border px-3 py-2", isLocalPlayer ? "border-amber-200/50 bg-amber-300/10" : "border-white/10 bg-white/[0.04]")}>
      <strong className="block text-sm text-white">{participant.handle}</strong>
      <span className="text-xs text-slate-400">{isLocalPlayer ? localPlayerLabel : remotePlayerLabel}</span>
      <small className="ml-2 text-xs text-slate-500">
        {participant.rating === undefined ? pendingRatingLabel : `${ratingLabelPrefix} ${participant.rating}`}
      </small>
    </article>
  );
}
