export type BattleSurvivalOutcome = "Survived" | "Eliminated";

export function battleSurvivalOutcomeFromAliveAtEnd(aliveAtEnd: boolean): BattleSurvivalOutcome {
  return aliveAtEnd ? "Survived" : "Eliminated";
}

export function battleSurvivalOutcomeAliveAtEnd(value: BattleSurvivalOutcome): boolean {
  return value === "Survived";
}

