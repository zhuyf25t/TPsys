import { useMemo, useState, type FormEvent } from "react";
import { getAuthSkinById, getAuthSkinOptions, loginUser, registerUser } from "../../features/auth/authGateway";

type AuthMode = "login" | "register";

interface AuthOverlayProps {
  initialMode?: AuthMode;
  onClose: () => void;
  onSuccess: () => void;
}

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
      const result =
        mode === "login"
          ? await loginUser({ handle, password })
          : await registerUser({ handle, password, skinId });

      if (!result.ok) {
        setError(result.error ?? "操作失败，请重试。");
        return;
      }

      onSuccess();
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="auth-overlay" role="presentation" onClick={onClose}>
      <section
        className="auth-overlay__panel"
        role="dialog"
        aria-modal="true"
        aria-label={mode === "login" ? "登录大厅" : "创建档案"}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="auth-overlay__layout">
          <aside className="auth-overlay__promo">
            <small>PLAYER ACCESS</small>
            <h3>{mode === "login" ? "进入大厅" : "创建档案"}</h3>
            <p>
              {mode === "login"
                ? "优先走后端身份；如果后端暂时不可用，会用本地演示兜底继续保存记录。"
                : "优先创建后端档案；离线时才会启用本地演示兜底。"}
            </p>

            <div className="auth-overlay__skin-card">
              <div className="auth-overlay__skin-card-copy">
                <span>当前档案皮肤</span>
                <strong>{selectedSkin.label}</strong>
                <small>{mode === "login" ? "进入后会延续你上次的状态。" : "创建后可以直接进入大厅。"}</small>
              </div>
              <img src={selectedSkin.imageSrc} alt={selectedSkin.label} />
            </div>

            <div className="auth-overlay__promo-tags">
              <span>保存战报</span>
              <span>继续评分</span>
              <span>保留外观</span>
            </div>
          </aside>

          <div className="auth-overlay__surface">
            <header className="auth-overlay__header">
              <div>
                <small>PLAYER ACCESS</small>
                <h3>{mode === "login" ? "登录" : "注册"}</h3>
                <p>后端是优先身份源；本地档案只作为演示兜底，不会掩盖访客状态。</p>
              </div>
              <button type="button" className="auth-overlay__close" onClick={onClose} aria-label="关闭">
                ×
              </button>
            </header>

            <div className="auth-overlay__tabs">
              <button
                type="button"
                className={`auth-overlay__tab${mode === "login" ? " auth-overlay__tab--active" : ""}`}
                onClick={() => {
                  setMode("login");
                  setError(null);
                }}
              >
                登录
              </button>
              <button
                type="button"
                className={`auth-overlay__tab${mode === "register" ? " auth-overlay__tab--active" : ""}`}
                onClick={() => {
                  setMode("register");
                  setError(null);
                }}
              >
                注册
              </button>
            </div>

            <form className="auth-overlay__form" onSubmit={handleSubmit}>
              <label className="auth-overlay__field">
                <span>玩家代号</span>
                <input
                  value={handle}
                  onChange={(event) => setHandle(event.target.value)}
                  maxLength={16}
                  placeholder="例如 PlayerOne"
                  autoFocus
                />
              </label>

              <label className="auth-overlay__field">
                <span>密码</span>
                <input
                  type="password"
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                  maxLength={32}
                  placeholder="至少 4 个字符"
                />
              </label>

              {mode === "register" ? (
                <div className="auth-overlay__field">
                  <span>档案皮肤</span>
                  <div className="auth-overlay__skins">
                    {skins.map((skin) => (
                      <button
                        key={skin.id}
                        type="button"
                        className={`auth-overlay__skin${skin.id === skinId ? " auth-overlay__skin--active" : ""}`}
                        onClick={() => setSkinId(skin.id)}
                      >
                        <img src={skin.imageSrc} alt={skin.label} />
                        <small>{skin.label}</small>
                      </button>
                    ))}
                  </div>
                </div>
              ) : (
                <div className="auth-overlay__field">
                  <span>当前档案</span>
                  <div className="auth-overlay__summary">
                    <strong>{selectedSkin.label}</strong>
                    <small>保留你的局内记录与战后变化。</small>
                  </div>
                </div>
              )}

              {error ? <p className="auth-overlay__error">{error}</p> : null}

              <div className="auth-overlay__actions">
                <button type="submit" className="auth-overlay__action auth-overlay__action--primary" disabled={pending}>
                  {pending ? "处理中..." : mode === "login" ? "进入大厅" : "创建档案"}
                </button>
                <button type="button" className="auth-overlay__action" onClick={onClose} disabled={pending}>
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
