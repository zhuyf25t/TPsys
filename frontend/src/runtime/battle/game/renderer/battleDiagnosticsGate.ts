const BATTLE_DIAGNOSTICS_STORAGE_KEY = "slay-demo:battle-diagnostics";

export type BattleDiagnosticsRoot = Record<string, unknown>;

type BattleDiagnosticsWindow = Window & {
  __slayDemoBattleDiagnostics?: BattleDiagnosticsRoot;
};

let cachedDiagnosticsEnabled: boolean | null = null;

/** 中文名：判断是否战斗diagnosticsenabled（isBattleDiagnosticsEnabled）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isBattleDiagnosticsEnabled(): boolean {
  if (cachedDiagnosticsEnabled !== null) {
    return cachedDiagnosticsEnabled;
  }

  cachedDiagnosticsEnabled = readBattleDiagnosticsEnabled();
  return cachedDiagnosticsEnabled;
}

/** 中文名：获取战斗diagnosticsroot（getBattleDiagnosticsRoot）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getBattleDiagnosticsRoot<TRoot extends BattleDiagnosticsRoot = BattleDiagnosticsRoot>(): TRoot | null {
  if (!isBattleDiagnosticsEnabled() || typeof window === "undefined") {
    return null;
  }

  const diagnosticsWindow = window as BattleDiagnosticsWindow;
  diagnosticsWindow.__slayDemoBattleDiagnostics = diagnosticsWindow.__slayDemoBattleDiagnostics ?? {};
  return diagnosticsWindow.__slayDemoBattleDiagnostics as TRoot;
}

function readBattleDiagnosticsEnabled(): boolean {
  if (typeof window === "undefined") {
    return false;
  }

  const params = new URLSearchParams(window.location.search);
  if (isEnabledValue(params.get("diagnostics")) || isEnabledValue(params.get("battleDiagnostics"))) {
    return true;
  }
  if (isTargetDiagnosticsEnabled(params.get("target"))) {
    return true;
  }

  try {
    return isEnabledValue(window.localStorage.getItem(BATTLE_DIAGNOSTICS_STORAGE_KEY));
  } catch {
    return false;
  }
}

function isEnabledValue(value: string | null): boolean {
  if (value === null) {
    return false;
  }

  return value === "1" || value.toLowerCase() === "true";
}

function isTargetDiagnosticsEnabled(target: string | null): boolean {
  if (!target) {
    return false;
  }

  try {
    const targetUrl = new URL(target, window.location.origin);
    const targetParams = targetUrl.searchParams;
    return isEnabledValue(targetParams.get("diagnostics")) || isEnabledValue(targetParams.get("battleDiagnostics"));
  } catch {
    return false;
  }
}
