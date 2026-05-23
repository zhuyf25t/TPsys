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
      <main className="min-h-screen bg-slate-950 px-6 py-12 text-slate-100">
        <section className="mx-auto flex max-w-xl flex-col gap-4 rounded border border-red-400/30 bg-slate-900/95 p-6 shadow-2xl shadow-red-950/30">
          <small className="text-xs font-bold uppercase tracking-[0.24em] text-red-300">Runtime interrupted</small>
          <h1 className="text-2xl font-bold">界面加载失败</h1>
          <p className="text-sm leading-6 text-slate-300">
            前端运行时出现错误。可以先清理旧的战斗恢复数据后重新进入；账号登录状态不会被清理。
          </p>
          <pre className="max-h-40 overflow-auto rounded bg-black/40 p-3 text-xs text-red-100">{this.state.error.message}</pre>
          <button
            type="button"
            className="rounded border border-cyan-300/40 bg-cyan-300/10 px-4 py-2 text-sm font-bold text-cyan-100 transition hover:bg-cyan-300/20"
            onClick={() => window.location.reload()}
          >
            重新进入
          </button>
          <button
            type="button"
            className="rounded border border-amber-300/40 bg-amber-300/10 px-4 py-2 text-sm font-bold text-amber-100 transition hover:bg-amber-300/20"
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
