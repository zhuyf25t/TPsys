import type { Hero, PreparedSkill, Vec2 } from "../../../objects/types";
import type { SceneGeometryObstacleBounds } from "../../../runtime/local/geometry/sceneGeometry";
import { isBlinkTargetValid } from "../../../runtime/local/movement/blinkTargetResolver";
import { isInsideWorld } from "../../../runtime/local/movement/motionController";
import { isFreezeTargetInRange } from "../../../runtime/local/skills/freezeFieldController";
import { getPreparedTargetSkillRuntimeProfile } from "../../../runtime/local/skills/skillRuntimeProfiles";

export interface SharedAuthoritativeTargetValidityInput {
  player: Hero;
  preparedSkill: Exclude<PreparedSkill, null>;
  target: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
}

export function isSharedAuthoritativeTargetValid(input: SharedAuthoritativeTargetValidityInput): boolean {
  const profile = getPreparedTargetSkillRuntimeProfile(input.preparedSkill);

  switch (profile.kind) {
    case "Blink":
      return (
        isTargetInRange(input.player.position, input.target, profile.target.range) &&
        isBlinkTargetValid({
          player: input.player,
          target: input.target,
          worldSize: input.worldSize,
          obstacleBounds: input.obstacleBounds
        })
      );
    case "Freeze":
      return (
        isFreezeTargetInRange(input.player.position, input.target, profile.target.range) &&
        isInsideWorld(input.target, 0, input.worldSize)
      );
  }
}

function isTargetInRange(origin: Vec2, target: Vec2, range: number): boolean {
  return Math.hypot(target.x - origin.x, target.y - origin.y) <= range;
}
