import { normalizeApiBase } from "../../../shared/api/apiUrl";
import { normalizePlayableIdentityHandle } from "../objects/identityHandlePolicy";

export interface AuthSkinOption {
  id: string;
  label: string;
  textureKey: string;
  imageSrc: string;
  tint: number;
}

export interface LocalAuthUser {
  handle: string;
  password: string;
  skinId: string;
  createdAt: number;
  sessionToken?: string;
}

export interface AuthResult {
  ok: boolean;
  error?: string;
  user?: LocalAuthUser;
  sessionToken?: string;
}

const USERS_KEY = "slay-demo.auth.users.v1";
const SESSION_KEY = "slay-demo.auth.session.v1";
const SESSION_TOKEN_KEY = "slay-demo.auth.session-token.v1";
const DEFAULT_HANDLE = "Visitor";
const DEFAULT_SKIN_ID = "blue";
const BUILTIN_ADMIN_HANDLE = "admin";
const BUILTIN_ADMIN_PASSWORD = "admin123456";
const CONFIGURED_AUTH_API_BASE = (import.meta.env.VITE_AUTH_API_BASE ?? "").trim();
const AUTH_API_BASE = normalizeApiBase(CONFIGURED_AUTH_API_BASE, "/api");
const ALLOW_LOCAL_AUTH_FALLBACK = parseBooleanEnv(import.meta.env.VITE_ALLOW_LOCAL_AUTH_FALLBACK);
const REMOTE_AUTH_UNAVAILABLE_ERROR = "服务器暂时不可用，请确认大厅服务已启动。";
const BACKEND_HEALTH_TTL_MS = 10_000;
const BACKEND_HEALTH_TIMEOUT_MS = 5_000;

interface BackendHealthState {
  healthy: boolean;
  checkedAt: number;
}

interface RemoteAuthPayload {
  handle: string;
  skinId: string;
  session: string;
}

let backendHealthState: BackendHealthState | null = null;
let backendHealthProbe: Promise<boolean> | null = null;
let cachedCurrentAuthUser: LocalAuthUser | null = null;
let authRefreshInFlight: Promise<LocalAuthUser | null> | null = null;
let authSessionBootstrapPromise: Promise<LocalAuthUser | null> | null = null;
let authSessionBootstrapDone = false;
const authStateListeners = new Set<() => void>();

const SKIN_OPTIONS: readonly AuthSkinOption[] = [
  {
    id: "blue",
    label: "蓝盾先锋",
    textureKey: "hero-player",
    imageSrc: "/assets/kenney-top-down-shooter/PNG/Man%20Blue/manBlue_hold.png",
    tint: 0x7ae2ff
  },
  {
    id: "survivor",
    label: "荒野幸存者",
    textureKey: "hero-survivor",
    imageSrc: "/assets/kenney-top-down-shooter/PNG/Survivor%201/survivor1_hold.png",
    tint: 0x7dd87d
  },
  {
    id: "soldier",
    label: "突击士兵",
    textureKey: "hero-soldier",
    imageSrc: "/assets/kenney-top-down-shooter/PNG/Soldier%201/soldier1_hold.png",
    tint: 0xffd36e
  },
  {
    id: "old",
    label: "老兵",
    textureKey: "hero-old",
    imageSrc: "/assets/kenney-top-down-shooter/PNG/Man%20Old/manOld_hold.png",
    tint: 0xc8b6ff
  }
] as const;

export function getAuthSkinOptions(): AuthSkinOption[] {
  return SKIN_OPTIONS.map((skin) => ({ ...skin }));
}

export function getAuthSkinById(skinId: string | null | undefined): AuthSkinOption {
  return SKIN_OPTIONS.find((skin) => skin.id === skinId) ?? SKIN_OPTIONS[0];
}

