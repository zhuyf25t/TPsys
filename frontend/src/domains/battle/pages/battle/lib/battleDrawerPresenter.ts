import type { getDiscussionSummaries } from "../../../../forum/api/forumGateway";
import { markMailAsReadRemote, type MailSummary } from "../../../../mail/api/mailsGateway";
import type { getRatingEntries } from "../../../../governance/api/ratingGateway";
import type { getReplaySummaries } from "../../../../replay/api/replayGateway";
import type { FriendRequestPreviewModel } from "../../../../social/components/friend-requests/friendRequestPreviewPresenter";
import type { QuickPreviewOverlayProps } from "../../../../../shared/ui/QuickPreviewOverlay";
import type { BattleDrawerId } from "./battlePageTypes";

export function buildBattleDrawer(
  activeDrawer: BattleDrawerId,
  replaySummaries: ReturnType<typeof getReplaySummaries>,
  discussionSummaries: ReturnType<typeof getDiscussionSummaries>,
  mailSummaries: MailSummary[],
  ratingEntries: ReturnType<typeof getRatingEntries>,
  friendRequestPreview: FriendRequestPreviewModel,
  mailOwnerHandle?: string | null
): Omit<QuickPreviewOverlayProps, "onClose"> {
  switch (activeDrawer) {
    case "replay":
      return {
        title: "最近回放",
        eyebrow: "Replay",
        detail: "最近几局真实战报。",
        emptyTitle: "暂无回放",
        emptyDetail: "完成一局后，这里会出现真实战报。",
        viewAllPath: "/replay",
        anchor: "left",
        items: replaySummaries.slice(0, 3).map((replay) => ({
          title: replay.title,
          meta: `${replay.resultLabel} / ${replay.finishedAtLabel}`,
          detail: replay.highlightLine
        }))
      };
    case "discussion":
      return {
        title: "最近讨论",
        eyebrow: "Forum",
        detail: "最近几条真实帖子。",
        emptyTitle: "暂无讨论",
        emptyDetail: "没有帖子时保持空状态。",
        viewAllPath: "/discussion",
        anchor: "left",
        items: discussionSummaries.slice(0, 3).map((topic) => ({
          title: topic.title,
          meta: `@${topic.author} / ${topic.updatedAt}`,
          detail: topic.excerpt
        }))
      };
    case "rating":
      return {
        title: "排行预览",
        eyebrow: "Ranking",
        detail: "少量真实评分记录。",
        emptyTitle: "暂无排行",
        emptyDetail: "先完成几局对战，这里才会出现真实评分。",
        viewAllPath: "/rating",
        anchor: "left",
        items: ratingEntries.slice(0, 5).map((entry) => ({
          title: `#${entry.rank} ${entry.handle}`,
          meta: `${entry.score} / ${entry.title}`,
          detail: `${entry.matchCount} 场 / 胜率 ${entry.winRate}`
        }))
      };
    case "mails":
      return {
        title: "最新通知",
        eyebrow: "Mails",
        detail: "最近几条真实通知。",
        emptyTitle: "暂无邮件",
        emptyDetail: "完成一局后，这里会出现新通知。",
        viewAllPath: "/mails",
        anchor: "right",
        items: mailSummaries.slice(0, 3).map((mail) => ({
          title: mail.subject,
          meta: `${mail.senderLabel} / ${mail.receivedLabel}`,
          detail: mail.excerpt,
          onSelect:
            mail.unread && mailOwnerHandle?.trim()
              ? () => {
                  void markMailAsReadRemote(mailOwnerHandle, mail.id);
                }
              : undefined
        }))
      };
    case "social":
      return {
        title: "好友通知",
        eyebrow: "Social",
        detail: friendRequestPreview.detail,
        emptyTitle: friendRequestPreview.emptyTitle,
        emptyDetail: friendRequestPreview.emptyDetail,
        viewAllPath: "/mails",
        anchor: "right",
        items: friendRequestPreview.items
      };
  }
}
