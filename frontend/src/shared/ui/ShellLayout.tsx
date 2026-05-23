import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { cn } from "./classNames";

interface ShellLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
  variant?: "default" | "lobby";
  hidePageHeader?: boolean;
  headerAside?: ReactNode;
  backTo?: string;
  backLabel?: string;
}

/** 中文名：页面壳层（ShellLayout）。游戏职责：为资料、论坛、回放等文本页面提供统一布局。 */
export function ShellLayout({
  title,
  subtitle,
  children,
  variant = "default",
  hidePageHeader = false,
  headerAside,
  backTo = "/",
  backLabel = "返回大厅"
}: ShellLayoutProps) {
  return (
    <div
      className={cn(
        "min-h-screen bg-slate-950 px-4 py-6 text-slate-100 sm:px-6 lg:px-8",
        variant === "lobby" && "bg-[radial-gradient(circle_at_top_left,rgba(14,165,233,0.18),transparent_34%),#020617]"
      )}
    >
      <main className="mx-auto max-w-7xl">
        <section className="rounded border border-white/10 bg-slate-900/70 p-4 shadow-2xl shadow-black/30 backdrop-blur sm:p-6">
          <div className="mb-5">
            <Link
              className="inline-flex items-center gap-2 rounded border border-white/10 bg-white/5 px-3 py-2 text-sm font-bold text-slate-200 transition hover:border-cyan-300/40 hover:text-cyan-100"
              to={backTo}
              aria-label={backLabel}
            >
              <span aria-hidden="true">&lt;</span>
              <span>{backLabel}</span>
            </Link>
          </div>
          {!hidePageHeader ? (
            <header className="mb-6 flex flex-col gap-4 border-b border-white/10 pb-5 lg:flex-row lg:items-end lg:justify-between">
              <div>
                <p className="text-xs font-bold uppercase tracking-[0.24em] text-cyan-200">View All</p>
                <h2 className="mt-2 text-3xl font-black tracking-wide text-white">{title}</h2>
                <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-300">{subtitle}</p>
              </div>
              {headerAside ? <div className="flex flex-wrap items-center gap-3">{headerAside}</div> : null}
            </header>
          ) : null}
          {children}
        </section>
      </main>
    </div>
  );
}
