package services.battle.persistence

import io.circe.syntax.*

import services.battle.objects.BattleResultRecord

private[persistence] object BattleResultFileJsonRenderer {
  def renderPayload(records: Vector[BattleResultRecord]): String =
    BattleResultFileJsonPayload.fromDomain(records).asJson.spaces2 + System.lineSeparator()
}
