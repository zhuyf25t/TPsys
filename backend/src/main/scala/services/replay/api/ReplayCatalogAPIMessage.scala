package services.replay.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.replay.objects.apiTypes.ReplayCatalogResponse
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayCatalogAPIMessage(
  limit: Option[Int] = None,
  handle: Option[String] = None
) extends APIMessageWithContext[ReplayService, ReplayCatalogResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayCatalogResponse] =
    for
      records <- service.list(limit.getOrElse(25))
    yield ReplayCatalogResponse.fromRecords(records, selectedHandle)

  private def selectedHandle: Option[PlayerHandle] =
    handle
      .flatMap(ReplayApiCodec.nonEmpty)
      .flatMap(PlayerHandle.forLookup)
}

object ReplayCatalogAPIMessage {
  given Decoder[ReplayCatalogAPIMessage] = deriveDecoder
}
