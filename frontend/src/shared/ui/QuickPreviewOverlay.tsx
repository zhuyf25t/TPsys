import { Link } from "react-router-dom";

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

/** 中文名称：快速预览层（QuickPreviewOverlay）。游戏职责：展示大厅角落入口的轻量预览。 */
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
    <div className="quick-overlay" role="presentation" onClick={onClose}>
      <aside
        className={`quick-overlay__panel quick-overlay__panel--${anchor}`}
        role="dialog"
        aria-modal="false"
        aria-label={title}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="quick-overlay__header">
          <div>
            <small>{eyebrow}</small>
            <h3>{title}</h3>
            <p>{detail}</p>
          </div>
          <button type="button" className="quick-overlay__close" onClick={onClose} aria-label="关闭">
            ×
          </button>
        </header>

        <div className="quick-overlay__list">
          {items.length > 0 ? (
            items.map((item) =>
              item.onSelect ? (
                <button key={`${item.title}-${item.meta}`} type="button" className="quick-overlay__item" onClick={item.onSelect}>
                  <PreviewContent item={item} />
                </button>
              ) : (
                <article key={`${item.title}-${item.meta}`} className="quick-overlay__item">
                  <PreviewContent item={item} />
                </article>
              )
            )
          ) : (
            <article className="quick-overlay__item quick-overlay__item--empty">
              <strong>{emptyTitle}</strong>
              <span>{emptyDetail}</span>
            </article>
          )}
        </div>

        <footer className="quick-overlay__footer">
          <Link className="quick-overlay__action quick-overlay__action--primary" to={viewAllPath}>
            查看全部
          </Link>
          <button type="button" className="quick-overlay__action" onClick={onClose}>
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
      <strong>{item.title}</strong>
      <small>{item.meta}</small>
      <span>{item.detail}</span>
    </>
  );
}
