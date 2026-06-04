export type SkillOutcomeReason =
  | "skill_not_owned"
  | "cooldown"
  | "missing_target"
  | "out_of_range"
  | "invalid_target"
  | "no_direction"
  | "blocked"
  | "insufficient_stamina";

export function isSkillOutcomeReason(value: unknown): value is SkillOutcomeReason {
  return value === "skill_not_owned" ||
    value === "cooldown" ||
    value === "missing_target" ||
    value === "out_of_range" ||
    value === "invalid_target" ||
    value === "no_direction" ||
    value === "blocked" ||
    value === "insufficient_stamina";
}
