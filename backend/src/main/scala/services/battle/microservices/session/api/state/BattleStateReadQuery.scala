package services.battle.microservices.session.api.state

import services.battle.objects.core.BattleId

final case class BattleStateReadQuery(battleId: BattleId)
