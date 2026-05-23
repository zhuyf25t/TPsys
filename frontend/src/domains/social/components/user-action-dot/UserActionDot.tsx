import { type CSSProperties, useEffect, useLayoutEffect, useRef, useState, useSyncExternalStore } from "react";
import { createPortal } from "react-dom";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, isBuiltinAdminHandle, subscribeAuthState } from "../../../identity/api/authGateway";
import { recordContributionAdjustment, submitGovernanceReviewNotification } from "../../../governance/api/governanceGateway";
import { buildBotProfilePath, isBotLikeHandle } from "../../../bots/objects/botHandle";
import { getFriendRequestStatus, sendFriendRequest } from "../../api/friendRequestGateway";
import { cn } from "../../../../shared/ui/classNames";

const ADMIN_DELTAS = [1, 2, 5, 10, 20, 50, 100, -1] as const;
const ADMIN_REASON_PRESETS = ["战绩贡献", "内容整理", "问题反馈", "社区帮助"] as const;
const PANEL_WIDTH = 320;
const PANEL_OFFSET_X = 12;
const PANEL_OFFSET_Y = 8;
const VIEWPORT_PADDING = 12;
const DEFAULT_PANEL_HEIGHT = 320;
const BOT_SUGGESTION_MAX_LENGTH = 220;

const primaryButton =
  "inline-flex h-9 items-center justify-center rounded-md bg-emerald-600 px-3 text-xs font-semibold text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50";
const secondaryButton =
  "inline-flex h-9 items-center justify-center rounded-md border border-slate-300 bg-white px-3 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50";
const fieldClassName =
  "mt-1 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20";

interface PanelAnchor {
  point: {
    x: number;
    y: number;
  };
  rect: {
    left: number;
    right: number;
    top: number;
    bottom: number;
  };
}

interface PanelSize {
  width: number;
  height: number;
}

interface UserActionDotProps {
  handle: string;
  className?: string;
  sourceLabel?: string;
  sourcePath?: string;
}

