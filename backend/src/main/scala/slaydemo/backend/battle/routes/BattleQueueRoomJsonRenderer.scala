package slaydemo.backend.battle.routes

import io.circe.syntax.*

import slaydemo.backend.battle.objects.{BattleQueueSnapshot, RealtimeRoomSnapshot}
import slaydemo.backend.battle.objects.apiTypes.{BattleQueueSnapshotResponse, RealtimeRoomSnapshotResponse}

private[routes] object BattleQueueRoomJsonRenderer {
  def renderQueueSnapshot(snapshot: BattleQueueSnapshot): String =
    BattleQueueSnapshotResponse.fromSnapshot(snapshot).asJson.noSpaces

  def renderRoomSnapshot(snapshot: RealtimeRoomSnapshot): String =
    RealtimeRoomSnapshotResponse.fromSnapshot(snapshot).asJson.noSpaces
}
