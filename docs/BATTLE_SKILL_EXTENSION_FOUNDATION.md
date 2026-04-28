# Battle Skill Extension Foundation

This is the first frontend skill runtime profile pass.

## Scope

- Added lightweight per-skill runtime profiles for Blink, Dash, and Freeze.
- Centralized frontend activation classification:
  - Dash is `instant` and reads `PlayerCommand.castDash`.
  - Blink is `prepared-target` and reads `PlayerCommand.toggleBlink`.
  - Freeze is `prepared-target` and reads `PlayerCommand.toggleFreeze`.
- Centralized prepared-target feedback and indicator radii used by shared-authoritative local feedback and world indicators.
- Kept existing Blink, Dash, and Freeze execution branches in place.

## Non-Goals

- This is not a backend authoritative skill plugin system.
- This does not change input bindings, HUD behavior, weapon behavior, or art/VFX drawing internals.
- This does not strategy-extract Blink teleport, Dash movement, or Freeze field execution yet.

## Preserved Behavior

- Blink and Freeze remain toggle-to-prepare skills.
- Dash remains an instant cast skill.
- Existing local feedback priority is preserved: if Blink and Freeze toggles arrive together for targeted feedback, Freeze wins.
- Existing local movement toggle application is preserved: same-frame Blink then Freeze toggles leave Freeze prepared.
- Existing visual radii are preserved for indicator and shared feedback rejection:
  - Blink indicator target radius: `11`.
  - Freeze indicator target radius: `SKILL_DEFINITIONS.Freeze.radius`.
  - Blink prepare/release feedback rejection radius: `24` / `28`.
  - Freeze prepare/release feedback rejection radius: `SKILL_DEFINITIONS.Freeze.radius * 0.2` / `SKILL_DEFINITIONS.Freeze.radius`.

## Remaining Work

- Skill-specific execution still lives in the local movement handler.
- Target validity still has skill-specific branches for Blink geometry and Freeze range/world checks.
- VFX controllers still own concrete drawing internals and may use separate artistic radii.
- Backend authoritative skill contracts are unchanged.