/** 中文名称：用户操作点。游戏职责：组织好友请求、本地社交状态和管理员贡献调整入口。 */
export function UserActionDot({ handle, className, sourceLabel, sourcePath }: UserActionDotProps) {
  const currentUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const [open, setOpen] = useState(false);
  const [panelAnchor, setPanelAnchor] = useState<PanelAnchor | null>(null);
  const [panelStyle, setPanelStyle] = useState<CSSProperties | undefined>();
  const [selectedDelta, setSelectedDelta] = useState<(typeof ADMIN_DELTAS)[number]>(10);
  const [customDelta, setCustomDelta] = useState("");
  const [reasonPreset, setReasonPreset] = useState<(typeof ADMIN_REASON_PRESETS)[number]>(ADMIN_REASON_PRESETS[0]);
  const [reason, setReason] = useState("");
  const [botSuggestionBody, setBotSuggestionBody] = useState("");
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [isFriendSubmitting, setIsFriendSubmitting] = useState(false);
  const [isContributionSubmitting, setIsContributionSubmitting] = useState(false);
  const [isBotSuggestionSubmitting, setIsBotSuggestionSubmitting] = useState(false);

  const friendStatus = currentUser ? getFriendRequestStatus(currentUser.handle, handle) : null;
  const isAdmin = isBuiltinAdminHandle(currentUser?.handle);
  const isSelf = Boolean(currentUser && normalizeHandle(currentUser.handle) === normalizeHandle(handle));
  const isBotTarget = isBotLikeHandle(handle);
  const effectiveDelta = resolveEffectiveDelta(selectedDelta, customDelta);
  const sourceContext = resolveSourceContext(sourceLabel, sourcePath);
  const portalTarget = typeof document === "undefined" ? null : document.body;
  const profilePath = isBotTarget ? buildBotProfilePath(handle) : `/profile/${encodeURIComponent(handle)}`;

  const closePanel = (): void => {
    setOpen(false);
    setPanelAnchor(null);
  };

  useEffect(() => {
    if (!open) {
      return;
    }

    const handlePointerDown = (event: PointerEvent): void => {
      const root = rootRef.current;
      const panel = panelRef.current;
      const target = event.target;

      if (!(target instanceof Node)) {
        closePanel();
        return;
      }

      if ((root && root.contains(target)) || (panel && panel.contains(target))) {
        return;
      }

      closePanel();
    };

    const handleKeyDown = (event: KeyboardEvent): void => {
      if (event.key === "Escape") {
        closePanel();
      }
    };

    document.addEventListener("pointerdown", handlePointerDown);
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("pointerdown", handlePointerDown);
      document.removeEventListener("keydown", handleKeyDown);
    };
  }, [open]);

  useLayoutEffect(() => {
    if (!open || !panelAnchor) {
      setPanelStyle(undefined);
      return;
    }

    let frameId = 0;

    const updatePanelPosition = (): void => {
      const panel = panelRef.current;
      if (!panel) {
        return;
      }

      const measuredRect = panel.getBoundingClientRect();
      const nextStyle = computePanelStyle(panelAnchor, {
        width: measuredRect.width || PANEL_WIDTH,
        height: measuredRect.height || DEFAULT_PANEL_HEIGHT
      });
      setPanelStyle((currentStyle) => (samePanelStyle(currentStyle, nextStyle) ? currentStyle : nextStyle));
    };

    const schedulePanelPositionUpdate = (): void => {
      if (frameId) {
        window.cancelAnimationFrame(frameId);
      }

      frameId = window.requestAnimationFrame(() => {
        frameId = 0;
        updatePanelPosition();
      });
    };

    updatePanelPosition();

    const resizeObserver = typeof ResizeObserver === "undefined" ? null : new ResizeObserver(() => schedulePanelPositionUpdate());
    if (resizeObserver && panelRef.current) {
      resizeObserver.observe(panelRef.current);
    }

    window.addEventListener("resize", schedulePanelPositionUpdate);

    return () => {
      if (frameId) {
        window.cancelAnimationFrame(frameId);
      }
      resizeObserver?.disconnect();
      window.removeEventListener("resize", schedulePanelPositionUpdate);
    };
  }, [open, panelAnchor]);

  async function handleFriendRequest(): Promise<void> {
    if (!currentUser || isSelf || isFriendSubmitting) {
      return;
    }

    setIsFriendSubmitting(true);
    try {
      const result = await sendFriendRequest({
        sourceHandle: currentUser.handle,
        targetHandle: handle
      });

      setStatusMessage(result.delivery === "failed" ? "好友申请发送失败。" : result.alreadySent ? "好友申请已经发送过了。" : "好友申请已发送。");
    } finally {
      setIsFriendSubmitting(false);
    }
  }

  async function handleBotSuggestionSubmit(): Promise<void> {
    if (!currentUser || !isBotTarget || isBotSuggestionSubmitting || !botSuggestionBody.trim()) {
      return;
    }

    setIsBotSuggestionSubmitting(true);
    try {
      const result = await submitGovernanceReviewNotification({
        actorHandle: currentUser.handle,
        kind: "bot_suggestion",
        targetType: "bot",
        targetId: handle,
        targetTitle: handle,
        targetPath: buildBotProfilePath(handle),
        body: botSuggestionBody.trim()
      });

      setStatusMessage(result.ok ? "建议已提交。" : "建议提交失败。");
      if (result.ok) {
        setBotSuggestionBody("");
      }
    } finally {
      setIsBotSuggestionSubmitting(false);
    }
  }

  async function handleContributionSubmit(): Promise<void> {
    if (!currentUser || !isAdmin || isContributionSubmitting || effectiveDelta === null || effectiveDelta === 0) {
      return;
    }

    const finalReason = buildContributionReason(reasonPreset, reason);
    setIsContributionSubmitting(true);
    try {
      const result = await recordContributionAdjustment({
        actorHandle: currentUser.handle,
        targetHandle: handle,
        delta: effectiveDelta,
        reason: finalReason,
        sourceLabel: sourceContext.label,
        sourcePath: sourceContext.path
      });

      setStatusMessage(result.delivery === "failed" ? "贡献调整失败。" : `已发送调整：${formatDelta(effectiveDelta)}。`);
      if (result.ok) {
        setReason("");
        setCustomDelta("");
      }
    } finally {
      setIsContributionSubmitting(false);
    }
  }

  const panel = (
    <div
      ref={panelRef}
      className="z-50 overflow-auto rounded-lg border border-slate-200 bg-white p-4 text-slate-800 shadow-2xl"
      role="dialog"
      aria-modal="false"
      aria-label={`@${handle} 的操作面板`}
      style={panelStyle ?? { visibility: "hidden" }}
    >
      <div className="flex items-start justify-between gap-3 border-b border-slate-200 pb-3">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">{isBotTarget ? "机器人目标" : "目标用户"}</p>
          <strong className="mt-1 block truncate text-lg text-slate-950">@{handle}</strong>
        </div>
        <Link className={secondaryButton} to={profilePath}>
          查看资料
        </Link>
      </div>

      <div className="mt-3 rounded-lg bg-slate-50 p-3 text-xs text-slate-600">
        <span className="font-semibold text-slate-500">来源</span>
        {sourceContext.path ? (
          <a className="mt-1 block break-all font-semibold text-emerald-700 hover:text-emerald-800" href={sourceContext.path}>
            {sourceContext.label}
          </a>
        ) : (
          <strong className="mt-1 block">{sourceContext.label}</strong>
        )}
      </div>

      {!isBotTarget ? (
        <section className="mt-4 border-t border-slate-200 pt-4">
          <p className="text-sm font-semibold text-slate-950">好友申请</p>
          <p className="mt-1 text-xs leading-5 text-slate-600">{currentUser ? "发送好友申请后，对方会在站内收到通知。" : "登录后才能发送好友申请。"}</p>
          <button
            type="button"
            className={cn(primaryButton, "mt-3 w-full")}
            onClick={() => {
              void handleFriendRequest();
            }}
            disabled={!currentUser || isSelf || Boolean(friendStatus) || isFriendSubmitting}
          >
            {isSelf ? "这是你自己" : friendStatus ? "已经发送" : isFriendSubmitting ? "发送中..." : "发送好友申请"}
          </button>
        </section>
      ) : (
        <section className="mt-4 border-t border-slate-200 pt-4">
          <p className="text-sm font-semibold text-slate-950">提交 bot 建议</p>
          <p className="mt-1 text-xs leading-5 text-slate-600">对机器人的意见会进入管理员通知链路。</p>
          <label className="mt-3 block text-xs font-semibold text-slate-600">
            <span>建议内容</span>
            <textarea
              className={fieldClassName}
              value={botSuggestionBody}
              onChange={(event) => setBotSuggestionBody(event.target.value)}
              rows={3}
              maxLength={BOT_SUGGESTION_MAX_LENGTH}
              placeholder="填写你对这个机器人目标的建议。"
            />
          </label>
          <button
            type="button"
            className={cn(primaryButton, "mt-3 w-full")}
            onClick={() => {
              void handleBotSuggestionSubmit();
            }}
            disabled={!currentUser || isBotSuggestionSubmitting || !botSuggestionBody.trim()}
          >
            {isBotSuggestionSubmitting ? "提交中..." : "提交 bot 建议"}
          </button>
        </section>
      )}

      {isAdmin ? (
        <section className="mt-4 border-t border-slate-200 pt-4">
          <p className="text-sm font-semibold text-slate-950">处理贡献</p>
          <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="贡献调整额度">
            {ADMIN_DELTAS.map((delta) => (
              <button
                key={delta}
                type="button"
                className={cn(
                  "rounded-full border px-3 py-1 text-xs font-semibold transition",
                  selectedDelta === delta ? "border-emerald-500 bg-emerald-600 text-white" : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                )}
                onClick={() => {
                  setSelectedDelta(delta);
                }}
              >
                {formatDelta(delta)}
              </button>
            ))}
          </div>

          <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="贡献调整原因预设">
            {ADMIN_REASON_PRESETS.map((preset) => (
              <button
                key={preset}
                type="button"
                className={cn(
                  "rounded-full border px-3 py-1 text-xs font-semibold transition",
                  reasonPreset === preset ? "border-slate-700 bg-slate-800 text-white" : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                )}
                onClick={() => {
                  setReasonPreset(preset);
                }}
              >
                {preset}
              </button>
            ))}
          </div>

          <label className="mt-3 block text-xs font-semibold text-slate-600">
            <span>原因说明</span>
            <textarea className={fieldClassName} value={reason} onChange={(event) => setReason(event.target.value)} rows={3} maxLength={120} placeholder="可选补充说明。" />
          </label>

          <label className="mt-3 block text-xs font-semibold text-slate-600">
            <span>自定义额度</span>
            <input className={fieldClassName} value={customDelta} onChange={(event) => setCustomDelta(event.target.value)} inputMode="numeric" placeholder="可选整数。" />
          </label>

          {customDelta.trim() && (effectiveDelta === null || effectiveDelta === 0) ? <p className="mt-2 text-xs font-semibold text-rose-700">自定义额度必须是非 0 整数。</p> : null}

          <button
            type="button"
            className={cn(primaryButton, "mt-3 w-full")}
            onClick={() => {
              void handleContributionSubmit();
            }}
            disabled={isContributionSubmitting || effectiveDelta === null || effectiveDelta === 0}
          >
            {isContributionSubmitting ? "提交中..." : "提交调整"}
          </button>
        </section>
      ) : null}

      {statusMessage ? <p className="mt-3 rounded-lg bg-slate-50 p-3 text-xs font-semibold text-slate-700">{statusMessage}</p> : null}
    </div>
  );

  return (
    <div ref={rootRef} className={cn("inline-flex", className)}>
      <button
        ref={triggerRef}
        type="button"
        className="inline-flex h-5 w-5 items-center justify-center rounded-full border border-slate-300 bg-white shadow-sm transition hover:border-emerald-500 hover:bg-emerald-50"
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={`打开 @${handle} 的操作面板`}
        onClick={(event) => {
          event.stopPropagation();
          if (open) {
            closePanel();
            return;
          }

          const nextAnchor = createPanelAnchor(event.currentTarget.getBoundingClientRect(), {
            x: event.clientX,
            y: event.clientY
          });
          setPanelAnchor(nextAnchor);
          setOpen(true);
        }}
      >
        <span className="h-2 w-2 rounded-full bg-emerald-500" aria-hidden="true" />
      </button>

      {open && portalTarget ? createPortal(panel, portalTarget) : null}
    </div>
  );
}

