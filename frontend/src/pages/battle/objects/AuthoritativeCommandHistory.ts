import type { AuthoritativeBattleCommand } from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";

export const AUTHORITATIVE_COMMAND_HISTORY_LIMIT = 180;
export const AUTHORITATIVE_COMMAND_HISTORY_ACK_RETENTION_MS = 3_000;

export interface AuthoritativeCommandHistoryEntry {
  clientCommandSeq: number;
  command: AuthoritativeBattleCommand;
  createdAt: number;
}

export interface AuthoritativeCommandHistoryStore {
  readonly entries: readonly AuthoritativeCommandHistoryEntry[];
  record: (command: AuthoritativeBattleCommand, createdAt?: number) => void;
  pruneThrough: (clientCommandSeq: number) => void;
  clear: () => void;
}

/** 中文名：创建authoritative命令历史（createAuthoritativeCommandHistory）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createAuthoritativeCommandHistory(
  limit = AUTHORITATIVE_COMMAND_HISTORY_LIMIT,
  acknowledgedRetentionMs = AUTHORITATIVE_COMMAND_HISTORY_ACK_RETENTION_MS
): AuthoritativeCommandHistoryStore {
  const boundedLimit = Math.max(1, Math.trunc(limit));
  const boundedAcknowledgedRetentionMs = Math.max(0, Math.trunc(acknowledgedRetentionMs));
  let entries: AuthoritativeCommandHistoryEntry[] = [];

  return {
    get entries() {
      return entries;
    },
    record(command, createdAt = Date.now()) {
      const entry = {
        clientCommandSeq: Math.max(0, Math.trunc(command.clientCommandSeq)),
        command: cloneCommand(command),
        createdAt
      };
      entries = [
        ...entries.filter((existing) => existing.clientCommandSeq !== entry.clientCommandSeq),
        entry
      ].slice(-boundedLimit);
    },
    pruneThrough(clientCommandSeq) {
      const acknowledgedSeq = Math.max(0, Math.trunc(clientCommandSeq));
      const retainedAcknowledgedCutoff = Date.now() - boundedAcknowledgedRetentionMs;
      entries = entries.filter(
        (entry) => entry.clientCommandSeq > acknowledgedSeq || entry.createdAt >= retainedAcknowledgedCutoff
      );
    },
    clear() {
      entries = [];
    }
  };
}

function cloneCommand(command: AuthoritativeBattleCommand): AuthoritativeBattleCommand {
  return {
    ...command,
    movement: { ...command.movement },
    aim: { ...command.aim },
    pointerWorld: command.pointerWorld ? { ...command.pointerWorld } : command.pointerWorld
  };
}
