import type { FriendRequestPreviewModel } from "../../friend-requests/components/friendRequestPreviewPresenter";
import type { BattleDrawerId } from "../objects/BattlePageState";
import type { QuickPreviewOverlayProps } from "../../../components/ui/QuickPreviewOverlay";

export interface BattleDrawerReplaySummary {
  finishedAtLabel: string;
  highlightLine: string;
  resultLabel: string;
  title: string;
}

export interface BattleDrawerDiscussionSummary {
  author: string;
  excerpt: string;
  title: string;
  updatedAt: string;
}

export interface BattleDrawerMailSummary {
  excerpt: string;
  id: string;
  receivedLabel: string;
  senderLabel: string;
  subject: string;
  unread: boolean;
}

export interface BattleDrawerRatingEntry {
  handle: string;
  matchCount: number;
  rank: number;
  score: number;
  title: string;
  winRate: string;
}

/** 中文名称：构建战斗抽屉。游戏职责：把非游戏画面预览数据转成抽屉展示模型。 */
export function buildBattleDrawer(
  activeDrawer: BattleDrawerId,
  replaySummaries: BattleDrawerReplaySummary[],
  discussionSummaries: BattleDrawerDiscussionSummary[],
  mailSummaries: BattleDrawerMailSummary[],
  ratingEntries: BattleDrawerRatingEntry[],
  friendRequestPreview: FriendRequestPreviewModel,
  onUnreadMailSelect: (mailId: string) => void
): Omit<QuickPreviewOverlayProps, "onClose"> {
  switch (activeDrawer) {
    case "replay":
      return {
        anchor: "left",
        detail: "最近几局真实战报。",
        emptyDetail: "完成一局后，这里会出现真实战报。",
        emptyTitle: "暂无回放",
        eyebrow: "Replay",
        items: replaySummaries.slice(0, 3).map((replay) => ({
          detail: replay.highlightLine,
          meta: `${replay.resultLabel} / ${replay.finishedAtLabel}`,
          title: replay.title
        })),
        title: "最近回放",
        viewAllPath: "/replay"
      };
    case "discussion":
      return {
        anchor: "left",
        detail: "最近几条真实帖子。",
        emptyDetail: "没有帖子时保持空状态。",
        emptyTitle: "暂无讨论",
        eyebrow: "论坛",
        items: discussionSummaries.slice(0, 3).map((topic) => ({
          detail: topic.excerpt,
          meta: `@${topic.author} / ${topic.updatedAt}`,
          title: topic.title
        })),
        title: "最近讨论",
        viewAllPath: "/discussion"
      };
    case "rating":
      return {
        anchor: "left",
        detail: "少量真实评分记录。",
        emptyDetail: "先完成几局对战，这里才会出现真实评分。",
        emptyTitle: "暂无排行",
        eyebrow: "Ranking",
        items: ratingEntries.slice(0, 5).map((entry) => ({
          detail: `${entry.matchCount} 场 / 胜率 ${entry.winRate}`,
          meta: `${entry.score} / ${entry.title}`,
          title: `#${entry.rank} ${entry.handle}`
        })),
        title: "排行预览",
        viewAllPath: "/rating"
      };
    case "mails":
      return {
        anchor: "right",
        detail: "最近几条真实通知。",
        emptyDetail: "完成一局后，这里会出现新通知。",
        emptyTitle: "暂无邮件",
        eyebrow: "站内信",
        items: mailSummaries.slice(0, 3).map((mail) => ({
          detail: mail.excerpt,
          meta: `${mail.senderLabel} / ${mail.receivedLabel}`,
          onSelect: mail.unread
            ? () => {
                onUnreadMailSelect(mail.id);
              }
            : undefined,
          title: mail.subject
        })),
        title: "最新通知",
        viewAllPath: "/mails"
      };
    case "social":
      return {
        anchor: "right",
        detail: friendRequestPreview.detail,
        emptyDetail: friendRequestPreview.emptyDetail,
        emptyTitle: friendRequestPreview.emptyTitle,
        eyebrow: "Social",
        items: friendRequestPreview.items,
        title: "好友通知",
        viewAllPath: "/friends"
      };
  }
}
