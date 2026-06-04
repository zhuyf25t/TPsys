import type { BotFrameBridge } from "../../../../bots/controller/botFrameBridge";
import type { PickupFrameBridge } from "../../../local/pickups/pickupFrameBridge";
import type { ProjectileSequenceBridge } from "../../../local/projectiles/projectileSequenceBridge";
import type { LocalBattleFrameSceneBridge } from "../../../local/session/localBattleFrameSceneBridge";
import type { BattleTemporalFrameBridge } from "../../../local/timers/battleTemporalFrameBridge";
import type { WeaponSwitchStateBridge } from "../../../local/weapons/weaponSwitchStateBridge";
import type { WeaponWheelSwitchSceneBridge } from "../../../local/weapons/weaponWheelSwitchSceneBridge";
import type { CombatProjectileEffectSceneBridge } from "../../renderer/effects/combatProjectileEffectSceneBridge";
import type { PlayerAbilitySceneBridge } from "../../renderer/effects/playerAbilitySceneBridge";
import type { PlayerMotionTweenController } from "../../renderer/effects/playerMotionTweenController";
import type { BattleFeedbackSceneBridge } from "../../renderer/effects/battleFeedbackSceneBridge";
import type { SharedAuthoritativeLocalFeedbackSceneBridge } from "../../renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge";
import type { WeaponActionSceneBridge } from "../../renderer/effects/weaponActionSceneBridge";
import type { ProjectileFrameSceneBridge } from "../../renderer/effects/projectileFrameSceneBridge";
import type { FreezeFieldSceneBridge } from "../../../local/skills/freezeFieldSceneBridge";

export interface GameSceneRuntimeBridgeSet {
  readonly weaponSwitchStateBridge: WeaponSwitchStateBridge;
  readonly projectileSequenceBridge: ProjectileSequenceBridge;
  readonly weaponWheelSwitchBridge: WeaponWheelSwitchSceneBridge;
  readonly freezeFieldBridge: FreezeFieldSceneBridge;
  readonly motionController: PlayerMotionTweenController;
  readonly playerAbilityBridge: PlayerAbilitySceneBridge;
  readonly combatEffectBridge: CombatProjectileEffectSceneBridge;
  readonly weaponActionBridge: WeaponActionSceneBridge;
  readonly projectileFrameBridge: ProjectileFrameSceneBridge;
  readonly battleFeedbackBridge: BattleFeedbackSceneBridge;
  readonly sharedAuthoritativeLocalFeedbackBridge: SharedAuthoritativeLocalFeedbackSceneBridge;
  readonly botFrameBridge: BotFrameBridge;
  readonly pickupFrameBridge: PickupFrameBridge;
  readonly temporalFrameBridge: BattleTemporalFrameBridge;
  readonly localBattleFrameBridge: LocalBattleFrameSceneBridge;
}
