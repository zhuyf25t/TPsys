package services.battle.database.results

import io.circe.syntax.*

import services.battle.objects.BattleResultRecord

private[results] object BattleResultFileJsonRenderer {
  def renderPayload(records: Vector[BattleResultRecord]): String =
    BattleResultFileJsonPayload.fromDomain(records).asJson.spaces2 + System.lineSeparator()
}
