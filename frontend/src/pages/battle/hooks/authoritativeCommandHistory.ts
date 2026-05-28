import type { AuthoritativeBattleCommand } from "../../../runtime/battle/authoritative/authoritativeBattleClient";

export const AUTHORITATIVE_COMMAND_HISTORY_LIMIT = 180;

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
  limit = AUTHORITATIVE_COMMAND_HISTORY_LIMIT
): AuthoritativeCommandHistoryStore {
  const boundedLimit = Math.max(1, Math.trunc(limit));
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
      entries = [...entries, entry].slice(-boundedLimit);
    },
    pruneThrough(clientCommandSeq) {
      const acknowledgedSeq = Math.max(0, Math.trunc(clientCommandSeq));
      entries = entries.filter((entry) => entry.clientCommandSeq > acknowledgedSeq);
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
