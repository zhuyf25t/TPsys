package services.battle.database

import io.circe.syntax.*

import services.battle.objects.BattleResultRecord

private[database] object BattleResultFileJsonRenderer {
  def renderPayload(records: Vector[BattleResultRecord]): String =
    BattleResultFileJsonPayload.fromDomain(records).asJson.spaces2 + System.lineSeparator()
}
