package slaydemo.backend.battle.objects

enum BattleParticipantKind {
  case Human
  case Bot
}

object BattleParticipantKind {
  def fromBotFlag(isBot: Boolean): BattleParticipantKind =
    if isBot then BattleParticipantKind.Bot else BattleParticipantKind.Human

  def isBot(value: BattleParticipantKind): Boolean =
    value == BattleParticipantKind.Bot
}
