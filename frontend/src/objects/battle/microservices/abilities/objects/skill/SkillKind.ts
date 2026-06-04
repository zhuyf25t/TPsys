export type SkillKind = "Blink" | "Dash" | "Freeze" | "Critical";

export function isSkillKind(value: unknown): value is SkillKind {
  return value === "Blink" || value === "Dash" || value === "Freeze" || value === "Critical";
}
