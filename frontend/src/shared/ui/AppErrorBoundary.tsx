import { Component, type ErrorInfo, type ReactNode } from "react";
import { resetRecoverableStartupStorage } from "../storage/startupStorageSanitizer";

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
          <p>前端运行时出现错误。可以先清理旧的战斗恢复数据后重新进入；账号登录状态不会被清理。</p>
          <pre>{this.state.error.message}</pre>
          <button type="button" onClick={() => window.location.reload()}>
            重新进入
          </button>
          <button
            type="button"
            onClick={() => {
              resetRecoverableStartupStorage();
              window.location.reload();
            }}
          >
            清理恢复数据后重进
          </button>
        </section>
      </main>
    );
  }
}
