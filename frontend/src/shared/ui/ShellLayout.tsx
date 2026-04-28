import type { ReactNode } from "react";
import { Link } from "react-router-dom";

interface ShellLayoutProps {
  title: string;
  subtitle: string;
  children: ReactNode;
  variant?: "default" | "lobby";
  hidePageHeader?: boolean;
  backTo?: string;
  backLabel?: string;
}

export function ShellLayout({
  title,
  subtitle,
  children,
  variant = "default",
  hidePageHeader = false,
  backTo = "/",
  backLabel = "返回大厅"
}: ShellLayoutProps) {
  return (
    <div className={`text-shell${variant === "lobby" ? " text-shell--lobby" : ""}`}>
      <main className="text-shell__content">
        <section className="text-shell__frame">
          <div className="text-shell__back">
            <Link className="text-shell__back-link" to={backTo} aria-label={backLabel}>
              <span className="text-shell__back-icon" aria-hidden="true">
                &lt;
              </span>
              <span>{backLabel}</span>
            </Link>
          </div>
          {!hidePageHeader ? (
            <header className="text-shell__header">
              <div>
                <p className="text-shell__eyebrow">View All</p>
                <h2>{title}</h2>
                <p>{subtitle}</p>
              </div>
            </header>
          ) : null}
          {children}
        </section>
      </main>
    </div>
  );
}
