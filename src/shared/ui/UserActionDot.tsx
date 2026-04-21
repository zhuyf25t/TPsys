import { type CSSProperties, useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, isBuiltinAdminHandle, subscribeAuthState } from "../../features/auth/authGateway";
import { getFriendRequestStatus } from "../../features/social/localFriendRequestStore";
import { sendFriendRequest } from "../../features/social/friendRequestGateway";
import { recordContributionAdjustment } from "../../features/governance/localAdminActionStore";

const ADMIN_DELTAS = [1, 2, 5, 10, 20, 50, 100, -1, -5] as const;

interface UserActionDotProps {
  handle: string;
  className?: string;
}

export function UserActionDot({ handle, className }: UserActionDotProps) {
  const currentUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const rootRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [panelStyle, setPanelStyle] = useState<CSSProperties | undefined>();
  const [selectedDelta, setSelectedDelta] = useState<(typeof ADMIN_DELTAS)[number]>(10);
  const [reason, setReason] = useState("");
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const friendStatus = currentUser ? getFriendRequestStatus(currentUser.handle, handle) : null;
  const isAdmin = isBuiltinAdminHandle(currentUser?.handle);
  const isSelf = Boolean(currentUser && normalizeHandle(currentUser.handle) === normalizeHandle(handle));

  useEffect(() => {
    if (!open) {
      return;
    }

    const handlePointerDown = (event: PointerEvent): void => {
      const root = rootRef.current;
      if (!root || root.contains(event.target as Node)) {
        return;
      }

      setOpen(false);
    };

    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") {
        setOpen(false);
      }
    };

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      setStatusMessage(null);
      setIsSubmitting(false);
    }
  }, [open]);

  useLayoutEffect(() => {
    if (!open) {
      return;
    }

    const updatePlacement = (): void => {
      const root = rootRef.current;
      const panel = panelRef.current;
      if (!root || !panel) {
        return;
      }

      const viewportPadding = 12;
      const viewportGap = 10;
      const rootRect = root.getBoundingClientRect();
      const panelRect = panel.getBoundingClientRect();
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;
      const width = Math.max(180, Math.min(320, viewportWidth - viewportPadding * 2));
      const height = Math.min(panelRect.height || 320, viewportHeight - viewportPadding * 2);

      let left = rootRect.right + viewportGap;
      left = Math.max(viewportPadding, Math.min(left, viewportWidth - width - viewportPadding));

      let top = rootRect.bottom + viewportGap;
      top = Math.max(viewportPadding, Math.min(top, viewportHeight - height - viewportPadding));

      setPanelStyle({
        position: "fixed",
        top,
        left,
        width,
        visibility: "visible",
        maxHeight: `calc(100dvh - ${viewportPadding * 2}px)`,
        overflow: "auto"
      });
    };

    updatePlacement();
    const animationFrame = window.requestAnimationFrame(updatePlacement);
    const handleResize = (): void => {
      updatePlacement();
    };

    window.addEventListener("resize", handleResize);
    window.addEventListener("scroll", handleResize, true);

    return () => {
      window.cancelAnimationFrame(animationFrame);
      window.removeEventListener("resize", handleResize);
      window.removeEventListener("scroll", handleResize, true);
    };
  }, [open, statusMessage, reason, selectedDelta, isSubmitting]);

  async function handleFriendRequest(): Promise<void> {
    if (!currentUser || isSelf || isSubmitting) {
      return;
    }

    setIsSubmitting(true);
    try {
      const result = await sendFriendRequest({
        sourceHandle: currentUser.handle,
        targetHandle: handle
      });

      if (result.alreadySent) {
        setStatusMessage("好友申请已经发过了。");
        return;
      }

      if (result.created) {
        setStatusMessage("好友申请已送出。");
        return;
      }

      setStatusMessage("暂时无法发送好友申请。");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleContributionSubmit(): void {
    if (!currentUser || !isAdmin || isSubmitting) {
      return;
    }

    const result = recordContributionAdjustment({
      actorHandle: currentUser.handle,
      targetHandle: handle,
      delta: selectedDelta,
      reason
    });

    if (result.ok) {
      setStatusMessage(`裁决已送达：${formatDelta(selectedDelta)}。`);
      setReason("");
    } else {
      setStatusMessage("裁决没有发出，请再试一次。");
    }
  }

  return (
    <div ref={rootRef} className={`user-action-dot${className ? ` ${className}` : ""}`}>
      <button
        type="button"
        className="user-action-dot__trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`打开 @${handle} 的操作面板`}
        onClick={() => setOpen((value) => !value)}
      >
        <span className="user-action-dot__pill" aria-hidden="true" />
      </button>

      {open ? (
        <div
          ref={panelRef}
          className="user-action-dot__panel"
          role="dialog"
          aria-modal="false"
          aria-label={`@${handle} 的操作面板`}
          style={panelStyle ?? { visibility: "hidden" }}
        >
          <div className="user-action-dot__header">
            <div>
              <p className="eyebrow">用户动作</p>
              <strong>@{handle}</strong>
            </div>
            <Link className="button-link" to={`/profile/${encodeURIComponent(handle)}`}>
              看档案
            </Link>
          </div>

          <section className="user-action-dot__section">
            <p className="user-action-dot__section-title">好友申请</p>
            <p className="user-action-dot__section-copy">
              {currentUser ? "发送好友申请后，对方会在站内信里收到通知。" : "登录后才能发起好友申请。"}
            </p>
            <button
              type="button"
              className="button-link button-link--primary user-action-dot__action"
              onClick={() => {
                void handleFriendRequest();
              }}
              disabled={!currentUser || isSelf || Boolean(friendStatus) || isSubmitting}
            >
              {isSelf ? "这是你自己" : friendStatus ? "已发送" : isSubmitting ? "发送中..." : "发送好友申请"}
            </button>
          </section>

          {isAdmin ? (
            <section className="user-action-dot__section user-action-dot__section--admin">
              <p className="user-action-dot__section-title">贡献裁决</p>
              <div className="user-action-dot__delta-row" role="group" aria-label="贡献调整额度">
                {ADMIN_DELTAS.map((delta) => (
                  <button
                    key={delta}
                    type="button"
                    className={`user-action-dot__delta${selectedDelta === delta ? " user-action-dot__delta--active" : ""}`}
                    onClick={() => setSelectedDelta(delta)}
                  >
                    {formatDelta(delta)}
                  </button>
                ))}
              </div>
              <label className="user-action-dot__field">
                <span>原因</span>
                <textarea
                  value={reason}
                  onChange={(event) => setReason(event.target.value)}
                  rows={3}
                  maxLength={120}
                  placeholder="可选，留一句说明。"
                />
              </label>
              <button type="button" className="button-link button-link--primary user-action-dot__action" onClick={handleContributionSubmit}>
                送出裁决
              </button>
            </section>
          ) : null}

          {statusMessage ? <p className="user-action-dot__status">{statusMessage}</p> : null}
        </div>
      ) : null}
    </div>
  );
}

function formatDelta(delta: number): string {
  return delta > 0 ? `+${delta}` : `${delta}`;
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
