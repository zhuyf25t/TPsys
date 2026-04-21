import type { ReactNode } from "react";
import { GameCornerButton } from "./GameCornerButton";

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
      <div className="text-shell__back">
        <GameCornerButton label={backLabel} iconKey="back" to={backTo} tooltipPlacement="bottom" />
      </div>
      <main className="text-shell__content">
        <section className="text-shell__frame">
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
