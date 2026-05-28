import { useMemo, useState, type FormEvent } from "react";
import { getAuthSkinById, getAuthSkinOptions, loginUser, registerUser } from "../../../../apis/identity/authGateway";

type AuthMode = "login" | "register";

interface AuthOverlayProps {
  initialMode?: AuthMode;
  onClose: () => void;
  onSuccess: () => void;
}

/** 中文名称：认证弹窗（AuthOverlay）。游戏职责：处理玩家登录、注册和初始皮肤选择。 */
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
            <h3>{mode === "login" ? "登录" : "注册"}</h3>
            <p>{mode === "login" ? "登录后继续同步你的战绩、排行和邮件。" : "创建新档案，开始记录你的对局。"}</p>

            <div className="auth-overlay__skin-card">
              <div className="auth-overlay__skin-card-copy">
                <span>当前皮肤</span>
                <strong>{selectedSkin.label}</strong>
                <small>{mode === "login" ? "登录后延续上次状态。" : "创建后即可开局。"}</small>
              </div>
              <img src={selectedSkin.imageSrc} alt={selectedSkin.label} />
            </div>

            <div className="auth-overlay__promo-tags">
              {["战绩", "排行", "外观"].map((tag) => (
                <span key={tag}>{tag}</span>
              ))}
            </div>
          </aside>

          <div className="auth-overlay__surface">
            <header className="auth-overlay__header">
              <div>
                <small>PLAYER ACCESS</small>
                <h3>{mode === "login" ? "登录" : "注册"}</h3>
                <p>在线档案会同步你的战绩和邮件。</p>
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
                  <span>皮肤</span>
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
                    <small>保留你的档案状态。</small>
                  </div>
                </div>
              )}

              {error ? <p className="auth-overlay__error">{error}</p> : null}

              <div className="auth-overlay__actions">
                <button type="submit" className="auth-overlay__action auth-overlay__action--primary" disabled={pending}>
                  {pending ? "处理中..." : mode === "login" ? "进入" : "创建"}
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
