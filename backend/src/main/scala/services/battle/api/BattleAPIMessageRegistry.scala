package services.battle.api

import system.api.RegisteredAPIMessage

object BattleAPIMessageRegistry {
  def registered(services: BattleAPIMessageServices): List[RegisteredAPIMessage] =
    List(
      BattleQueueJoinAPIMessage.registered(services),
      BattleQueueStatusAPIMessage.registered(services),
      BattleQueueLeaveAPIMessage.registered(services),
      BattleRoomSnapshotAPIMessage.registered(services),
      BattleRoomHeartbeatAPIMessage.registered(services),
      BattleStateReadAPIMessage.registered(services),
      BattleCommandAPIMessage.registered(services),
      BattleResultListAPIMessage.registered(services),
      BattleResultRecordAPIMessage.registered(services)
    )
}