function formatDelta(delta: number): string {
  return delta > 0 ? `+${delta}` : `${delta}`;
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}

function resolveEffectiveDelta(selectedDelta: number, customDelta: string): number | null {
  const normalized = customDelta.trim();
  if (!normalized) {
    return selectedDelta;
  }

  const value = Number(normalized);
  if (!Number.isFinite(value) || !Number.isInteger(value)) {
    return null;
  }

  return value;
}

function buildContributionReason(reasonPreset: string, reason: string): string {
  const normalizedReason = reason.trim();
  return normalizedReason ? `${reasonPreset}: ${normalizedReason}` : reasonPreset;
}

function resolveSourceContext(sourceLabel?: string, sourcePath?: string): { label: string; path: string } {
  const path = sourcePath?.trim() || getDocumentLocation();
  const label = sourceLabel?.trim() || getDocumentTitle() || path || "当前页面";
  return { label, path };
}

function getDocumentTitle(): string {
  return typeof document === "undefined" ? "" : document.title.trim();
}

function getDocumentLocation(): string {
  return typeof window === "undefined" ? "" : window.location.pathname + window.location.search + window.location.hash;
}

function createPanelAnchor(triggerRect: DOMRect, clickPoint?: { x: number; y: number }): PanelAnchor {
  const fallbackPoint = {
    x: triggerRect.left + triggerRect.width / 2,
    y: triggerRect.top + triggerRect.height / 2
  };
  const hasClickPoint =
    clickPoint !== undefined &&
    clickPoint.x >= triggerRect.left &&
    clickPoint.x <= triggerRect.right &&
    clickPoint.y >= triggerRect.top &&
    clickPoint.y <= triggerRect.bottom;

  return {
    point: hasClickPoint
      ? {
          x: clampNumber(clickPoint.x, triggerRect.left, triggerRect.right),
          y: clampNumber(clickPoint.y, triggerRect.top, triggerRect.bottom)
        }
      : fallbackPoint,
    rect: {
      left: triggerRect.left,
      right: triggerRect.right,
      top: triggerRect.top,
      bottom: triggerRect.bottom
    }
  };
}

