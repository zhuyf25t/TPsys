import { QuickPreviewOverlay } from "../../../components/ui/QuickPreviewOverlay";
import {
  buildBattleDrawer,
  type BattleDrawerDiscussionSummary,
  type BattleDrawerMailSummary,
  type BattleDrawerRatingEntry,
  type BattleDrawerReplaySummary
} from "../functions/buildBattleDrawer";
import type { FriendRequestPreviewModel } from "../../friend-requests/components/friendRequestPreviewPresenter";
import type { BattleDrawerId } from "../objects/BattlePageState";

interface BattleDrawerLayerProps {
  activeDrawer: BattleDrawerId | null;
  replaySummaries: BattleDrawerReplaySummary[];
  discussionSummaries: BattleDrawerDiscussionSummary[];
  mailSummaries: BattleDrawerMailSummary[];
  ratingEntries: BattleDrawerRatingEntry[];
  friendRequestPreview: FriendRequestPreviewModel;
  onUnreadMailSelect: (mailId: string) => void;
  onClose: () => void;
}

export function BattleDrawerLayer({
  activeDrawer,
  replaySummaries,
  discussionSummaries,
  mailSummaries,
  ratingEntries,
  friendRequestPreview,
  onUnreadMailSelect,
  onClose
}: BattleDrawerLayerProps) {
  if (!activeDrawer) {
    return null;
  }

  return (
    <QuickPreviewOverlay
      {...buildBattleDrawer(
        activeDrawer,
        replaySummaries,
        discussionSummaries,
        mailSummaries,
        ratingEntries,
        friendRequestPreview,
        onUnreadMailSelect
      )}
      onClose={onClose}
    />
  );
}
