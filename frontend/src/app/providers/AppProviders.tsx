import type { ReactNode } from "react";
import { BrowserRouter } from "react-router-dom";
import { AppErrorBoundary } from "../../shared/ui/AppErrorBoundary";

export interface AppProvidersProps {
  children: ReactNode;
}

export function AppProviders({ children }: AppProvidersProps) {
  return (
    <AppErrorBoundary>
      <BrowserRouter>{children}</BrowserRouter>
    </AppErrorBoundary>
  );
}
