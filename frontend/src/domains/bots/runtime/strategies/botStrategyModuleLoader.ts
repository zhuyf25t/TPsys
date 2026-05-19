import {
  normalizeBotStrategyKey,
  registerBotStrategy,
  type BotCommandStrategy
} from "./botSdk";

export type BotStrategyModuleLoaderErrorCode =
  | "MODULE_NOT_OBJECT"
  | "EXPORT_NOT_FOUND"
  | "STRATEGY_EXPORT_INVALID"
  | "MODULE_URL_INVALID"
  | "MODULE_LOAD_FAILED"
  | "REGISTRATION_FAILED";

export class BotStrategyModuleLoaderError extends Error {
  readonly code: BotStrategyModuleLoaderErrorCode;
  readonly originalError: unknown;

  constructor(code: BotStrategyModuleLoaderErrorCode, message: string, originalError?: unknown) {
    super(message);
    this.name = "BotStrategyModuleLoaderError";
    this.code = code;
    this.originalError = originalError;
  }
}

export interface BotStrategyModuleResolveOptions {
  readonly exportName?: string;
}

export type BotStrategyModuleResolveResult =
  | {
      readonly ok: true;
      readonly strategy: BotCommandStrategy;
      readonly exportName: string;
    }
  | {
      readonly ok: false;
      readonly error: BotStrategyModuleLoaderError;
    };

export type BotStrategyModuleRegistrationResult =
  | {
      readonly ok: true;
      readonly strategy: BotCommandStrategy;
      readonly strategyId: string;
      readonly exportName: string;
    }
  | {
      readonly ok: false;
      readonly error: BotStrategyModuleLoaderError;
    };

export function resolveBotStrategyFromModule(
  moduleNamespace: unknown,
  options: BotStrategyModuleResolveOptions = {}
): BotStrategyModuleResolveResult {
  if (isBotCommandStrategy(moduleNamespace)) {
    return {
      ok: true,
      strategy: moduleNamespace,
      exportName: "module"
    };
  }

  if (!isRecord(moduleNamespace)) {
    return failure("MODULE_NOT_OBJECT", "Bot strategy module must be an object or ESM module namespace.");
  }

  const exportName = options.exportName?.trim();
  if (exportName) {
    return resolveNamedExport(moduleNamespace, exportName);
  }

  const defaultExport = moduleNamespace.default;
  if (isBotCommandStrategy(defaultExport)) {
    return {
      ok: true,
      strategy: defaultExport,
      exportName: "default"
    };
  }

  for (const namedExport of Object.keys(moduleNamespace).filter((key) => key !== "default").sort()) {
    const candidate = moduleNamespace[namedExport];
    if (isBotCommandStrategy(candidate)) {
      return {
        ok: true,
        strategy: candidate,
        exportName: namedExport
      };
    }
  }

  const checkedExports = Object.keys(moduleNamespace).sort();
  return failure(
    checkedExports.length > 0 ? "STRATEGY_EXPORT_INVALID" : "EXPORT_NOT_FOUND",
    checkedExports.length > 0
      ? `No valid bot strategy export found. Checked exports: ${checkedExports.join(", ")}.`
      : "No exports found in bot strategy module."
  );
}

export function registerBotStrategyFromModule(
  moduleNamespace: unknown,
  options: BotStrategyModuleResolveOptions = {}
): BotStrategyModuleRegistrationResult {
  const resolved = resolveBotStrategyFromModule(moduleNamespace, options);
  if (!resolved.ok) {
    return resolved;
  }

  try {
    registerBotStrategy(resolved.strategy);
  } catch (error) {
    return failure(
      "REGISTRATION_FAILED",
      `Failed to register bot strategy '${resolved.strategy.strategyId}': ${describeUnknownError(error)}`,
      error
    );
  }

  return {
    ok: true,
    strategy: resolved.strategy,
    strategyId: normalizeBotStrategyKey(resolved.strategy.strategyId) ?? resolved.strategy.strategyId.trim(),
    exportName: resolved.exportName
  };
}

export async function loadAndRegisterBotStrategyModule(
  moduleUrl: unknown,
  options: BotStrategyModuleResolveOptions = {}
): Promise<BotStrategyModuleRegistrationResult> {
  if (typeof moduleUrl !== "string") {
    return failure("MODULE_URL_INVALID", "Bot strategy module URL must be a string.");
  }

  const trimmedModuleUrl = moduleUrl.trim();
  if (!trimmedModuleUrl) {
    return failure("MODULE_URL_INVALID", "Bot strategy module URL must be a non-empty string.");
  }

  let moduleNamespace: unknown;
  try {
    // Explicit developer bridge only: accept trusted local-dev URLs or reviewed inputs, never untrusted remote code.
    moduleNamespace = await import(/* @vite-ignore */ trimmedModuleUrl);
  } catch (error) {
    return failure(
      "MODULE_LOAD_FAILED",
      `Failed to load bot strategy module '${trimmedModuleUrl}': ${describeUnknownError(error)}`,
      error
    );
  }

  return registerBotStrategyFromModule(moduleNamespace, options);
}

function resolveNamedExport(moduleNamespace: Record<string, unknown>, exportName: string): BotStrategyModuleResolveResult {
  if (!Object.prototype.hasOwnProperty.call(moduleNamespace, exportName)) {
    return failure("EXPORT_NOT_FOUND", `Bot strategy module export '${exportName}' was not found.`);
  }

  const strategy = moduleNamespace[exportName];
  if (!isBotCommandStrategy(strategy)) {
    return failure(
      "STRATEGY_EXPORT_INVALID",
      `Bot strategy module export '${exportName}' must have a non-empty strategyId string and decide(context) function.`
    );
  }

  return {
    ok: true,
    strategy,
    exportName
  };
}

function isBotCommandStrategy(value: unknown): value is BotCommandStrategy {
  if (!isRecord(value)) {
    return false;
  }

  return typeof value.strategyId === "string" && value.strategyId.trim().length > 0 && typeof value.decide === "function";
}

function failure(
  code: BotStrategyModuleLoaderErrorCode,
  message: string,
  originalError?: unknown
): { readonly ok: false; readonly error: BotStrategyModuleLoaderError } {
  return {
    ok: false,
    error: new BotStrategyModuleLoaderError(code, message, originalError)
  };
}

function describeUnknownError(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }

  return String(error);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
