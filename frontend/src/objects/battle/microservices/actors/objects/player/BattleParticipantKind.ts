export type BattleParticipantKind = "Human" | "Bot";

export function battleParticipantKindFromBotFlag(isBot: boolean): BattleParticipantKind {
  return isBot ? "Bot" : "Human";
}

export function battleParticipantKindIsBot(value: BattleParticipantKind): boolean {
  return value === "Bot";
}

