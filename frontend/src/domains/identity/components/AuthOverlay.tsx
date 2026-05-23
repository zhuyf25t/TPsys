import { useMemo, useState, type FormEvent } from "react";
import { cn } from "../../../shared/ui/classNames";
import { getAuthSkinById, getAuthSkinOptions, loginUser, registerUser } from "../api/authGateway";

type AuthMode = "login" | "register";

interface AuthOverlayProps {
  initialMode?: AuthMode;
  onClose: () => void;
  onSuccess: () => void;
}

/** 中文名：认证弹窗（AuthOverlay）。游戏职责：处理玩家登录、注册和初始皮肤选择。 */
export function AuthOverlay({ initialMode = "login", onClose, onSuccess }: AuthOverlayProps) {
  const [mode, setMode] = useState<AuthMode>(initialMode);
  const [handle, setHandle] = useState("");
  const [password, setPassword] = useState("");
  const [skinId, setSkinId] = useState(getAuthSkinOptions()[0]?.id ?? "blue");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const skins = useMemo(() => getAuthSkinOptions(), []);
  const selectedSkin = getAuthSkinById(skinId);

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setPending(true);
    setError(null);

    try {
      const result = mode === "login" ? await loginUser({ handle, password }) : await registerUser({ handle, password, skinId });

      if (!result.ok) {
        setError(result.error ?? "提交失败，请重试。");
        return;
      }

      onSuccess();
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 grid place-items-center bg-black/70 p-4 backdrop-blur-sm" role="presentation" onClick={onClose}>
      <section
        className="w-full max-w-4xl overflow-hidden rounded border border-white/10 bg-slate-950 text-slate-100 shadow-2xl shadow-black/60"
        role="dialog"
        aria-modal="true"
        aria-label={mode === "login" ? "登录大厅" : "创建档案"}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="grid md:grid-cols-[0.9fr_1.1fr]">
          <aside className="hidden flex-col justify-between gap-6 bg-[radial-gradient(circle_at_top,rgba(14,165,233,0.24),transparent_34%),linear-gradient(145deg,rgba(15,23,42,0.96),rgba(2,6,23,0.98))] p-6 md:flex">
            <div>
              <small className="text-xs font-black uppercase tracking-[0.24em] text-cyan-200">PLAYER ACCESS</small>
              <h3 className="mt-3 text-3xl font-black text-white">{mode === "login" ? "登录" : "注册"}</h3>
              <p className="mt-3 text-sm leading-6 text-slate-300">
                {mode === "login" ? "登录后继续同步你的战绩、排行和邮件。" : "创建新档案，开始记录你的对局。"}
              </p>
            </div>

            <div className="flex items-center gap-4 rounded border border-white/10 bg-white/[0.05] p-4">
              <div className="min-w-0 flex-1">
                <span className="text-xs font-bold text-slate-400">当前皮肤</span>
                <strong className="mt-1 block text-white">{selectedSkin.label}</strong>
                <small className="mt-1 block text-xs text-slate-400">{mode === "login" ? "登录后延续上次状态。" : "创建后即可开局。"}</small>
              </div>
              <img className="h-20 w-20 rounded-full border border-white/10 object-cover" src={selectedSkin.imageSrc} alt={selectedSkin.label} />
            </div>

            <div className="flex flex-wrap gap-2">
              {["战绩", "排行", "外观"].map((tag) => (
                <span key={tag} className="rounded border border-cyan-200/20 bg-cyan-300/10 px-3 py-1 text-xs font-bold text-cyan-100">
                  {tag}
                </span>
              ))}
            </div>
          </aside>

          <div className="p-5 sm:p-6">
            <header className="flex items-start justify-between gap-4">
              <div>
                <small className="text-xs font-black uppercase tracking-[0.24em] text-cyan-200">PLAYER ACCESS</small>
                <h3 className="mt-2 text-2xl font-black text-white">{mode === "login" ? "登录" : "注册"}</h3>
                <p className="mt-2 text-sm leading-6 text-slate-300">在线档案会同步你的战绩和邮件。</p>
              </div>
              <button
                type="button"
                className="grid h-9 w-9 flex-none place-items-center rounded border border-white/10 bg-white/5 text-lg text-slate-200 transition hover:border-red-300/50 hover:text-red-100"
                onClick={onClose}
                aria-label="关闭"
              >
                ×
              </button>
            </header>

            <div className="mt-5 grid grid-cols-2 gap-2 rounded border border-white/10 bg-black/20 p-1">
              {(["login", "register"] as const).map((nextMode) => (
                <button
                  key={nextMode}
                  type="button"
                  className={cn(
                    "rounded px-3 py-2 text-sm font-black transition",
                    mode === nextMode ? "bg-cyan-300/20 text-cyan-50" : "text-slate-400 hover:bg-white/5 hover:text-slate-100"
                  )}
                  onClick={() => {
                    setMode(nextMode);
                    setError(null);
                  }}
                >
                  {nextMode === "login" ? "登录" : "注册"}
                </button>
              ))}
            </div>

            <form className="mt-5 flex flex-col gap-4" onSubmit={handleSubmit}>
              <label className="flex flex-col gap-2">
                <span className="text-sm font-bold text-slate-300">玩家代号</span>
                <input
                  className="rounded border border-white/10 bg-slate-900 px-3 py-2 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-cyan-300/60"
                  value={handle}
                  onChange={(event) => setHandle(event.target.value)}
                  maxLength={16}
                  placeholder="例如 PlayerOne"
                  autoFocus
                />
              </label>

              <label className="flex flex-col gap-2">
                <span className="text-sm font-bold text-slate-300">密码</span>
                <input
                  className="rounded border border-white/10 bg-slate-900 px-3 py-2 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-cyan-300/60"
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  maxLength={32}
                  placeholder="至少 4 个字符"
                />
              </label>

              {mode === "register" ? (
                <div className="flex flex-col gap-2">
                  <span className="text-sm font-bold text-slate-300">皮肤</span>
                  <div className="grid grid-cols-3 gap-2">
                    {skins.map((skin) => (
                      <button
                        key={skin.id}
                        type="button"
                        className={cn(
                          "rounded border p-2 text-center transition",
                          skin.id === skinId ? "border-cyan-300/70 bg-cyan-300/15" : "border-white/10 bg-white/[0.03] hover:bg-white/[0.07]"
                        )}
                        onClick={() => setSkinId(skin.id)}
                      >
                        <img className="mx-auto h-12 w-12 rounded-full object-cover" src={skin.imageSrc} alt={skin.label} />
                        <small className="mt-1 block text-xs text-slate-300">{skin.label}</small>
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="rounded border border-white/10 bg-white/[0.04] p-3">
                  <span className="text-xs font-bold text-slate-400">当前档案</span>
                  <strong className="mt-1 block text-white">{selectedSkin.label}</strong>
                  <small className="text-xs text-slate-400">保留你的档案状态。</small>
                </div>
              )}

              {error ? <p className="rounded border border-red-300/30 bg-red-500/10 px-3 py-2 text-sm text-red-100">{error}</p> : null}

              <div className="grid gap-3 sm:grid-cols-2">
                <button
                  type="submit"
                  className="rounded border border-cyan-300/50 bg-cyan-300/15 px-4 py-3 text-sm font-black text-cyan-50 transition hover:bg-cyan-300/25 disabled:cursor-not-allowed disabled:opacity-50"
                  disabled={pending}
                >
                  {pending ? "处理中..." : mode === "login" ? "进入" : "创建"}
                </button>
                <button
                  type="button"
                  className="rounded border border-white/10 bg-white/5 px-4 py-3 text-sm font-black text-slate-200 transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-50"
                  onClick={onClose}
                  disabled={pending}
                >
                  返回
                </button>
              </div>
            </form>
          </div>
        </div>
      </section>
    </div>
  );
}