function computePanelStyle(anchor: PanelAnchor, panelSize: PanelSize): CSSProperties {
  const viewportWidth = typeof window === "undefined" ? PANEL_WIDTH + VIEWPORT_PADDING * 2 : window.innerWidth;
  const viewportHeight = typeof window === "undefined" ? DEFAULT_PANEL_HEIGHT + VIEWPORT_PADDING * 2 : window.innerHeight;
  const availableWidth = Math.max(viewportWidth - VIEWPORT_PADDING * 2, 0);
  const availableHeight = Math.max(viewportHeight - VIEWPORT_PADDING * 2, 0);
  const panelWidth = availableWidth > 0 ? Math.min(Math.max(panelSize.width || PANEL_WIDTH, 0), availableWidth) : 0;
  const panelHeight = availableHeight > 0 ? Math.min(Math.max(panelSize.height || DEFAULT_PANEL_HEIGHT, 0), availableHeight) : 0;
  const maxLeft = Math.max(VIEWPORT_PADDING, viewportWidth - panelWidth - VIEWPORT_PADDING);
  const maxTop = Math.max(VIEWPORT_PADDING, viewportHeight - panelHeight - VIEWPORT_PADDING);
  const left = pickClosestClampedCoordinate([anchor.point.x + PANEL_OFFSET_X, anchor.point.x - PANEL_OFFSET_X - panelWidth], VIEWPORT_PADDING, maxLeft);
  const preferredTop = Math.max(anchor.point.y, anchor.rect.bottom) + PANEL_OFFSET_Y;
  const top = clampNumber(preferredTop, VIEWPORT_PADDING, maxTop);

  return {
    position: "fixed",
    left: `${left}px`,
    top: `${top}px`,
    width: `${panelWidth}px`,
    maxHeight: `${availableHeight}px`
  };
}

function pickClosestClampedCoordinate(preferredValues: readonly number[], min: number, max: number): number {
  const [firstPreferred = min, ...restPreferred] = preferredValues;
  let bestValue = clampNumber(firstPreferred, min, max);
  let bestDistance = Math.abs(bestValue - firstPreferred);

  for (const preferredValue of restPreferred) {
    const candidateValue = clampNumber(preferredValue, min, max);
    const candidateDistance = Math.abs(candidateValue - preferredValue);
    if (candidateDistance < bestDistance) {
      bestValue = candidateValue;
      bestDistance = candidateDistance;
    }
  }

  return bestValue;
}

function clampNumber(value: number, min: number, max: number): number {
  if (max <= min) {
    return min;
  }

  return Math.min(Math.max(value, min), max);
}

function samePanelStyle(currentStyle: CSSProperties | undefined, nextStyle: CSSProperties): boolean {
  return (
    currentStyle?.position === nextStyle.position &&
    currentStyle?.left === nextStyle.left &&
    currentStyle?.top === nextStyle.top &&
    currentStyle?.width === nextStyle.width &&
    currentStyle?.maxHeight === nextStyle.maxHeight
  );
}