export function getCurrentAuthUser(): LocalAuthUser | null {
  const sessionToken = getCurrentAuthSessionToken();

  if (!ALLOW_LOCAL_AUTH_FALLBACK && !isRemoteSessionToken(sessionToken)) {
    cachedCurrentAuthUser = null;
    return null;
  }

  if (cachedCurrentAuthUser && sessionToken && cachedCurrentAuthUser.sessionToken === sessionToken) {
    return cachedCurrentAuthUser;
  }

  const localCurrent = readLocalCurrentAuthUser();
  if (localCurrent?.sessionToken === sessionToken) {
    cachedCurrentAuthUser = localCurrent;
    return localCurrent;
  }

  if (!sessionToken && !ALLOW_LOCAL_AUTH_FALLBACK) {
    cachedCurrentAuthUser = null;
    return null;
  }

  if (!ALLOW_LOCAL_AUTH_FALLBACK) {
    cachedCurrentAuthUser = null;
    return null;
  }

  cachedCurrentAuthUser = localCurrent;
  return localCurrent;
}

export function getCurrentAuthHandle(): string {
  return getCurrentAuthUser()?.handle ?? DEFAULT_HANDLE;
}

export function getCurrentAuthSkin(): AuthSkinOption {
  return getAuthSkinById(getCurrentAuthUser()?.skinId ?? DEFAULT_SKIN_ID);
}

export function isBuiltinAdminHandle(handle: string | null | undefined): boolean {
  return normalizeHandle(handle) === BUILTIN_ADMIN_HANDLE;
}

export function isBuiltinAdminCredentials(handle: string, password: string): boolean {
  return isBuiltinAdminHandle(handle) && password.trim() === BUILTIN_ADMIN_PASSWORD;
}

export function getCurrentAuthSessionToken(): string | null {
  if (typeof window === "undefined") {
    return null;
  }

  return window.localStorage.getItem(SESSION_TOKEN_KEY);
}

export async function registerUser(input: {
  handle: string;
  password: string;
  skinId: string;
}): Promise<AuthResult> {
  if (isBuiltinAdminHandle(input.handle)) {
    return {
      ok: false,
      error: "这个名称已保留。"
    };
  }

  const remote = await tryRemoteRegister(input);
  if (remote) {
    return remote;
  }

  if (!ALLOW_LOCAL_AUTH_FALLBACK) {
    return {
      ok: false,
      error: REMOTE_AUTH_UNAVAILABLE_ERROR
    };
  }

  return registerLocalUser(input);
}

export async function loginUser(input: {
  handle: string;
  password: string;
}): Promise<AuthResult> {
  if (ALLOW_LOCAL_AUTH_FALLBACK && isBuiltinAdminCredentials(input.handle, input.password)) {
    return loginBuiltinAdminUser();
  }

  const remote = await tryRemoteLogin(input);
  if (remote) {
    return remote;
  }

  if (!ALLOW_LOCAL_AUTH_FALLBACK) {
    return {
      ok: false,
      error: REMOTE_AUTH_UNAVAILABLE_ERROR
    };
  }

  return loginLocalUser(input);
}

export function bootstrapAuthSession(): Promise<LocalAuthUser | null> {
  if (typeof window === "undefined") {
    return Promise.resolve(null);
  }

  if (authSessionBootstrapDone) {
    return Promise.resolve(getCurrentAuthUser());
  }

  if (!authSessionBootstrapPromise) {
    authSessionBootstrapPromise = refreshCurrentAuthUser().finally(() => {
      authSessionBootstrapDone = true;
      authSessionBootstrapPromise = null;
    });
  }

  return authSessionBootstrapPromise;
}

