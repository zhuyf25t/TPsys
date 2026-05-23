import { Link } from "react-router-dom";
import { cn } from "./classNames";

export interface QuickPreviewEntry {
  title: string;
  meta: string;
  detail: string;
  onSelect?: () => void;
}

export interface QuickPreviewOverlayProps {
  title: string;
  eyebrow: string;
  detail: string;
  emptyTitle: string;
  emptyDetail: string;
  viewAllPath: string;
  anchor: "left" | "right";
  items: QuickPreviewEntry[];
  onClose: () => void;
}

/** 中文名：快速预览层（QuickPreviewOverlay）。游戏职责：展示大厅角落入口的轻量预览。 */
export function QuickPreviewOverlay({
  title,
  eyebrow,
  detail,
  emptyTitle,
  emptyDetail,
  viewAllPath,
  anchor,
  items,
  onClose
}: QuickPreviewOverlayProps) {
  return (
    <div className="fixed inset-0 z-50 bg-black/35 backdrop-blur-sm" role="presentation" onClick={onClose}>
      <aside
        className={cn(
          "absolute top-6 flex max-h-[calc(100vh-3rem)] w-[min(420px,calc(100vw-2rem))] flex-col gap-4 overflow-hidden rounded border border-white/10 bg-slate-950/95 p-4 text-slate-100 shadow-2xl shadow-black/50",
          anchor === "left" ? "left-4" : "right-4"
        )}
        role="dialog"
        aria-modal="false"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="flex items-start justify-between gap-4 border-b border-white/10 pb-4">
          <div>
            <small className="text-xs font-black uppercase tracking-[0.22em] text-cyan-200">{eyebrow}</small>
            <h3 className="mt-2 text-xl font-black text-white">{title}</h3>
            <p className="mt-2 text-sm leading-6 text-slate-300">{detail}</p>
          </div>
          <button
            type="button"
            className="grid h-9 w-9 flex-none place-items-center rounded border border-white/10 bg-white/5 text-xl text-slate-200 transition hover:border-red-300/50 hover:text-red-100"
            onClick={onClose}
            aria-label="关闭"
          >
            ×
          </button>
        </header>

        <div className="flex min-h-0 flex-col gap-3 overflow-auto pr-1">
          {items.length > 0 ? (
            items.map((item) =>
              item.onSelect ? (
                <button
                  key={`${item.title}-${item.meta}`}
                  type="button"
                  className="rounded border border-white/10 bg-white/[0.04] p-3 text-left font-inherit transition hover:border-cyan-300/40 hover:bg-cyan-300/10"
                  onClick={item.onSelect}
                >
                  <PreviewContent item={item} />
                </button>
              ) : (
                <article key={`${item.title}-${item.meta}`} className="rounded border border-white/10 bg-white/[0.04] p-3">
                  <PreviewContent item={item} />
                </article>
              )
            )
          ) : (
            <article className="rounded border border-dashed border-white/15 bg-white/[0.03] p-4">
              <strong className="block text-sm text-white">{emptyTitle}</strong>
              <span className="mt-2 block text-sm leading-6 text-slate-400">{emptyDetail}</span>
            </article>
          )}
        </div>

        <footer className="flex flex-wrap justify-end gap-3 border-t border-white/10 pt-4">
          <Link className="rounded border border-cyan-300/40 bg-cyan-300/10 px-4 py-2 text-sm font-bold text-cyan-100 transition hover:bg-cyan-300/20" to={viewAllPath}>
            查看全部
          </Link>
          <button
            type="button"
            className="rounded border border-white/10 bg-white/5 px-4 py-2 text-sm font-bold text-slate-200 transition hover:bg-white/10"
            onClick={onClose}
          >
            返回
          </button>
        </footer>
      </aside>
    </div>
  );
}

function PreviewContent({ item }: { item: QuickPreviewEntry }) {
  return (
    <>
      <strong className="block text-sm text-white">{item.title}</strong>
      <small className="mt-1 block text-xs font-bold uppercase tracking-[0.18em] text-amber-200/80">{item.meta}</small>
      <span className="mt-2 block text-sm leading-6 text-slate-300">{item.detail}</span>
    </>
  );
}
