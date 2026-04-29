package slaydemo.backend.battle.policies

trait BattlePolicy {
  def allowCommand(currentPhase: String, payloadType: String): Boolean
}