export function registerLocalUser(input: {
  handle: string;
  password: string;
  skinId: string;
}): AuthResult {
  const handle = normalizePlayableIdentityHandle(input.handle);
  const password = input.password.trim();
  const skin = getAuthSkinById(input.skinId);

  if (!handle || handle.length < 3 || handle.length > 16) {
    return { ok: false, error: "玩家名称需要 3 到 16 个字符。" };
  }

  if (!/^[a-zA-Z0-9_-]+$/.test(handle)) {
    return { ok: false, error: "名称只支持字母、数字、下划线和连字符。" };
  }

  if (password.length < 4) {
    return { ok: false, error: "密码至少需要 4 个字符。" };
  }

  const users = readUsers();
  if (users.some((user) => user.handle.toLowerCase() === handle.toLowerCase())) {
    return { ok: false, error: "这个名称已经被使用了。" };
  }

  const user: LocalAuthUser = {
    handle,
    password,
    skinId: skin.id,
    createdAt: Date.now(),
    sessionToken: makeSessionToken(handle)
  };

  writeUsers([user, ...users]);
  writeSessionHandle(user.handle);
  writeSessionToken(user.sessionToken);
  setCachedCurrentAuthUser(user);
  return { ok: true, user, sessionToken: user.sessionToken };
}

export function loginLocalUser(input: { handle: string; password: string }): AuthResult {
  const handle = normalizePlayableIdentityHandle(input.handle);
  const password = input.password.trim();

  if (!handle) {
    return { ok: false, error: "名称或密码不正确。" };
  }

  const user = readUsers().find((entry) => entry.handle.toLowerCase() === handle.toLowerCase());

  if (!user || user.password !== password) {
    return { ok: false, error: "名称或密码不正确。" };
  }

  const nextUser = {
    ...user,
    sessionToken: user.sessionToken ?? makeSessionToken(user.handle)
  };

  const nextUsers = readUsers().map((entry) =>
    entry.handle.toLowerCase() === nextUser.handle.toLowerCase() ? nextUser : entry
  );

  writeUsers(nextUsers);
  writeSessionHandle(nextUser.handle);
  writeSessionToken(nextUser.sessionToken ?? null);
  setCachedCurrentAuthUser(nextUser);
  return { ok: true, user: nextUser, sessionToken: nextUser.sessionToken };
}

export function logoutLocalUser(): void {
  if (typeof window === "undefined") {
    return;
  }

  clearLocalSession();
  setCachedCurrentAuthUser(null);
}

export function updateCurrentAuthSkin(skinId: string): AuthResult {
  const current = getCurrentAuthUser();
  if (!current) {
    return { ok: false, error: "当前没有已登录玩家。" };
  }

  const nextSkin = getAuthSkinById(skinId);
  const users = readUsers().map((user) =>
    user.handle.toLowerCase() === current.handle.toLowerCase()
      ? { ...user, skinId: nextSkin.id }
      : user
  );

  writeUsers(users);
  setCachedCurrentAuthUser(users.find((user) => user.handle.toLowerCase() === current.handle.toLowerCase()) ?? null);
  return {
    ok: true,
    user: users.find((user) => user.handle.toLowerCase() === current.handle.toLowerCase())
  };
}

export function subscribeAuthState(listener: () => void): () => void {
  authStateListeners.add(listener);

  return () => {
    authStateListeners.delete(listener);
  };
}

export async function refreshCurrentAuthUser(): Promise<LocalAuthUser | null> {
  if (typeof window === "undefined") {
    return null;
  }

  if (authRefreshInFlight) {
    return authRefreshInFlight;
  }

  authRefreshInFlight = (async () => {
    const sessionToken = getCurrentAuthSessionToken();
    const localCurrent = readLocalCurrentAuthUser();

    if (!ALLOW_LOCAL_AUTH_FALLBACK && !isRemoteSessionToken(sessionToken)) {
      clearLocalSession();
      setCachedCurrentAuthUser(null);
      return null;
    }

    if (!sessionToken) {
      setCachedCurrentAuthUser(ALLOW_LOCAL_AUTH_FALLBACK ? localCurrent : null);
      return ALLOW_LOCAL_AUTH_FALLBACK ? localCurrent : null;
    }

    if (!(await canUseBackend())) {
      if (!ALLOW_LOCAL_AUTH_FALLBACK) {
        clearLocalSession();
        setCachedCurrentAuthUser(null);
        return null;
      }

      setCachedCurrentAuthUser(localCurrent);
      return localCurrent;
    }

    const remoteCurrent = await tryRemoteCurrentUser(sessionToken);
    if (remoteCurrent) {
      setCachedCurrentAuthUser(remoteCurrent);
      return remoteCurrent;
    }

    clearLocalSession();
    setCachedCurrentAuthUser(null);
    return null;
  })().finally(() => {
    authRefreshInFlight = null;
  });

  return authRefreshInFlight;
}

