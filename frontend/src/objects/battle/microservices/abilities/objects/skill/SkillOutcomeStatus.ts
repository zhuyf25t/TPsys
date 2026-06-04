export type SkillOutcomeStatus = "applied" | "noop";

export function isSkillOutcomeStatus(value: unknown): value is SkillOutcomeStatus {
  return value === "applied" || value === "noop";
}

