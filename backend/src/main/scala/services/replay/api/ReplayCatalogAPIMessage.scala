package services.replay.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.identity.objects.PlayerHandle
import services.replay.services.ReplayService
import system.api.APIMessageWithContext

final case class ReplayCatalogAPIMessage(
  limit: Option[ReplayListLimitInput] = None,
  handle: Option[PlayerHandle] = None
) extends APIMessageWithContext[ReplayService, ReplayCatalogResponse] {
  override def plan(service: ReplayService, connection: Connection): IO[ReplayCatalogResponse] =
    ReplayReadAPIPlanner.planCatalog(service, this)
}

object ReplayCatalogAPIMessage {
  import ReplayAPIMessageDecoding.given

  given Decoder[ReplayCatalogAPIMessage] =
    deriveDecoder[ReplayCatalogAPIMessage]
}