async function tryRemoteRegister(input: {
  handle: string;
  password: string;
  skinId: string;
}): Promise<AuthResult | null> {
  try {
    const response = await fetch(`${AUTH_API_BASE}/identity/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input)
    });

    if (!response.ok) {
      return {
        ok: false,
        error: mapRemoteAuthError(await readRemoteErrorCode(response, "register"))
      };
    }

    const payload = normalizeRemoteAuthPayload(await response.json().catch(() => null));
    if (!payload) {
      recordBackendHealth(false);
      return null;
    }

    const user: LocalAuthUser = {
      handle: payload.handle,
      password: input.password.trim(),
      skinId: payload.skinId,
      createdAt: Date.now(),
      sessionToken: payload.session
    };

    return persistRemoteAuthUser(user);
  } catch {
    recordBackendHealth(false);
    return null;
  }
}

async function tryRemoteLogin(input: {
  handle: string;
  password: string;
}): Promise<AuthResult | null> {
  try {
    const response = await fetch(`${AUTH_API_BASE}/identity/session`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(input)
    });

    if (!response.ok) {
      return {
        ok: false,
        error: mapRemoteAuthError(await readRemoteErrorCode(response, "login"))
      };
    }

    const payload = normalizeRemoteAuthPayload(await response.json().catch(() => null));
    if (!payload) {
      recordBackendHealth(false);
      return null;
    }

    const existing = readUsers().find((user) => user.handle.toLowerCase() === payload.handle.toLowerCase());

    const user: LocalAuthUser = {
      handle: payload.handle,
      password: input.password.trim(),
      skinId: payload.skinId,
      createdAt: existing?.createdAt ?? Date.now(),
      sessionToken: payload.session
    };

    return persistRemoteAuthUser(user);
  } catch {
    recordBackendHealth(false);
    return null;
  }
}

async function tryRemoteCurrentUser(sessionToken: string): Promise<LocalAuthUser | null> {
  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), BACKEND_HEALTH_TIMEOUT_MS);

    try {
      const response = await fetch(`${AUTH_API_BASE}/identity/me`, {
        method: "GET",
        cache: "no-store",
        signal: controller.signal,
        headers: {
          Authorization: `Bearer ${sessionToken}`
        }
      });

      if (!response.ok) {
        return null;
      }

      const payload = normalizeRemoteAuthPayload(await response.json().catch(() => null));
      if (!payload) {
        return null;
      }

      const existing = readUsers().find((user) => user.handle.toLowerCase() === payload.handle.toLowerCase());
      const nextUser: LocalAuthUser =
        existing ?? {
          handle: payload.handle,
          password: "",
          skinId: payload.skinId,
          createdAt: Date.now(),
          sessionToken: payload.session
        };

      const hydratedUser = {
        ...nextUser,
        skinId: payload.skinId,
        sessionToken: payload.session
      };

      hydrateLocalSession(hydratedUser, hydratedUser.sessionToken);
      recordBackendHealth(true);
      setCachedCurrentAuthUser(hydratedUser);
      return hydratedUser;
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    recordBackendHealth(false);
    return null;
  }
}

function parseBooleanEnv(value: string | undefined | null): boolean {
  const normalized = (value ?? "").trim().toLowerCase();
  return normalized === "true" || normalized === "1" || normalized === "yes" || normalized === "on";
}

function normalizeRemoteAuthPayload(value: unknown): RemoteAuthPayload | null {
  if (!value || typeof value !== "object") {
    return null;
  }

  const payload = value as Partial<Record<keyof RemoteAuthPayload, unknown>>;
  const handle = normalizePlayableIdentityHandle(readRequiredString(payload.handle));
  const skinId = readRequiredString(payload.skinId);
  const session = readRequiredString(payload.session);
  return handle && skinId && session ? { handle, skinId, session } : null;
}

function readRequiredString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function isRemoteSessionToken(sessionToken: string | null | undefined): sessionToken is string {
  return Boolean(sessionToken?.trim()) && !sessionToken!.trim().startsWith("local-");
}

async function canUseBackend(): Promise<boolean> {
  if (!AUTH_API_BASE) {
    return false;
  }

  if (backendHealthState && Date.now() - backendHealthState.checkedAt < BACKEND_HEALTH_TTL_MS) {
    return backendHealthState.healthy;
  }

  if (backendHealthProbe) {
    return backendHealthProbe;
  }

  backendHealthProbe = probeBackendHealth().finally(() => {
    backendHealthProbe = null;
  });

  return backendHealthProbe;
}

async function probeBackendHealth(): Promise<boolean> {
  try {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), BACKEND_HEALTH_TIMEOUT_MS);

    try {
      const response = await fetch(`${AUTH_API_BASE}/health`, {
        method: "GET",
        cache: "no-store",
        signal: controller.signal
      });

      if (!response.ok) {
        recordBackendHealth(false);
        return false;
      }

      const payload = (await response.json().catch(() => null)) as { status?: string } | null;
      const healthy = payload?.status === "ok" || response.ok;
      recordBackendHealth(healthy);
      return healthy;
    } finally {
      window.clearTimeout(timeout);
    }
  } catch {
    recordBackendHealth(false);
    return false;
  }
}

function recordBackendHealth(healthy: boolean): void {
  backendHealthState = {
    healthy,
    checkedAt: Date.now()
  };
}

function setCachedCurrentAuthUser(user: LocalAuthUser | null): void {
  cachedCurrentAuthUser = isPlayableLocalAuthUser(user) ? user : null;
  emitAuthStateChange();
}

async function persistRemoteAuthUser(user: LocalAuthUser): Promise<AuthResult> {
  const playableHandle = normalizePlayableIdentityHandle(user.handle);
  if (!playableHandle) {
    clearLocalSession();
    setCachedCurrentAuthUser(null);
    return { ok: false, error: mapRemoteAuthError("invalid_handle") };
  }

  const playableUser = { ...user, handle: playableHandle };
  hydrateLocalSession(playableUser, playableUser.sessionToken);
  recordBackendHealth(true);
  setCachedCurrentAuthUser(playableUser);
  void refreshCurrentAuthUser();
  return { ok: true, user: playableUser, sessionToken: playableUser.sessionToken };
}

function emitAuthStateChange(): void {
  authStateListeners.forEach((listener) => {
    try {
      listener();
    } catch {
      // ignore listener failures
    }
  });
}

async function readRemoteErrorCode(
  response: Response,
  fallbackCode: string
): Promise<string> {
  try {
    const payload = (await response.json()) as { code?: string; error?: string } | null;
    return payload?.code ?? fallbackCode;
  } catch {
    return fallbackCode;
  }
}

function mapRemoteAuthError(code: string): string {
  switch (code) {
    case "handle_taken":
      return "这个名称已经被使用了。";
    case "invalid_handle":
      return "玩家名称需要 3 到 16 个字符。";
    case "invalid_password":
      return "密码至少需要 4 个字符。";
    case "invalid_skin":
      return "请选择一个可用的皮肤。";
    case "invalid_credentials":
      return "名称或密码不正确。";
    default:
      return "操作失败，请稍后重试。";
  }
}

function hydrateLocalSession(user: LocalAuthUser, sessionToken?: string): void {
  const playableHandle = normalizePlayableIdentityHandle(user.handle);
  if (!playableHandle) {
    clearLocalSession();
    return;
  }

  const users = readUsers();
  const withoutCurrent = users.filter((entry) => entry.handle.toLowerCase() !== playableHandle.toLowerCase());
  const hydratedUser = sessionToken
    ? { ...user, handle: playableHandle, sessionToken }
    : { ...user, handle: playableHandle };
  writeUsers([hydratedUser, ...withoutCurrent]);
  writeSessionHandle(hydratedUser.handle);
  writeSessionToken(sessionToken ?? hydratedUser.sessionToken ?? null);
}

function readLocalCurrentAuthUser(): LocalAuthUser | null {
  const users = readUsers();
  const sessionHandle = readSessionHandle();
  if (!sessionHandle) {
    return null;
  }

  const playableSessionHandle = normalizePlayableIdentityHandle(sessionHandle);
  if (!playableSessionHandle) {
    clearLocalSession();
    return null;
  }

  const localUser = users.find((user) => user.handle.toLowerCase() === playableSessionHandle.toLowerCase()) ?? null;
  if (localUser) {
    return localUser;
  }

  if (isBuiltinAdminHandle(playableSessionHandle)) {
    return buildBuiltinAdminUser(getCurrentAuthSessionToken() ?? undefined);
  }

  return null;
}

function readUsers(): LocalAuthUser[] {
  if (typeof window === "undefined") {
    return [];
  }

  const raw = window.localStorage.getItem(USERS_KEY);
  if (!raw) {
    return [];
  }

  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }

    const playableUsers = parsed.filter(isPlayableLocalAuthUser);
    if (playableUsers.length !== parsed.length) {
      writeUsers(playableUsers);
    }

    return playableUsers;
  } catch {
    return [];
  }
}

function writeUsers(users: LocalAuthUser[]): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(USERS_KEY, JSON.stringify(users.filter(isPlayableLocalAuthUser)));
}

function isPlayableLocalAuthUser(user: unknown): user is LocalAuthUser {
  if (!user || typeof user !== "object") {
    return false;
  }

  const handle = (user as { handle?: unknown }).handle;
  return typeof handle === "string" && normalizePlayableIdentityHandle(handle) !== null;
}

function readSessionHandle(): string | null {
  if (typeof window === "undefined") {
    return null;
  }

  return window.localStorage.getItem(SESSION_KEY);
}

function writeSessionHandle(handle: string): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.setItem(SESSION_KEY, handle);
}

function writeSessionToken(sessionToken: string | null | undefined): void {
  if (typeof window === "undefined") {
    return;
  }

  if (!sessionToken) {
    window.localStorage.removeItem(SESSION_TOKEN_KEY);
    return;
  }

  window.localStorage.setItem(SESSION_TOKEN_KEY, sessionToken);
}

function clearLocalSession(): void {
  if (typeof window === "undefined") {
    return;
  }

  window.localStorage.removeItem(SESSION_KEY);
  window.localStorage.removeItem(SESSION_TOKEN_KEY);
}

function makeSessionToken(handle: string): string {
  const suffix = Math.random().toString(36).slice(2, 10);
  return `local-${handle.toLowerCase()}-${suffix}`;
}

function loginBuiltinAdminUser(): AuthResult {
  const user = persistBuiltinAdminUser();
  return {
    ok: true,
    user,
    sessionToken: user.sessionToken
  };
}

function persistBuiltinAdminUser(sessionToken?: string): LocalAuthUser {
  const user = buildBuiltinAdminUser(sessionToken);
  const users = readUsers().filter((entry) => entry.handle.toLowerCase() !== BUILTIN_ADMIN_HANDLE);
  writeUsers([user, ...users]);
  writeSessionHandle(user.handle);
  writeSessionToken(user.sessionToken ?? null);
  setCachedCurrentAuthUser(user);
  return user;
}

function buildBuiltinAdminUser(sessionToken?: string): LocalAuthUser {
  return {
    handle: BUILTIN_ADMIN_HANDLE,
    password: BUILTIN_ADMIN_PASSWORD,
    skinId: DEFAULT_SKIN_ID,
    createdAt: Date.now(),
    sessionToken: sessionToken ?? makeSessionToken(BUILTIN_ADMIN_HANDLE)
  };
}

function normalizeHandle(handle: string | null | undefined): string {
  return (handle ?? "").trim().toLowerCase();
}
