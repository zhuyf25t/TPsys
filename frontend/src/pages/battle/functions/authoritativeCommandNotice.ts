import type {
  AuthoritativeBattleCommandAccepted,
  AuthoritativeBattleCommandReason,
  AuthoritativeBattleCommandSubmitOutcome,
  AuthoritativeBattleSkillKind,
  AuthoritativeBattleSkillOutcome,
  AuthoritativeBattleSkillOutcomeReason
} from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";

const SKILL_NOTICE_LABELS: Record<AuthoritativeBattleSkillKind, string> = {
  Blink: "闪现",
  Dash: "冲刺",
  Freeze: "冰冻",
  Critical: "暴击"
};

const COMMAND_IGNORED_NOTICES: Record<AuthoritativeBattleCommandReason, string> = {
  battle_finished: "对局已结束",
  battle_inactive: "对局未开始",
  player_dead: "已被淘汰"
};

const SKILL_NOOP_REASON_NOTICES: Record<AuthoritativeBattleSkillOutcomeReason, string> = {
  skill_not_owned: "未装备",
  cooldown: "冷却中",
  missing_target: "没有目标",
  out_of_range: "目标太远",
  invalid_target: "目标无效",
  no_direction: "没有方向",
  blocked: "被障碍阻挡",
  insufficient_stamina: "体力不足"
};

/** 中文名：解析命令failurenotice（resolveCommandFailureNotice）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveCommandFailureNotice(outcome: AuthoritativeBattleCommandSubmitOutcome): string {
  if (outcome.ok) {
    return resolveAcceptedCommandNotice(outcome.accepted) ?? "命令未生效";
  }

  if (outcome.kind === "network") {
    return "网络同步中断";
  }

  if (outcome.kind === "parse") {
    return "服务器响应异常";
  }

  switch (outcome.errorCode) {
    case "command_not_authorized":
      return "命令被服务器拒绝";
    case "battle_not_found":
      return "对战已结束或不存在";
    case "player_not_found":
      return "玩家状态未同步";
    default:
      return "命令提交失败";
  }
}

/** 中文名：解析accepted命令notice（resolveAcceptedCommandNotice）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveAcceptedCommandNotice(accepted: AuthoritativeBattleCommandAccepted): string | null {
  if (accepted.commandStatus === "ignored") {
    return accepted.commandReason ? COMMAND_IGNORED_NOTICES[accepted.commandReason] : "命令未生效";
  }

  const noopOutcome = accepted.outcomes.find((outcome) => outcome.status === "noop");
  return noopOutcome ? resolveSkillOutcomeNotice(noopOutcome) : null;
}

/** 中文名：解析技能outcomenotice（resolveSkillOutcomeNotice）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveSkillOutcomeNotice(outcome: AuthoritativeBattleSkillOutcome): string {
  const skillLabel = SKILL_NOTICE_LABELS[outcome.action];
  const reasonNotice = outcome.reason ? SKILL_NOOP_REASON_NOTICES[outcome.reason] : "未生效";
  return `${skillLabel}${reasonNotice}`;
}
