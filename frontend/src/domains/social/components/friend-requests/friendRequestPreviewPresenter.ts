import type { QuickPreviewEntry } from "../../../../shared/ui/QuickPreviewOverlay";
import type { FriendRequestRecord } from "../../api/friendRequestGateway";

export interface FriendRequestPreviewModel {
  badgeCount: number;
  detail: string;
  emptyTitle: string;
  emptyDetail: string;
  items: QuickPreviewEntry[];
}

/** 中文名：构建好友请求preview（buildFriendRequestPreview）。游戏职责：在前端社交域中组织好友请求和本地社交状态，支撑玩家关系互动。 */
export function buildFriendRequestPreview(
  requests: readonly FriendRequestRecord[],
  ownerHandle?: string | null
): FriendRequestPreviewModel {
  const owner = normalizeHandle(ownerHandle ?? "");
  if (!owner) {
    return {
      badgeCount: 0,
      detail: "登录后可同步好友申请状态。",
      emptyTitle: "登录后查看好友申请",
      emptyDetail: "登录后会显示发给你的好友申请和处理状态。",
      items: []
    };
  }

  const visibleRequests = requests
    .filter((request) => isVisibleToOwner(request, owner))
    .sort((left, right) => compareFriendRequests(left, right, owner));
  const badgeCount = visibleRequests.filter((request) => isIncomingPendingRequest(request, owner)).length;

  return {
    badgeCount,
    detail: badgeCount > 0 ? `有 ${badgeCount} 条待处理好友申请。` : "好友申请会显示真实同步状态。",
    emptyTitle: "暂无好友申请",
    emptyDetail: "收到或发出好友申请后会显示在这里。",
    items: visibleRequests.slice(0, 3).map((request) => toPreviewEntry(request, owner))
  };
}

function toPreviewEntry(request: FriendRequestRecord, owner: string): QuickPreviewEntry {
  const direction = resolveDirectionLabel(request, owner);
  return {
    title: `${direction}: ${resolvePeerHandle(request, owner)}`,
    meta: `${resolveStatusLabel(request.status)} / ${formatFriendRequestTime(request)}`,
    detail: `来源: @${request.sourceHandle} / 去向: @${request.targetHandle}`
  };
}

function compareFriendRequests(left: FriendRequestRecord, right: FriendRequestRecord, owner: string): number {
  const priorityDelta = getRequestPriority(left, owner) - getRequestPriority(right, owner);
  if (priorityDelta !== 0) {
    return priorityDelta;
  }

  const rightTime = right.respondedAt ?? right.createdAt;
  const leftTime = left.respondedAt ?? left.createdAt;
  return rightTime - leftTime || right.id.localeCompare(left.id);
}

function getRequestPriority(request: FriendRequestRecord, owner: string): number {
  if (isIncomingPendingRequest(request, owner)) {
    return 0;
  }

  if (request.status === "pending") {
    return 1;
  }

  return 2;
}

function isVisibleToOwner(request: FriendRequestRecord, owner: string): boolean {
  return normalizeHandle(request.sourceHandle) === owner || normalizeHandle(request.targetHandle) === owner;
}

function isIncomingPendingRequest(request: FriendRequestRecord, owner: string): boolean {
  return request.status === "pending" && normalizeHandle(request.targetHandle) === owner;
}

function resolveDirectionLabel(request: FriendRequestRecord, owner: string): string {
  if (normalizeHandle(request.targetHandle) === owner) {
    return "来自";
  }

  return "发往";
}

function resolvePeerHandle(request: FriendRequestRecord, owner: string): string {
  return normalizeHandle(request.targetHandle) === owner ? `@${request.sourceHandle}` : `@${request.targetHandle}`;
}

function resolveStatusLabel(status: FriendRequestRecord["status"]): string {
  switch (status) {
    case "pending":
      return "待处理";
    case "accepted":
      return "已同意";
    case "rejected":
      return "已拒绝";
  }
}

function formatFriendRequestTime(request: FriendRequestRecord): string {
  const timestamp = request.respondedAt ?? request.createdAt;
  const deltaMinutes = Math.max(0, Math.floor((Date.now() - timestamp) / 60000));

  if (deltaMinutes < 1) {
    return "刚刚";
  }

  if (deltaMinutes < 60) {
    return `${deltaMinutes} 分钟前`;
  }

  const deltaHours = Math.floor(deltaMinutes / 60);
  if (deltaHours < 24) {
    return `${deltaHours} 小时前`;
  }

  const deltaDays = Math.floor(deltaHours / 24);
  if (deltaDays < 7) {
    return `${deltaDays} 天前`;
  }

  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(timestamp);
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
