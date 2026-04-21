import { Component, type ErrorInfo, type ReactNode } from "react";

interface AppErrorBoundaryProps {
  children: ReactNode;
}

interface AppErrorBoundaryState {
  error: Error | null;
}

export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { error: null };

  static getDerivedStateFromError(error: Error): AppErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    console.error("[APP_ERROR]", error, info.componentStack);
  }

  render(): ReactNode {
    if (!this.state.error) {
      return this.props.children;
    }

    return (
      <main className="app-fallback">
        <section className="app-fallback__panel">
          <small>Runtime interrupted</small>
          <h1>界面加载失败</h1>
          <p>前端运行时出现错误。请刷新页面；如果仍失败，把控制台里的 `[APP_ERROR]` 发给我。</p>
          <pre>{this.state.error.message}</pre>
          <button type="button" onClick={() => window.location.reload()}>
            重新进入
          </button>
        </section>
      </main>
    );
  }
}
