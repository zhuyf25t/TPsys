package services.battle.microservices.results.objects.result

private[services] enum BattleFinishProjectionStatus {
  case Pending
  case InProgress
  case Ready
  case NotConfigured
  case Failed(message: String)
}
