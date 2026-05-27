package services.battle.objects.apiTypes.results

import io.circe.{Decoder, DecodingFailure, HCursor}

import services.battle.objects.BattleResultListQuery
import services.battle.objects.core.{BattleId, BattleResultListLimit}
import services.identity.objects.PlayerHandle

object BattleResultListRequest {
  given Decoder[BattleResultListQuery] =
    Decoder.instance(decodeRequest)

  private def decodeRequest(cursor: HCursor): Either[DecodingFailure, BattleResultListQuery] =
    for
      handle <- optionalText(cursor, "handle").map(_.map(PlayerHandle.apply))
      battleId <- optionalText(cursor, "battleId").map(_.map(BattleId.apply))
      limit <- optional[Int](cursor, "limit").map(_.map(BattleResultListLimit.apply))
    yield BattleResultListQuery(
      handle = handle.flatMap(value => PlayerHandle.forLookup(value.value)),
      battleId = battleId,
      limit = limit.getOrElse(BattleResultListLimit(25))
    )

  private def optionalText(cursor: HCursor, key: String): Either[DecodingFailure, Option[String]] =
    optional[String](cursor, key).map(_.flatMap(nonEmpty))

  private def optional[A: Decoder](cursor: HCursor, key: String): Either[DecodingFailure, Option[A]] =
    cursor.get[Option[A]](key).left.map(_ => invalidField(key, cursor))

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def invalidField(key: String, cursor: HCursor): DecodingFailure =
    DecodingFailure(s"Invalid battle result list field: $key", cursor.history)
}
